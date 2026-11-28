package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.{CatalogRecord, EventCount, SiteTypes}
import com.histmersion.api.jobs.{JobInfo, JobKinds}
import com.histmersion.config.AppConfig
import com.histmersion.domain.{Caller, DeepTime}
import com.histmersion.config.MediaConfig
import com.histmersion.upstreams.wikidata.{WikidataEntities, WikidataItem}
import com.histmersion.util.jobs.{BackgroundJob, Job}
import com.histmersion.domain.catalogue.CatalogueJobs
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.catalogue.events.EventProviders
import com.histmersion.pluginhost.{HttpTransport, PluginEvents, Polite}

import java.time.Clock
import scala.annotation.tailrec
import com.histmersion.api.Api
import com.histmersion.api.catalogue.PlacesJobKinds
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The refinement of the Wikidata sites' types (a job, dry run or not): each site on the map asked for its classes
  * (P31) and their superclasses (P279), and given the curated type they fall under.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class SiteTypesControllers(
    sites: SiteTypeService,
    jobs: CatalogueJobs,
    settings: () => AppConfig,
    http: HttpTransport,
    clock: Clock
):
  private def ec = settings().events

  /** A polite client at `intervalMs` between requests to one host; the media politeness otherwise. */
  private def polite(intervalMs: PluginEvents.IntervalMs): Polite =
    PluginEvents.polite(http, settings().media, intervalMs)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def refineSiteTypes(caller: Caller, req: SiteTypes.Request)(using Creator.New): Either[Api.Error, JobInfo] =
    jobs.start(
      SiteTypesControllers.SiteTypesJobKind,
      JobKinds.Subject(s"site types of the Wikidata sites on the map${if req.dryRun.dryRun then " (dry run)" else ""}")
    ) { (_, progress) =>
      Right(BackgroundJob.Outcome.Summary(SiteTypes.Report.summary(runSiteTypes(req, progress)).value))
    }

  /** The refinement itself, inside the job (under its unit's permit). */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def runSiteTypes(req: SiteTypes.Request, progress: JobInfo.Progress => Unit)(using WritePermit): SiteTypes.Report =
    val started = DeepTime.SignedYear(clock.millis())
    val refresh = (if req.refresh.refresh then SiteTypeService.Refresh.Refresh else SiteTypeService.Refresh.Reuse)
    if req.dryRun.dryRun then
      SiteTypes.Report(
        dryRun = SiteTypes.Report.DryRun.DryRun,
        pending = SiteTypes.Report.Pending(sites.countSitesToType(refresh, None).value)
      )
    else
      val c = polite(MediaConfig.RequestIntervalMs(ec.wikidata.apiIntervalMs.value))
      val cap = req.limit.map(_.value).filter(_ > 0).getOrElse(Int.MaxValue)
      val api = WikidataEntities.Api(settings().media.wikidata.apiUrl.value)
      val maxlag = WikidataEntities.Maxlag(settings().media.wikidata.maxlag.value)
      val superCache = scala.collection.mutable.Map.empty[String, List[String]]
      def claims(ids: List[WikidataItem.Id], prop: EventProviders.WikidataProperty): Map[String, List[String]] =
        ids
          .grouped(math.max(1, ec.siteTypes.batch.value))
          .toList
          .flatMap { b =>
            c.json(HttpTransport.Url(WikidataEntities.url(api, b, maxlag).value))
              .toOption
              .flatMap(j => EventProviders.WikidataEvents.claims(j, prop))
              .getOrElse(Map.empty)
              .toList
          }
          .toMap

      /** The superclasses of `classes` within `depth` P279 steps, nearest first (cached per run). */
      def supers(classes: List[CatalogRecord.Wikidata.Class]): List[CatalogRecord.Wikidata.Class] =
        val unknown = classes.filterNot(SiteTypes.wikidataClasses.contains)
        @tailrec def walk(
            steps: Int,
            frontier: List[CatalogRecord.Wikidata.Class],
            acc: List[CatalogRecord.Wikidata.Class]
        ): List[CatalogRecord.Wikidata.Class] =
          if steps <= 0 then acc
          else
            val ask = frontier.filterNot(q => superCache.contains(q.value)).distinct
            if ask.nonEmpty then
              val got = claims(ask.map(q => WikidataItem.Id(q.value)), EventProviders.WikidataProperty("P279"))
              superCache ++= ask.map(q => q.value -> got.getOrElse(q.value, Nil))
            val next = frontier
              .flatMap(q => superCache.getOrElse(q.value, Nil))
              .map(CatalogRecord.Wikidata.Class(_))
              .distinct
              .filterNot(acc.contains)
            walk(steps - 1, next.filterNot(SiteTypes.wikidataClasses.contains), acc ++ next)
        walk(math.max(0, ec.siteTypes.superclassDepth.value), unknown, Nil)
      def next(limit: Int) =
        PageSize.option(limit).fold(Nil)(size => sites.sitesToType(size, refresh, Some(started)))
      // `var`s: the walk
      var report = SiteTypes.Report(dryRun = SiteTypes.Report.DryRun.Apply)
      var batch = next(math.min(ec.siteTypes.batch.value, cap))
      while batch.nonEmpty && report.asked.value < cap && !jobs.held do
        val p31 = claims(batch.map(_._2), EventProviders.WikidataProperty("P31"))
        val answered = batch.filter((_, q, _) => p31.contains(q.value))
        val types = answered.map { (pid, q, cls) =>
          val own = p31.getOrElse(q.value, Nil).map(CatalogRecord.Wikidata.Class(_))
          pid -> SiteTypes.wikidata(own, supers(own), cls)
        }
        sites.setSiteTypes(types)
        report = report.copy(
          asked = SiteTypes.Report.Asked(report.asked.value + answered.size),
          typed = EventCount.add(report.typed, EventCount.of(types.flatMap(_._2).map(t => EventCount.Key(t.value)))),
          untyped = SiteTypes.Report.Untyped(report.untyped.value + types.count(_._2.isEmpty)),
          failed = report.failed ++ Option
            .when(answered.isEmpty)(SiteTypes.Report.Failed(s"no answer for ${batch.head._2}…"))
            .toList
        )
        progress(JobInfo.Progress(s"${report.asked} site(s) asked · ${c.requests} request(s)"))
        batch = if answered.isEmpty then Nil
        else next(math.min(ec.siteTypes.batch.value, math.max(0, cap - report.asked.value)))
      report.copy(
        pending = SiteTypes.Report.Pending(sites.countSitesToType(refresh, Some(started)).value),
        requests = SiteTypes.Report.Requests(c.requests.value)
      )

object SiteTypesControllers:
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  val SiteTypesJobKind: Job.Kind = Job.Kind.assume(PlacesJobKinds.SiteTypes.kind.value)
