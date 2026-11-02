package com.histmersion.server

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.periods.{PeriodImports, PeriodOFetch, PeriodService, PeriodSources}
import com.histmersion.domain.catalogue.places.{
  BuiltinGazetteers,
  GazetteerImports,
  GazetteerLoad,
  NaturalEarthLand,
  PlaceService
}
import com.histmersion.util.storage.Location

import java.nio.file.{Files, Path}

/** Named periods (#719) and first-run data (#720): the period-preset write path and the `init` job.
  *
  * The data a new install needs ships WITH the server — the gazetteer (`<init.data-dir>/places`, the repo's
  * `db/places`) and the period presets (`<init.data-dir>/periods`, the repo's `db/periods`); `scripts/bundle/server.sh`
  * copies both into the bundle and [[Supervisor]] points `init.data-dir` there. The `init` job imports them server-side
  * — the CLI is a pure HTTP client and has no files to send — and, with `fetch`, downloads the network gazetteers and
  * PeriodO as well. A starting server runs the same job by itself for whichever of the two tables is still EMPTY
  * ([[autoStart]]), in the background, so binding the port never waits for it.
  *
  * `download` is the seam a test substitutes for the network.
  */
final class Bootstrap(
    deps: Deps,
    download: String => Either[String, String] = HttpDownload().text,
    gazetteerDownload: GazetteerLoad.Download = HttpDownload().file
):

  private val ds = deps.ds
  private def cfg = deps.cfg
  private val maintenance = deps.maintenance

  /** The init job running now, if any — one at a time ([[BackgroundJob]]): two concurrent seeds would only race each
    * other over the same upserts.
    */
  private val slot = BackgroundJob.Slot()

  private def dataDir: Path = Path.of(cfg.init.dataDir)
  private def periodsSeed: Path = dataDir.resolve(cfg.periods.seedFile)

  // ── periods ──────────────────────────────────────────────────────────────────────────────────

  /** The named periods, and how the seed, a sent dataset and the live dump come in. */
  val periods: PeriodService = PeriodService(deps.transactor)
  val periodImports: PeriodImports =
    PeriodImports(
      periods,
      () => PeriodSources(cfg.periods.periodoUrl, cfg.periods.collections),
      () => bundledSeed,
      download
    )

  /** The bundled seed's text. */
  private def bundledSeed: Either[String, String] =
    if !Files.isRegularFile(periodsSeed) then Left(s"no period seed at $periodsSeed (set init.data-dir)")
    else Right(Files.readString(periodsSeed))

  // ── the gazetteer ───────────────────────────────────────────────────────────────────────────────

  /** The gazetteer's records, and how its seed, its pinned extracts, its drop folder, the plugins' sources and the land
    * come in.
    */
  private val places: PlaceService = PlaceService(deps.transactor)
  private val gazetteerImports: GazetteerImports = CatalogueWiring.gazetteer(deps, places, gazetteerDownload, download)

  // ── init ─────────────────────────────────────────────────────────────────────────────────────

  /** On a server start (`init.auto`): the init job for whichever table is empty — or, for the periods, whose bundled
    * seed is newer than the one applied — or nothing.
    */
  def autoStart(): Option[JobInfo] =
    val p = plan(fetch = false, onlyEmpty = true)
    if p.isEmpty then None
    else
      scribe.info(
        s"on start (an empty table, or a newer bundled seed): importing the ${InitPlan.describe(p)} from $dataDir in the background"
      )
      start(p).toOption

  /** `POST /api/init` — the bundled gazetteer and period presets (re-imported: every write is an upsert) and, with
    * `fetch`, the network sources in `init.fetch-sources`.
    */
  def init(fetch: Boolean): Either[ApiError, JobInfo] =
    maintenance.guard("init")(start(plan(fetch, onlyEmpty = false)))

  // ── the external gazetteer sources (#1064) ─────────────────────────────────────────────────────

  /** `GET /api/places/sources` — the pinned extracts, the LPF drop folder, and what was imported. */
  def gazetteerSources: List[GazetteerSourceInfo] =
    gazetteerImports.sources()

  /** `POST /api/places/load?source=&apply=` — one external source as an init job: a dry run that reports what it would
    * do (the default), or the import itself.
    */
  def loadGazetteer(source: String, apply: Boolean): Either[ApiError, JobInfo] =
    maintenance.guard("places/load"):
      GazetteerKind
        .parse(source)
        .toRight(
          ApiError(
            s"unknown gazetteer source '$source' (available: ${GazetteerLoad.names.mkString(", ")})",
            Some(Write.CodeInvalid)
          )
        )
        .flatMap(k => start(InitPlan(fetch = List(k.wire), check = !apply)))

  /** What an init job will import: everything bundled, or (on a first start) only what is empty, plus the network
    * sources when asked. Decided up front and stored on the job (#770).
    */
  private[server] def plan(fetch: Boolean, onlyEmpty: Boolean): InitPlan =
    InitPlan(
      gazetteer = !onlyEmpty || places.count() == 0,
      periodPresets = !onlyEmpty || periods.count() == 0 || periodImports.seedOutdated,
      fetch = if fetch then cfg.init.sources else Nil
    )

  private def start(p: InitPlan): Either[ApiError, JobInfo] =
    BackgroundJob.start(
      ds,
      Bootstrap.JobKind,
      Some(JobDetail(init = Some(p))),
      slot,
      "an init job",
      pins = deps.pins,
      stops = Some(deps.jobStops)
    )((job, note) => run(job.id, p, note))

  private def run(id: String, p: InitPlan, note: String => Unit): BackgroundJob.Outcome =
    // the definitions a period import left out go on the job (#771), not just into the log
    def withSkipped(r: PeriodImportResult): PeriodImportResult =
      if r.skippedCount > 0 then
        BackgroundJob.amendDetail(ds, id) { found =>
          val d = if found == JobDetail() then JobDetail(init = Some(p)) else found
          d.copy(
            skippedPeriods = (d.skippedPeriods ++ r.skipped).take(Bootstrap.SkippedListed),
            skippedPeriodCount = d.skippedPeriodCount + r.skippedCount
          )
        }
      r
    val steps: List[(String, () => Either[String, String])] =
      List(
        Option.when(p.gazetteer)("gazetteer" -> (() => seedPlaces(note))),
        Option.when(p.periodPresets)(
          "period presets" -> (() =>
            periodImports
              .applySeed(replace = false)
              .map(withSkipped)
              .map(r => s"${Plural.count(r.periods, "period")}, ${Plural.count(r.presets, "preset")}" + skippedNote(r))
          )
        )
      ).flatten ++ p.fetch.map(s =>
        s"${if p.check then "dry run of" else "fetch"} $s" -> (() => fetchSource(s, withSkipped, p.check, note))
      )
    val results = steps.map { (name, step) =>
      if maintenance.active then name -> Left("a restore started; skipped")
      else
        note(name)
        scribe.info(s"[init] $name…")
        val r = try step()
        catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        r.fold(e => scribe.warn(s"[init] $name failed: $e"), ok => scribe.info(s"[init] $name: $ok"))
        name -> r
    }
    val failed = results.collect { case (n, Left(e)) => s"$n: $e" }
    val done = results.collect { case (n, Right(s)) => s"$n: $s" }
    val summary = if done.isEmpty then Some("nothing to do") else Some(done.mkString("; "))
    BackgroundJob.Outcome(summary, Option.when(failed.nonEmpty)(failed.mkString("; ")))

  /** The bundled gazetteer, the way `histmersion places seed` imports it. */
  private def seedPlaces(note: String => Unit): Either[String, String] =
    gazetteerImports.seed(deps.storage.store(Location.GazetteerSeed), note)

  private def skippedNote(r: PeriodImportResult): String =
    if r.skippedCount == 0 then "" else s", ${r.skippedCount} skipped"

  private def fetchSource(
      name: String,
      periods: PeriodImportResult => PeriodImportResult,
      check: Boolean = false,
      note: String => Unit = _ => ()
  ): Either[String, String] =
    val gazetteer = GazetteerKind.parse(name)
    if check && gazetteer.isEmpty then Left(s"'$name' has no dry run (only ${GazetteerLoad.names.mkString(", ")})")
    else
      gazetteer match
        case Some(k) =>
          gazetteerImports.load(k, apply = !check, note).map(_.map(_.describe).mkString("; "))
        case None => fetchNetwork(name, periods)

  private def fetchNetwork(name: String, periods: PeriodImportResult => PeriodImportResult): Either[String, String] =
    name.trim.toLowerCase match
      case PeriodOFetch.name =>
        periodImports.fetch(Nil).map(periods).map(r => Plural.count(r.periods, "period") + skippedNote(r))
      case NaturalEarthLand.name =>
        gazetteerImports.land(dryRun = false).map(r => s"${r.places} polit(y/ies) clipped")
      case other =>
        val available = Bootstrap.sources ++ gazetteerImports.thirdParty
        gazetteerImports
          .source(other, s"unknown source '$other' (available: ${available.mkString(", ")})")
          .flatMap(src => gazetteerImports.fetch(src, dryRun = false))
          .map(r => s"${r.places} place(s)")

object Bootstrap:
  /** The `jobs.kind` of an init job — what `histmersion jobs` lists. */
  val JobKind = InitPlan.JobKind

  /** The `seed_versions` name of the bundled period seed. */
  val PeriodSeed: String = PeriodImports.SeedName

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed: Int = PeriodImports.SkippedListed

  /** Every name `init.fetch-sources` may hold (and the sources a plugin registers). */
  def sources: List[String] =
    BuiltinGazetteers.names ++ List(NaturalEarthLand.name, PeriodOFetch.name) ++ GazetteerLoad.names
