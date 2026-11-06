package com.histmersion.server

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.api.{Plural, Write}
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
import com.histmersion.api.catalogue.SkippedPeriod
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Period

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
    download: Bootstrap.DownloadArg1 => Either[Bootstrap.DownloadResultPart1, Bootstrap.DownloadResultPart2] = HttpDownload().text,
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
  private def bundledSeed: Either[com.histmersion.server.Bootstrap.BundledSeedResultPart1, com.histmersion.server.Bootstrap.BundledSeedResultPart2] =
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
  def init(fetch: com.histmersion.server.Bootstrap.InitFetch): Either[Api.Error, JobInfo] =
    maintenance.guard("init")(start(plan(fetch, onlyEmpty = false)))

  // ── the external gazetteer sources (#1064) ─────────────────────────────────────────────────────

  /** `GET /api/places/sources` — the pinned extracts, the LPF drop folder, and what was imported. */
  def gazetteerSources: List[Gazetteer.SourceInfo] =
    gazetteerImports.sources()

  /** `POST /api/places/load?source=&apply=` — one external source as an init job: a dry run that reports what it would
    * do (the default), or the import itself.
    */
  def loadGazetteer(source: com.histmersion.server.Bootstrap.LoadGazetteerSource, apply: com.histmersion.server.Bootstrap.LoadGazetteerApply): Either[Api.Error, JobInfo] =
    maintenance.guard("places/load"):
      Gazetteer.Kind
        .parse(source)
        .toRight(
          Api.Error(
            s"unknown gazetteer source '$source' (available: ${GazetteerLoad.names.mkString(", ")})",
            Some(Write.CodeInvalid)
          )
        )
        .flatMap(k => start(InitPlan(fetch = List(k.wire), check = !apply)))

  /** What an init job will import: everything bundled, or (on a first start) only what is empty, plus the network
    * sources when asked. Decided up front and stored on the job (#770).
    */
  private[server] def plan(fetch: com.histmersion.server.Bootstrap.PlanFetch, onlyEmpty: com.histmersion.server.Bootstrap.PlanOnlyEmpty): InitPlan =
    InitPlan(
      gazetteer = !onlyEmpty || places.count() == 0,
      periodPresets = !onlyEmpty || periods.count() == 0 || periodImports.seedOutdated,
      fetch = if fetch then cfg.init.sources else Nil
    )

  private def start(p: InitPlan): Either[Api.Error, JobInfo] =
    BackgroundJob.start(
      ds,
      Bootstrap.JobKind,
      Some(InitPlan.Detail(Some(p))),
      slot,
      "an init job",
      pins = deps.pins,
      stops = Some(deps.jobStops)
    )((job, note) => run(job.id, p, note))

  private def run(id: com.histmersion.server.Bootstrap.RunId, p: InitPlan, note: com.histmersion.server.Bootstrap.RunNoteArg1 => Unit): BackgroundJob.Outcome =
    // the definitions a period import left out go on the job (#771), not just into the log
    def withSkipped(r: Period.ImportResult): Period.ImportResult =
      if r.skippedCount > 0 then
        BackgroundJob.amendDetail(ds, id) { found =>
          val d = if found == JobDetail.empty then InitPlan.Detail(Some(p)) else found
          d.change(SkippedPeriod.Skipped)(s => (s ++ r.skipped).take(Bootstrap.SkippedListed))
            .change(SkippedPeriod.SkippedCount)(_ + r.skippedCount)
        }
      r
    val steps: List[(com.histmersion.server.Bootstrap.RunStepsPart1, () => Either[com.histmersion.server.Bootstrap.RunStepsPart2ResultPart1, com.histmersion.server.Bootstrap.RunStepsPart2ResultPart2])] =
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
  private def seedPlaces(note: com.histmersion.server.Bootstrap.SeedPlacesNoteArg1 => Unit): Either[com.histmersion.server.Bootstrap.SeedPlacesResultPart1, com.histmersion.server.Bootstrap.SeedPlacesResultPart2] =
    gazetteerImports.seed(deps.storage.store(Location.GazetteerSeed), note)

  private def skippedNote(r: Period.ImportResult): com.histmersion.server.Bootstrap.SkippedNoteResult =
    if r.skippedCount == 0 then "" else s", ${r.skippedCount} skipped"

  private def fetchSource(
      name: com.histmersion.server.Bootstrap.FetchSourceName,
      periods: Period.ImportResult => Period.ImportResult,
      check: com.histmersion.server.Bootstrap.FetchSourceCheck = false,
      note: com.histmersion.server.Bootstrap.FetchSourceNoteArg1 => Unit = _ => ()
  ): Either[com.histmersion.server.Bootstrap.FetchSourceResultPart1, com.histmersion.server.Bootstrap.FetchSourceResultPart2] =
    val gazetteer = Gazetteer.Kind.parse(name)
    if check && gazetteer.isEmpty then Left(s"'$name' has no dry run (only ${GazetteerLoad.names.mkString(", ")})")
    else
      gazetteer match
        case Some(k) =>
          gazetteerImports.load(k, apply = !check, note).map(_.map(_.describe).mkString("; "))
        case None => fetchNetwork(name, periods)

  private def fetchNetwork(name: com.histmersion.server.Bootstrap.FetchNetworkName, periods: Period.ImportResult => Period.ImportResult): Either[com.histmersion.server.Bootstrap.FetchNetworkResultPart1, com.histmersion.server.Bootstrap.FetchNetworkResultPart2] =
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
  type RunStepsPart1 = RunStepsPart1.T
  object RunStepsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStepsPart2ResultPart1 = RunStepsPart2ResultPart1.T
  object RunStepsPart2ResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStepsPart2ResultPart2 = RunStepsPart2ResultPart2.T
  object RunStepsPart2ResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DownloadArg1 = DownloadArg1.T
  object DownloadArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart2 = DownloadResultPart2.T
  object DownloadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type BundledSeedResultPart1 = BundledSeedResultPart1.T
  object BundledSeedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BundledSeedResultPart2 = BundledSeedResultPart2.T
  object BundledSeedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InitFetch = InitFetch.T
  object InitFetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadGazetteerSource = LoadGazetteerSource.T
  object LoadGazetteerSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadGazetteerApply = LoadGazetteerApply.T
  object LoadGazetteerApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanFetch = PlanFetch.T
  object PlanFetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanOnlyEmpty = PlanOnlyEmpty.T
  object PlanOnlyEmpty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunId = RunId.T
  object RunId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunNoteArg1 = RunNoteArg1.T
  object RunNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPlacesResultPart1 = SeedPlacesResultPart1.T
  object SeedPlacesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPlacesResultPart2 = SeedPlacesResultPart2.T
  object SeedPlacesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedPlacesNoteArg1 = SeedPlacesNoteArg1.T
  object SeedPlacesNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkippedNoteResult = SkippedNoteResult.T
  object SkippedNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchSourceResultPart1 = FetchSourceResultPart1.T
  object FetchSourceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchSourceResultPart2 = FetchSourceResultPart2.T
  object FetchSourceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchSourceName = FetchSourceName.T
  object FetchSourceName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchSourceCheck = FetchSourceCheck.T
  object FetchSourceCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchSourceNoteArg1 = FetchSourceNoteArg1.T
  object FetchSourceNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchNetworkResultPart1 = FetchNetworkResultPart1.T
  object FetchNetworkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchNetworkResultPart2 = FetchNetworkResultPart2.T
  object FetchNetworkResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchNetworkName = FetchNetworkName.T
  object FetchNetworkName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodSeed = PeriodSeedValue
  type PeriodSeedValue = PeriodSeedValue.T
  object PeriodSeedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkippedListed = SkippedListedValue
  type SkippedListedValue = SkippedListedValue.T
  object SkippedListedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesResult = SourcesResult.T
  object SourcesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val JobKind = InitPlan.JobKind

  /** The `seed_versions` name of the bundled period seed. */
  val PeriodSeed: com.histmersion.server.Bootstrap.PeriodSeed = PeriodImports.SeedName

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed: com.histmersion.server.Bootstrap.SkippedListed = PeriodImports.SkippedListed

  /** Every name `init.fetch-sources` may hold (and the sources a plugin registers). */
  def sources: List[com.histmersion.server.Bootstrap.SourcesResult] =
    BuiltinGazetteers.names ++ List(NaturalEarthLand.name, PeriodOFetch.name) ++ GazetteerLoad.names
