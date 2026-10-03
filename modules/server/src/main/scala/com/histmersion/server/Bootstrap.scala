package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{JobRepo, PeriodRepo, PlaceRepo}
import com.histmersion.server.importers.{GazetteerImporter, ImportRunner, NaturalEarthLand, PeriodOFetch}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference

/** Named periods (#719) and first-run data (#720): the period-preset write path and the `init` job.
  *
  * The data a new install needs ships WITH the server — the gazetteer (`<init.data-dir>/places`, the
  * repo's `db/places`) and the period presets (`<init.data-dir>/periods`, the repo's `db/periods`);
  * `scripts/bundle/server.sh` copies both into the bundle and [[Supervisor]] points `init.data-dir`
  * there. The `init` job imports them server-side — the CLI is a pure HTTP client and has no files
  * to send — and, with `fetch`, downloads the network gazetteers and PeriodO as well. A starting
  * server runs the same job by itself for whichever of the two tables is still EMPTY
  * ([[autoStart]]), in the background, so binding the port never waits for it.
  *
  * `download` is the seam a test substitutes for the network. */
final class Bootstrap(deps: Deps, download: String => Either[String, String] = PeriodOFetch.download):

  private val ds          = deps.ds
  private val cfg         = deps.cfg
  private val maintenance = deps.maintenance

  /** The init job running now, if any — one at a time: two concurrent seeds would only race each
    * other over the same upserts. An `AtomicReference` because the job thread clears it. */
  private val running = AtomicReference[Option[String]](None)

  private def dataDir: Path     = Path.of(cfg.init.dataDir)
  private def placesDir: Path   = dataDir.resolve("places")
  private def periodsSeed: Path = dataDir.resolve(cfg.periods.seedFile)

  // ── periods ──────────────────────────────────────────────────────────────────────────────────

  def period(id: String): Either[ApiError, PeriodDetail] =
    PeriodRepo.get(ds, id).toRight(ApiError(s"No period '$id'", Some(Write.CodeNotFound))).map { p =>
      PeriodDetail(p, p.parentId.flatMap(PeriodRepo.get(ds, _)), PeriodRepo.children(ds, id).toList)
    }

  /** `POST /api/periods/seed` — the presets bundled with this server. */
  def seedPeriods(): Either[ApiError, PeriodImportResult] =
    maintenance.guard("periods/seed")(seedPeriodsNow().left.map(ApiError(_)))

  /** `POST /api/periods/import` — a PeriodO dataset sent by the client (a dump, or a subset of one). */
  def importPeriods(collections: List[String], body: String): Either[ApiError, PeriodImportResult] =
    maintenance.guard("periods/import"):
      PeriodO.parse(body).left.map(ApiError(_)).map(d => write(d.only(collections.map(_.trim).toSet)))

  /** `POST /api/periods/fetch?source=periodo` — the live PeriodO dump, the named collections only. */
  def fetchPeriods(source: String, collections: List[String]): Either[ApiError, PeriodImportResult] =
    maintenance.guard("periods/fetch"):
      if source.trim.toLowerCase != PeriodOFetch.name then
        Left(ApiError(s"unknown period source '$source' (available: ${PeriodOFetch.name})"))
      else fetchPeriodsNow(collections).left.map(ApiError(_))

  private def seedPeriodsNow(): Either[String, PeriodImportResult] =
    if !Files.isRegularFile(periodsSeed) then Left(s"no period seed at $periodsSeed (set init.data-dir)")
    else PeriodO.parse(Files.readString(periodsSeed)).map(write)

  private def fetchPeriodsNow(collections: List[String]): Either[String, PeriodImportResult] =
    val wanted = collections.map(_.trim).filter(_.nonEmpty) match
      case Nil => cfg.periods.collections
      case cs  => cs
    for
      raw <- download(cfg.periods.periodoUrl)
      all <- PeriodO.parse(raw)
      d    = all.only(wanted.toSet)
      _   <- if d.authorities.isEmpty then Left(s"no PeriodO collection ${wanted.mkString(", ")} in the dump")
             else Right(())
    yield write(d)

  /** Upsert a dataset. A dataset WITHOUT curated parents (a raw PeriodO dump) is grouped under one
    * node per authority; every period already in the store keeps its place. */
  private def write(d: PeriodODataset): PeriodImportResult =
    val roots = d.parents.isEmpty
    val ids   = d.authorities.flatMap(a => a.id :: a.periods.map(_.id))
    val rows  = PeriodO.toPeriods(d, PeriodRepo.placement(ds, ids), roots)
    val n     = PeriodRepo.upsert(ds, rows)
    PeriodImportResult(n, if roots then d.authorities.count(_.periods.nonEmpty) else 0,
      PeriodRepo.presetCount(ds), d.skipped.take(Bootstrap.SkippedListed), d.skipped.size)

  // ── init ─────────────────────────────────────────────────────────────────────────────────────

  /** On a server start (`init.auto`): the init job for whichever table is empty, or nothing. */
  def autoStart(): Option[JobInfo] =
    val p = plan(fetch = false, onlyEmpty = true)
    if p.isEmpty then None
    else
      scribe.info(s"first start: importing the ${InitPlan.describe(p)} from $dataDir in the background")
      start(p).toOption

  /** `POST /api/init` — the bundled gazetteer and period presets (re-imported: every write is an
    * upsert) and, with `fetch`, the network sources in `init.fetch-sources`. */
  def init(fetch: Boolean): Either[ApiError, JobInfo] =
    maintenance.guard("init")(start(plan(fetch, onlyEmpty = false)))

  /** What an init job will import: everything bundled, or (on a first start) only what is empty, plus
    * the network sources when asked. Decided up front and stored on the job (#770). */
  private def plan(fetch: Boolean, onlyEmpty: Boolean): InitPlan =
    InitPlan(
      gazetteer = !onlyEmpty || PlaceRepo.count(ds) == 0,
      periodPresets = !onlyEmpty || PeriodRepo.count(ds) == 0,
      fetch = if fetch then cfg.init.sources else Nil)

  private def start(p: InitPlan): Either[ApiError, JobInfo] =
    if !running.compareAndSet(None, Some("starting")) then
      Left(ApiError(s"an init job is already running (${running.get.getOrElse("?")}) — see `histmersion jobs`"))
    else
      val job = JobRepo.create(ds, Bootstrap.JobKind, None, None, detail = Some(JobDetail(init = Some(p))))
      running.set(Some(job.id))
      val t = Thread.ofPlatform().daemon(true).name("histmersion-init").unstarted { () =>
        try run(job.id, p)
        catch
          case e: Throwable =>
            scribe.error(s"init job ${job.id} died: ${e.getMessage}", e)
            val _ = JobRepo.transition(ds, job.id, JobState.Running, JobState.Failed,
              error = Some(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
        finally running.set(None)
      }
      t.start()
      Right(JobRepo.get(ds, job.id).getOrElse(job))

  private def run(id: String, p: InitPlan): Unit =
    val _ = JobRepo.transition(ds, id, JobState.Queued, JobState.Running, Some("starting"))
    def note(s: String): Unit = JobRepo.progress(ds, id, s)
    // the definitions a period import left out go on the job (#771), not just into the log
    def periods(r: PeriodImportResult): PeriodImportResult =
      if r.skippedCount > 0 then
        JobRepo.get(ds, id).foreach { j =>
          val d = j.detail.getOrElse(JobDetail(init = Some(p)))
          JobRepo.setDetail(ds, id, d.copy(
            skippedPeriods = (d.skippedPeriods ++ r.skipped).take(Bootstrap.SkippedListed),
            skippedPeriodCount = d.skippedPeriodCount + r.skippedCount))
        }
      r
    val steps: List[(String, () => Either[String, String])] =
      List(
        Option.when(p.gazetteer)("gazetteer" -> (() => seedPlaces(note))),
        Option.when(p.periodPresets)("period presets" -> (() =>
          seedPeriodsNow().map(periods).map(r =>
            s"${Plural.count(r.periods, "period")}, ${Plural.count(r.presets, "preset")}" + skippedNote(r))))
      ).flatten ++ p.fetch.map(s => s"fetch $s" -> (() => fetchSource(s, periods)))
    val results = steps.map { (name, step) =>
      if maintenance.active then name -> Left("a restore started; skipped")
      else
        note(name)
        scribe.info(s"[init] $name…")
        val r = try step() catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        r.fold(e => scribe.warn(s"[init] $name failed: $e"), ok => scribe.info(s"[init] $name: $ok"))
        name -> r
    }
    val failed = results.collect { case (n, Left(e)) => s"$n: $e" }
    val done   = results.collect { case (n, Right(s)) => s"$n: $s" }
    val summary = if done.isEmpty then Some("nothing to do") else Some(done.mkString("; "))
    val _ =
      if failed.isEmpty then JobRepo.transition(ds, id, JobState.Running, JobState.Done, summary)
      else JobRepo.transition(ds, id, JobState.Running, JobState.Failed, summary, Some(failed.mkString("; ")))

  /** The bundled gazetteer, the way `histmersion places seed` imports it: [[PlaceSeed.files]] order,
    * then a second pass over every file that reported a dangling parent, then `finding_places`. */
  private def seedPlaces(note: String => Unit): Either[String, String] =
    if !Files.isDirectory(placesDir) then Left(s"no gazetteer seed at $placesDir (set init.data-dir)")
    else
      val files = PlaceSeed.files(placesDir)
      def importFile(f: Path, kind: Option[String]): Either[String, PlaceImportResult] =
        Lpf.parse(Files.readString(f), kind.getOrElse(PlaceKind.Region)).left.map(e => s"${placesDir.relativize(f)}: $e")
          .map(_.grouped(math.max(1, cfg.places.importBatch)).foldLeft(PlaceImportResult(0, 0, 0, Nil)) { (sum, b) =>
            val r = PlaceRepo.upsertPlaces(ds, b)
            PlaceImportResult(sum.places + r.places, sum.names + r.names, sum.relations + r.relations,
              (sum.failed ++ r.failed).take(20))
          })
      def pass(fs: List[(Path, Option[String])]): Either[String, (Int, List[(Path, Option[String])])] =
        fs.zipWithIndex.foldLeft[Either[String, (Int, List[(Path, Option[String])])]](Right((0, Nil))) {
          case (acc, ((f, kind), i)) =>
            acc.flatMap { (n, retry) =>
              note(s"gazetteer: ${placesDir.relativize(f)} (${i + 1}/${fs.size})")
              importFile(f, kind).map(r => (n + r.places, if r.failed.isEmpty then retry else retry :+ (f -> kind)))
            }
        }
      if files.isEmpty then Left(s"no JSON files under $placesDir")
      else
        for
          first <- pass(files)
          _     <- if first._2.isEmpty then Right((0, Nil)) else pass(first._2)
        yield
          note("gazetteer: matching findings to places")
          val r = PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
          s"${first._1} place(s) from ${files.size} file(s); ${r.links} finding→place link(s)"

  private def skippedNote(r: PeriodImportResult): String =
    if r.skippedCount == 0 then "" else s", ${r.skippedCount} skipped"

  private def fetchSource(name: String, periods: PeriodImportResult => PeriodImportResult): Either[String, String] =
    name.trim.toLowerCase match
      case PeriodOFetch.name =>
        fetchPeriodsNow(Nil).map(periods).map(r => Plural.count(r.periods, "period") + skippedNote(r))
      case NaturalEarthLand.name =>
        NaturalEarthLand.run(ds, dryRun = false).map(r => s"${r.places} polit(y/ies) clipped")
      case other =>
        GazetteerImporter.byName(other)
          .toRight(s"unknown source '$other' (available: ${Bootstrap.sources.mkString(", ")})")
          .flatMap(imp => ImportRunner.fetchAndImport(imp, ds, dryRun = false, cfg.places.pointToleranceKm))
          .map(r => s"${r.places} place(s)")

object Bootstrap:
  /** The `jobs.kind` of an init job — what `histmersion jobs` lists. */
  val JobKind = InitPlan.JobKind

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed = 50

  /** Every name `init.fetch-sources` may hold. */
  def sources: List[String] =
    GazetteerImporter.all.map(_.name) ++ List(NaturalEarthLand.name, PeriodOFetch.name)
