package com.histmersion
package server
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*

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
import com.histmersion.util.jobs.Job
import com.histmersion.util.storage.Location

import java.nio.file.{Files, Path}
import com.histmersion.api.catalogue.SkippedPeriod
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Period
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.catalogue.places.GazetteerControllers

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
    download: HttpDownload.Url => Either[Problem, HttpDownload.Text] = HttpDownload().text,
    gazetteerDownload: GazetteerLoad.Download = HttpDownload().file
):

  private val ds = deps.ds
  private def cfg = deps.cfg
  private val maintenance = deps.maintenance

  /** The init job running now, if any — one at a time ([[BackgroundJob]]): two concurrent seeds would only race each
    * other over the same upserts.
    */
  private val slot = BackgroundJob.Slot()

  private def dataDir: Path = Path.of(cfg.init.dataDir.value)
  private def periodsSeed: Path = dataDir.resolve(cfg.periods.seedFile.value)

  // ── periods ──────────────────────────────────────────────────────────────────────────────────

  /** The named periods, and how the seed, a sent dataset and the live dump come in. */
  val periods: PeriodService = PeriodService(deps.transactor)
  val periodImports: PeriodImports =
    new PeriodImports(
      periods,
      () =>
        PeriodSources(
          PeriodSources.PeriodoUrl(cfg.periods.periodoUrl.value),
          cfg.periods.collections.map(c => PeriodSources.Collections(c.value))
        ),
      () => bundledSeed,
      url =>
        download(HttpDownload.Url(url.value))
          .map(t => PeriodImports.Document(t.value))
    )

  /** The bundled seed's text. */
  private def bundledSeed: Either[Problem, PeriodImports.Document] =
    if !Files.isRegularFile(periodsSeed) then
      Left(Problem.Unclassified(Legacy.Text(s"no period seed at $periodsSeed (set init.data-dir)")))
    else Right(PeriodImports.Document(Files.readString(periodsSeed)))

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
  def init(fetch: Boolean): Either[Api.Error, JobInfo] =
    maintenance.guard(WriteGate.OperationName("init"))(start(plan(fetch, onlyEmpty = false)))

  // ── the external gazetteer sources (#1064) ─────────────────────────────────────────────────────

  /** `GET /api/places/sources` — the pinned extracts, the LPF drop folder, and what was imported. */
  def gazetteerSources: List[Gazetteer.SourceInfo] =
    gazetteerImports.sources()

  /** `POST /api/places/load?source=&apply=` — one external source as an init job: a dry run that reports what it would
    * do (the default), or the import itself.
    */
  def loadGazetteer(source: Gazetteer.Kind.Wire, apply: GazetteerLoad.LoadApply): Either[Api.Error, JobInfo] =
    maintenance.guard(WriteGate.OperationName("places/load")):
      Gazetteer.Kind
        .parse(source)
        .toRight(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"unknown gazetteer source '$source' (available: ${GazetteerLoad.names.mkString(", ")})")
            ),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
        .flatMap(k => start(InitPlan(fetch = List(InitPlan.Fetch(k.wire.value)), check = InitPlan.Check(!apply.value))))

  /** What an init job will import: everything bundled, or (on a first start) only what is empty, plus the network
    * sources when asked. Decided up front and stored on the job (#770).
    */
  private[server] def plan(fetch: Boolean, onlyEmpty: Boolean): InitPlan =
    InitPlan(
      gazetteer = InitPlan.Gazetteer(!onlyEmpty || places.count().value == 0),
      periodPresets = InitPlan.PeriodPresets(!onlyEmpty || periods.count().value == 0 || periodImports.seedOutdated),
      fetch = if fetch then cfg.init.sources.map(s => InitPlan.Fetch(s.value)) else Nil
    )

  private def start(p: InitPlan): Either[Api.Error, JobInfo] =
    BackgroundJob.start(
      ds,
      Bootstrap.JobKind,
      Some(InitPlan.Detail(Some(p))),
      slot,
      BackgroundJob.Work("an init job"),
      pins = deps.pins,
      stops = Some(deps.jobStops)
    )((job, note) => run(Job.Id.assume(job.id.value), p, note))

  private def run(id: Job.Id, p: InitPlan, note: JobInfo.Progress => Unit): BackgroundJob.Outcome =
    // the definitions a period import left out go on the job (#771), not just into the log
    def withSkipped(r: Period.ImportResult): Period.ImportResult =
      if r.skippedCount.value > 0 then
        BackgroundJob.amendDetail(ds, id) { found =>
          val d = if found == JobDetail.empty then InitPlan.Detail(Some(p)) else found
          d.change(SkippedPeriod.Skipped)(s => (s ++ r.skipped).take(Bootstrap.SkippedListed))
            .change(SkippedPeriod.SkippedCount)(c => Period.ImportResult.SkippedCount(c.value + r.skippedCount.value))
        }
      r
    val steps: List[(String, () => Either[String, String])] =
      List(
        Option.when(p.gazetteer.value)("gazetteer" -> (() => seedPlaces(note))),
        Option.when(p.periodPresets.value)(
          "period presets" -> (() =>
            periodImports
              .applySeed(replace = PeriodImports.Replace(false))
              .map(withSkipped)
              .map(r =>
                s"${Plural.count(Plural.Count(r.periods.value.toLong), Plural.CountSingular("period"))}, ${Plural.count(Plural.Count(r.presets.value.toLong), Plural.CountSingular("preset"))}" + skippedNote(
                  r
                )
              )
              .left
              .map(_.sentence)
          )
        )
      ).flatten ++ p.fetch.map(s =>
        s"${if p.check.value then "dry run of" else "fetch"} $s" -> (() =>
          fetchSource(s.value, withSkipped, p.check.value, note)
        )
      )
    val results = steps.map { (name, step) =>
      if maintenance.active then name -> Left("a restore started; skipped")
      else
        note(JobInfo.Progress(name))
        scribe.info(s"[init] $name…")
        val r = try step()
        catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        r.fold(e => scribe.warn(s"[init] $name failed: $e"), ok => scribe.info(s"[init] $name: $ok"))
        name -> r
    }
    val failed = results.collect { case (n, Left(e)) => s"$n: $e" }
    val done = results.collect { case (n, Right(s)) => s"$n: $s" }
    val summary = if done.isEmpty then "nothing to do" else done.mkString("; ")
    BackgroundJob.Outcome(
      Some(BackgroundJob.Outcome.Summary(summary)),
      Option.when(failed.nonEmpty)(Problem.Unclassified(Legacy.Text(failed.mkString("; "))))
    )

  /** The bundled gazetteer, the way `histmersion places seed` imports it. */
  private def seedPlaces(note: JobInfo.Progress => Unit): Either[String, String] =
    gazetteerImports.seed(deps.storage.store(Location.GazetteerSeed), note).left.map(_.sentence).map(_.value)

  private def skippedNote(r: Period.ImportResult): String =
    if r.skippedCount.value == 0 then "" else s", ${r.skippedCount} skipped"

  private def fetchSource(
      name: String,
      periods: Period.ImportResult => Period.ImportResult,
      check: Boolean = false,
      note: JobInfo.Progress => Unit = _ => ()
  ): Either[String, String] =
    val gazetteer = Gazetteer.Kind.parse(Gazetteer.Kind.Wire(name))
    if check && gazetteer.isEmpty then Left(s"'$name' has no dry run (only ${GazetteerLoad.names.mkString(", ")})")
    else
      gazetteer match
        case Some(k) =>
          gazetteerImports
            .load(k, apply = GazetteerLoad.LoadApply(!check), note)
            .left
            .map(_.sentence)
            .map(_.map(_.describe).mkString("; "))
        case None => fetchNetwork(name, periods)

  private def fetchNetwork(name: String, periods: Period.ImportResult => Period.ImportResult): Either[String, String] =
    name.trim.toLowerCase match
      case PeriodOFetch.name =>
        periodImports
          .fetch(Nil)
          .left
          .map(_.sentence)
          .map(periods)
          .map(r =>
            s"${Plural.count(Plural.Count(r.periods.value.toLong), Plural.CountSingular("period"))}${skippedNote(r)}"
          )
      case NaturalEarthLand.name =>
        gazetteerImports
          .land(dryRun = GazetteerControllers.DryRun(false))
          .left
          .map(_.sentence)
          .map(r => s"${r.places} polit(y/ies) clipped")
      case other =>
        val available = Bootstrap.sources ++ gazetteerImports.thirdParty
        gazetteerImports
          .source(
            BuiltinGazetteers.Entry.Name(other),
            Problem.Unclassified(Legacy.Text(s"unknown source '$other' (available: ${available.mkString(", ")})"))
          )
          .flatMap(src => gazetteerImports.fetch(src, dryRun = GazetteerControllers.DryRun(false)))
          .left
          .map(_.sentence)
          .map(r => s"${r.places} place(s)")

object Bootstrap:
  /** The `jobs.kind` of an init job — what `histmersion jobs` lists. */
  val JobKind: Job.Kind = Job.Kind.assume(InitPlan.JobKind)

  /** The `seed_versions` name of the bundled period seed. */
  val PeriodSeed: String = PeriodImports.SeedName

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed: Int = PeriodImports.SkippedListed

  /** Every name `init.fetch-sources` may hold (and the sources a plugin registers). */
  def sources: List[BuiltinGazetteers.Entry.Name] =
    BuiltinGazetteers.names ++ List(NaturalEarthLand.name, PeriodOFetch.name).map(BuiltinGazetteers.Entry.Name(_)) ++
      GazetteerLoad.names.map(n => BuiltinGazetteers.Entry.Name(n.value))
