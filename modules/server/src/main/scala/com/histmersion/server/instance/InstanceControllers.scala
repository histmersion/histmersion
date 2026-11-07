package com.histmersion.server.instance

import com.histmersion.util.database.Transactor

import com.histmersion.domain.personal.seen.{SeenBoard, SeenService}
import com.histmersion.server.{BuildMeta, Deps, StorageLocations, Supervisor}
import com.histmersion.server.UiConfigViews
import com.histmersion.util.basemap.BasemapService
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.data.diagnostics.{DiagnosticsService, StorageCache, StorageFigures}
import com.histmersion.util.jobs.JobsData
import com.histmersion.util.ocr.ConverterState
import com.histmersion.util.storage.MeasureBudget
import com.histmersion.UserId
import hearth.kindlings.optics.syntax.*

import java.time.Instant
import scala.util.Try
import com.histmersion.api.automation.AgentLeaseInfo
import com.histmersion.api.basemap.BaseMapSource
import com.histmersion.api.instance.{About, BuildStamp, CorpusCounts, Health, UiConfig, UiNotices, Version}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.library.{CorrespondingSource, UpstreamQuota}
import com.histmersion.api.settings.ReloadReport
import com.histmersion.api.Api
import com.histmersion.api.library.Acquire
import com.histmersion.api.maintenance.Storage

/** This server as REST, MCP and the CLI ask about it, over the diagnostics: its version and health, About (the build,
  * the job queue, the newest backup, the store's counts, the storage, the plugins, the agent lease, the metered
  * upstreams and the acquisition list's re-check), the web page's settings, the toolbar's counters and the windows'
  * seen marks, and the config reload. Each part of About that cannot be read becomes a `problems` line.
  */
final class InstanceControllers(
    deps: Deps,
    converter: () => ConverterState,
    diagnostics: DiagnosticsService,
    backups: BackupService,
    seen: SeenService,
    basemap: BasemapService,
    agentLease: () => Option[AgentLeaseInfo],
    upstreams: Instant => List[UpstreamQuota],
    acquireRecheck: Instant => Option[Acquire.Checks.RecheckStatus],
    setupDue: () => InstanceControllers.SetupDueResult
):
  /** Read at each use: a config reload switches it (Deps). */
  private def cfg = deps.cfg

  def version: Version.Info = Version.Info(Version.name, Version.version)

  /** The Storage part of About: measured in the background, at most once per `storage.ttl-seconds` ([[StorageCache]]),
    * so `about` itself never walks a disk.
    */
  private val storage = StorageCache(
    prepare = () => {
      val c = cfg
      val env = deps.environment.lookup
      val measured =
        StorageLocations.measured(c, env, deps.userPaths.osName, Supervisor.currentExecutable)
      val run = deps.pins.carry(
        StorageFigures(Deps.data).measure(
          measured,
          StorageLocations.install,
          c,
          Some(diagnostics),
          backups.list.left.map(_.message),
          MeasureBudget.of(c.storage.maxEntries.toLong, c.storage.maxMillis.toLong),
          () => deps.clock.instant()
        )
      )
      () => run.run()
    },
    ttlMillis = () => cfg.storage.ttlSeconds * 1000L
  )

  /** `POST /api/config/reload` (MCP `config`, `histmersion config reload`): the config file read again and the server
    * switched to it in place — docs/research-plugins.md §3.5.
    */
  def configReload(): ReloadReport = deps.reloader()

  def health: Health =
    val conv = converter()
    Try(com.histmersion.util.database.Migrate.current(deps.ds)) match
      case scala.util.Success(v) => Health(if conv.healthy then com.histmersion.api.instance.Health.Status("ok") else com.histmersion.api.instance.Health.Status("degraded"), com.histmersion.api.instance.Health.Db("ok"), v, Some(conv.render))
      case scala.util.Failure(e) => Health(com.histmersion.api.instance.Health.Status("degraded"), s"error: ${e.getMessage}", None, Some(conv.render))

  /** `GET /api/about` — the build, the health report, the job queue, the newest backup and the store's counts. Each
    * part that cannot be read becomes a `problems` line, never a failed request: this is the page someone opens BECAUSE
    * something looks wrong.
    */
  def about(now: Instant = Instant.now(deps.clock)): About =
    def attempt[A](what: com.histmersion.server.instance.InstanceControllers.Operation)(a: => A): Either[com.histmersion.server.instance.InstanceControllers.Problem, A] =
      Try(a).toEither.left.map(e => s"$what: ${e.getMessage}")
    val jobs = attempt(com.histmersion.server.instance.InstanceControllers.Operation("job counts"))(
      JobCounts.of(diagnostics.figures(JobsData.owner).counts.map(c => c.noun -> c.count.toInt))
    )
    val counts = attempt(com.histmersion.server.instance.InstanceControllers.Operation("store counts"))(corpusCounts)
    val listed = backups.list.left.map(e => s"backups: ${e.message}")
    val last = listed.toOption.flatMap(_.headOption)
    val age = last
      .flatMap(b => Try(Instant.parse(b.createdAt)).toOption)
      .map(t => math.max(0L, now.getEpochSecond - t.getEpochSecond))
    About(
      name = Version.name,
      build = BuildStamp(
        Version.version,
        BuildMeta.gitCommit,
        BuildMeta.commitDate,
        BuildMeta.dirty,
        licence = Some(Version.licence),
        sourceUrl = Some(CorrespondingSource.url(cfg.server.sourceUrl, BuildMeta.gitCommit))
      ),
      health = health,
      jobs = jobs.getOrElse(JobCounts()),
      counts = counts.toOption,
      lastBackup = last,
      lastBackupAgeSeconds = age,
      problems = List(jobs, counts, listed).flatMap(_.left.toOption),
      configFile = deps.configFile,
      agentLease = agentLease(),
      plugins = deps.plugins.report,
      storage = storageNow(now),
      upstreams = upstreams(now),
      acquireRecheck = Try(acquireRecheck(now)).toOption.flatten
    )

  /** The corpus About counts: what the owners count of their records. */
  private def corpusCounts: CorpusCounts =
    val figures = Deps.data.owners.filterNot(_ == JobsData.owner).map(diagnostics.figures)
    def count(noun: com.histmersion.server.instance.InstanceControllers.Noun): com.histmersion.RowCount = figures.flatMap(_.count(noun)).headOption.fold(0)(_.toInt)
    CorpusCounts(
      count(com.histmersion.server.instance.InstanceControllers.Noun("sources")),
      count(com.histmersion.server.instance.InstanceControllers.Noun("findings")),
      count(com.histmersion.server.instance.InstanceControllers.Noun("articles")),
      count(com.histmersion.server.instance.InstanceControllers.Noun("events")),
      count(com.histmersion.server.instance.InstanceControllers.Noun("places")),
      count(com.histmersion.server.instance.InstanceControllers.Noun("periods")),
      count(com.histmersion.server.instance.InstanceControllers.Noun("presets"))
    )

  /** The last storage measurement, with its age as of `now`. */
  private def storageNow(now: Instant): Storage.Report =
    val r = storage.get(cfg.storage.waitMillis.toLong)
    r.modify(_.ageSeconds)
      .setTo(
        r.measuredAt
          .flatMap(t => Try(Instant.parse(t)).toOption)
          .map(t => math.max(0L, now.getEpochSecond - t.getEpochSecond))
      )

  /** `GET /api/ui/counters` — the lists' counts and "something new" marks ([[UiNotices]]), and, where the server runs
    * agents, what they are doing (the top bar's status).
    */
  def uiCounters: UiNotices.Counters =
    val marks = seen.marks(UserId.Local).map((b, at) => (b.value: String) -> at.iso)
    val counters = Transactor(deps.ds).read(UiRepo.counters(marks))
    if cfg.agents.enabled then counters.modify(_.agents).setTo(Some(Transactor(deps.ds).read(UiRepo.agents)))
    else counters

  /** `POST /api/ui/seen/{board}` — the reader opened that window, which showed items up to `mark.at`. Answers the
    * counters after the mark moved.
    */
  def markSeen(board: com.histmersion.server.instance.InstanceControllers.Board, mark: UiNotices.SeenMark): Either[Api.Error, UiNotices.Counters] =
    deps.maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("mark_seen")):
      for
        b <- UiNotices.Board
          .parse(board)
          .toRight(Api.Error(s"unknown board '$board' — one of: ${UiNotices.Board.wires.mkString(", ")}"))
        at <- mark.at.map(_.trim).filter(_.nonEmpty) match
          case None    => Right(None)
          case Some(s) =>
            Try(Instant.parse(s)).toOption.map(Some(_)).toRight(Api.Error(s"'at' is not an ISO-8601 instant: $s"))
      yield
        val _ = seen.mark(UserId.Local, SeenBoard(b.wire), at)
        uiCounters

  /** The `ui` config block, plus whether writes need a key — never the key itself — and the plugins (their settings
    * with secrets as references, never values).
    */
  def uiConfig: UiConfig = UiConfigViews.page(
    cfg.ui,
    writeKeyRequired = cfg.auth.apiKey.nonEmpty,
    agentsEnabled = cfg.agents.enabled,
    plugins = deps.plugins.report,
    baseMapSource = baseMapSource,
    mapLanguage = cfg.basemap.labels.language,
    setupDue = Try(setupDue()).getOrElse(false)
  )

  /** `ui.base-map-source` resolved ([[BaseMapSource]]): `auto` becomes `local` once the stored extract can draw one of
    * the vector styles whole — a `stat` and a cached header read.
    */
  def baseMapSource: com.histmersion.server.instance.InstanceControllers.MapSource =
    cfg.ui.baseMapSource.trim.toLowerCase(java.util.Locale.ROOT) match
      case s @ (BaseMapSource.Local | BaseMapSource.Online) => s
      case _ => BaseMapSource.resolve(cfg.ui.baseMapSource, basemap.status)

object InstanceControllers:
  type Board = Board.T
  object Board extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapSource = MapSource.T
  object MapSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Noun = Noun.T
  object Noun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Operation = Operation.T
  object Operation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetupDue = SetupDue.T
  object SetupDue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SetupDueResult = com.histmersion.server.instance.InstanceControllers.SetupDue
  val SetupDueResult: _root_.com.histmersion.server.instance.InstanceControllers.SetupDue.type = _root_.com.histmersion.server.instance.InstanceControllers.SetupDue

