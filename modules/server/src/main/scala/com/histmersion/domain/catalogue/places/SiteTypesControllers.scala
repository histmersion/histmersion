package com.histmersion.domain.catalogue.places

import com.histmersion.api.{
  ApiError,
  EventCount,
  JobInfo,
  JobKindsAdapter,
  SiteTypes,
  SiteTypesReport,
  SiteTypesRequest
}
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueJobs
import com.histmersion.domain.catalogue.events.EventProviders
import com.histmersion.pluginhost.{HttpTransport, PluginEvents, Polite}

import java.time.Clock
import scala.annotation.tailrec

/** The refinement of the Wikidata sites' types (a job, dry run or not): each site on the map asked for its classes
  * (P31) and their superclasses (P279), and given the curated type they fall under.
  */
final class SiteTypesControllers(
    sites: SiteTypeService,
    jobs: CatalogueJobs,
    settings: () => AppConfig,
    http: HttpTransport,
    clock: Clock
):
  private def ec = settings().events

  /** A polite client at `intervalMs` between requests to one host; the media politeness otherwise. */
  private def polite(intervalMs: Int): Polite = PluginEvents.polite(http, settings().media, intervalMs)

  def refineSiteTypes(caller: Caller, req: SiteTypesRequest): Either[ApiError, JobInfo] =
    jobs.start(
      SiteTypesControllers.SiteTypesJobKind,
      s"site types of the Wikidata sites on the map${if req.dryRun then " (dry run)" else ""}",
      write = !req.dryRun
    ) { (_, progress) =>
      Right(SiteTypesReport.summary(runSiteTypes(req, progress)))
    }

  def runSiteTypes(req: SiteTypesRequest, progress: String => Unit): SiteTypesReport =
    val started = clock.millis()
    if req.dryRun then SiteTypesReport(dryRun = true, pending = sites.countSitesToType(req.refresh, None))
    else
      val c = polite(ec.wikidata.apiIntervalMs)
      val cap = req.limit.filter(_ > 0).getOrElse(Int.MaxValue)
      val api = settings().media.wikidata.apiUrl
      val superCache = scala.collection.mutable.Map.empty[String, List[String]]
      def claims(ids: List[String], prop: String): Map[String, List[String]] =
        ids
          .grouped(ec.siteTypes.batch.max(1))
          .toList
          .flatMap { b =>
            c.json(com.histmersion.upstreams.WikidataEntities.url(api, b, settings().media.wikidata.maxlag))
              .flatMap(EventProviders.WikidataEvents.claims(_, prop))
              .getOrElse(Map.empty)
              .toList
          }
          .toMap

      /** The superclasses of `classes` within `depth` P279 steps, nearest first (cached per run). */
      def supers(classes: List[String]): List[String] =
        val unknown = classes.filterNot(SiteTypes.wikidataClasses.contains)
        @tailrec def walk(steps: Int, frontier: List[String], acc: List[String]): List[String] =
          if steps <= 0 then acc
          else
            val ask = frontier.filterNot(superCache.contains).distinct
            if ask.nonEmpty then
              val got = claims(ask, "P279")
              superCache ++= ask.map(q => q -> got.getOrElse(q, Nil))
            val next = frontier.flatMap(q => superCache.getOrElse(q, Nil)).distinct.filterNot(acc.contains)
            walk(steps - 1, next.filterNot(SiteTypes.wikidataClasses.contains), acc ++ next)
        walk(ec.siteTypes.superclassDepth.max(0), unknown, Nil)
      // `var`s: the walk
      var report = SiteTypesReport(dryRun = false)
      var batch = sites.sitesToType(ec.siteTypes.batch.min(cap), req.refresh, Some(started))
      while batch.nonEmpty && report.asked < cap && !jobs.held do
        val p31 = claims(batch.map(_._2), "P31")
        val answered = batch.filter((_, q, _) => p31.contains(q))
        val types = answered.map { (pid, q, cls) =>
          val own = p31.getOrElse(q, Nil)
          pid -> SiteTypes.wikidata(own, supers(own), cls)
        }
        sites.setSiteTypes(types)
        report = report.copy(
          asked = report.asked + answered.size,
          typed = EventCount.add(report.typed, EventCount.of(types.flatMap(_._2))),
          untyped = report.untyped + types.count(_._2.isEmpty),
          failed = report.failed ++ Option.when(answered.isEmpty)(s"no answer for ${batch.head._2}…").toList
        )
        progress(s"${report.asked} site(s) asked · ${c.requests} request(s)")
        batch = if answered.isEmpty then Nil
        else
          sites.sitesToType(
            ec.siteTypes.batch.min((cap - report.asked).max(0)),
            req.refresh,
            Some(started)
          )
      report.copy(pending = sites.countSitesToType(req.refresh, Some(started)), requests = c.requests)

object SiteTypesControllers:
  val SiteTypesJobKind: String = JobKindsAdapter.SiteTypes.value
