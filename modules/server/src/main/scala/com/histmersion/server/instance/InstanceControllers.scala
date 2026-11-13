package com.histmersion
package server.instance
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.database.Transactor

import com.histmersion.domain.personal.seen.{SeenBoard, SeenService}
import com.histmersion.server.{BuildMeta, Deps, StorageLocations, Supervisor}
import com.histmersion.server.UiConfigViews
import com.histmersion.util.basemap.BasemapService
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.data.diagnostics.{DataCount, DiagnosticsService, StorageCache, StorageFigures}
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
import com.histmersion.api.jobs.{JobCounts, JobState}
import com.histmersion.api.library.{CorrespondingSource, UpstreamQuota}
import com.histmersion.api.settings.ReloadReport
import com.histmersion.api.Api
import com.histmersion.api.library.Acquire
import com.histmersion.api.maintenance.Storage
import com.histmersion.util.database.Migrate
import com.histmersion.util.data.maintenance.WriteGate

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
    setupDue: () => Boolean
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
          backups.list.left.map(_.message.sentence),
          MeasureBudget.of(
            MeasureBudget.MaxEntries(c.storage.maxEntries.value.toLong),
            MeasureBudget.MaxMillis(c.storage.maxMillis.value.toLong)
          ),
          () => deps.clock.instant()
        )
      )
      () => run.run()
    },
    ttlMillis = () => StorageCache.TimeToLive(cfg.storage.ttlSeconds.value * 1000L)
  )

  /** `POST /api/config/reload` (MCP `config`, `histmersion config reload`): the config file read again and the server
    * switched to it in place — docs/research-plugins.md §3.5.
    */
  def configReload(): ReloadReport = deps.reloader()

  def health: Health =
    val conv = converter()
    Try(Migrate.current(deps.ds)) match
      case scala.util.Success(v) =>
        Health(
          if conv.healthy then Health.Status("ok") else Health.Status("degraded"),
          Health.Db("ok"),
          v.map(x => Health.SchemaVersion(x.value)),
          Some(Health.Converter(conv.render.value))
        )
      case scala.util.Failure(e) =>
        Health(
          Health.Status("degraded"),
          Health.Db(s"error: ${e.getMessage}"),
          None,
          Some(Health.Converter(conv.render.value))
        )

  /** `GET /api/about` — the build, the health report, the job queue, the newest backup and the store's counts. Each
    * part that cannot be read becomes a `problems` line, never a failed request: this is the page someone opens BECAUSE
    * something looks wrong.
    */
  def about(now: Instant = Instant.now(deps.clock)): About =
    def attempt[A](what: String)(a: => A): Either[String, A] =
      Try(a).toEither.left.map(e => s"$what: ${e.getMessage}")
    val jobs = attempt("job counts")(
      JobCounts.of(
        diagnostics
          .figures(JobsData.owner)
          .counts
          .map(c => JobState.Wire(c.noun.value) -> JobCounts.Count(c.count.value.toInt))
      )
    )
    val counts = attempt("store counts")(corpusCounts)
    val listed = backups.list.left.map(e => s"backups: ${e.message.sentence}")
    val last = listed.toOption.flatMap(_.headOption)
    val age = last
      .flatMap(b => Try(Instant.parse(b.createdAt.value)).toOption)
      .map(t => About.LastBackupAgeSeconds(math.max(0L, now.getEpochSecond - t.getEpochSecond)))
    About(
      name = About.Name(Version.name.value),
      build = BuildStamp(
        BuildStamp.Version(Version.version.value),
        BuildMeta.gitCommit.map(BuildStamp.GitCommit(_)),
        BuildMeta.commitDate.map(BuildStamp.CommitDate(_)),
        BuildStamp.Dirty(BuildMeta.dirty),
        licence = Some(BuildStamp.Licence(Version.licence.value)),
        sourceUrl = Some(
          BuildStamp.SourceUrl(
            CorrespondingSource
              .url(
                CorrespondingSource.Repository(cfg.server.sourceUrl.value),
                BuildMeta.gitCommit.map(CorrespondingSource.Commit(_))
              )
              .value
          )
        )
      ),
      health = health,
      jobs = jobs.getOrElse(JobCounts()),
      counts = counts.toOption,
      lastBackup = last,
      lastBackupAgeSeconds = age,
      problems = List(jobs, counts, listed).flatMap(_.left.toOption).map(About.Problems(_)),
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
    def count(noun: String): Int =
      figures.flatMap(_.count(DataCount.Noun(noun))).headOption.fold(0)(_.value.toInt)
    CorpusCounts(
      CorpusCounts.Sources(count("sources")),
      CorpusCounts.Findings(count("findings")),
      CorpusCounts.Articles(count("articles")),
      CorpusCounts.Events(count("events")),
      CorpusCounts.Places(count("places")),
      CorpusCounts.Periods(count("periods")),
      CorpusCounts.Presets(count("presets"))
    )

  /** The last storage measurement, with its age as of `now`. */
  private def storageNow(now: Instant): Storage.Report =
    val r = storage.get(StorageCache.WaitMillis(cfg.storage.waitMillis.value.toLong))
    r.modify(_.ageSeconds)
      .setTo(
        r.measuredAt
          .flatMap(t => Try(Instant.parse(t.value)).toOption)
          .map(t => Storage.Report.AgeSeconds(math.max(0L, now.getEpochSecond - t.getEpochSecond)))
      )

  /** `GET /api/ui/counters` — the lists' counts and "something new" marks ([[UiNotices]]), and, where the server runs
    * agents, what they are doing (the top bar's status).
    */
  def uiCounters: UiNotices.Counters =
    val marks =
      seen.marks(UserId.Local).map((b, at) => UiNotices.Board.Wire(b.value) -> UiNotices.Counter.Seen(at.iso.value))
    val counters = Transactor(deps.ds).read(UiRepo.counters(marks))
    if cfg.agents.enabled.value then counters.modify(_.agents).setTo(Some(Transactor(deps.ds).read(UiRepo.agents)))
    else counters

  /** `POST /api/ui/seen/{board}` — the reader opened that window, which showed items up to `mark.at`. Answers the
    * counters after the mark moved.
    */
  def markSeen(board: UiNotices.Board.Wire, mark: UiNotices.SeenMark): Either[Api.Error, UiNotices.Counters] =
    deps.maintenance.guard(WriteGate.OperationName("mark_seen")):
      for
        b <- UiNotices.Board
          .parse(board)
          .toRight(
            Api.Error(
              Problem
                .Unclassified(Legacy.Text(s"unknown board '$board' — one of: ${UiNotices.Board.wires.mkString(", ")}"))
            )
          )
        at <- mark.at.map(_.value.trim).filter(_.nonEmpty) match
          case None    => Right(None)
          case Some(s) =>
            Try(Instant.parse(s)).toOption
              .map(Some(_))
              .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"'at' is not an ISO-8601 instant: $s"))))
      yield
        val _ = seen.mark(UserId.Local, SeenBoard(b.wire.value), at)
        uiCounters

  /** The `ui` config block, plus whether writes need a key — never the key itself — and the plugins (their settings
    * with secrets as references, never values).
    */
  def uiConfig: UiConfig = UiConfigViews.page(
    cfg.ui,
    writeKeyRequired = UiConfig.WriteKeyRequired(cfg.auth.apiKey.value.nonEmpty),
    agentsEnabled = UiConfig.AgentsEnabled(cfg.agents.enabled.value),
    plugins = deps.plugins.report,
    baseMapSource = baseMapSource,
    mapLanguage = UiConfig.MapLanguage(cfg.basemap.labels.language.value),
    setupDue = UiConfig.SetupDue(Try(setupDue()).getOrElse(false))
  )

  /** `ui.base-map-source` resolved ([[BaseMapSource]]): `auto` becomes `local` once the stored extract can draw one of
    * the vector styles whole — a `stat` and a cached header read.
    */
  def baseMapSource: BaseMapSource.Wire =
    val setting = BaseMapSource.Wire(cfg.ui.baseMapSource.value)
    setting.value.trim.toLowerCase(java.util.Locale.ROOT) match
      case BaseMapSource.Local | BaseMapSource.Online =>
        BaseMapSource.Wire(setting.value.trim.toLowerCase(java.util.Locale.ROOT))
      case _ => BaseMapSource.resolve(setting, basemap.status)
