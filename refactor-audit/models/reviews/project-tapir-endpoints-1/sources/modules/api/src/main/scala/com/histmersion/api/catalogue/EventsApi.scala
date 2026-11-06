package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.Api
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given

/** Appended to [[Api.all]] before the event routes: otherwise `events/leads` would be read as the event `leads`. */
object EventsApi:

  type CorrectionsPart1Part1 = CorrectionsPart1Part1.T
  object CorrectionsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CorrectionsPart1Part2 = CorrectionsPart1Part2.T
  object CorrectionsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CorrectionsPart1Part3 = CorrectionsPart1Part3.T
  object CorrectionsPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CorrectionsPart1Part4 = CorrectionsPart1Part4.T
  object CorrectionsPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksPart1 = LinksPart1.T
  object LinksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinksPart1Part1 = ApplyLinksPart1Part1.T
  object ApplyLinksPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private lazy val eventQuery: EndpointInput[Api.EventQuery] =
    Api.filterQuery
      .and(query[Option[String]]("text"))
      .and(query[Option[String]]("kind"))
      .and(query[Option[String]]("historicity"))
      .and(query[Option[String]]("placeId"))
      .and(query[Option[String]]("tier"))
      .and(query[Option[Boolean]]("context"))
      .and(query[Option[String]]("area"))
      .mapTo[Api.EventQuery]

  lazy val importEvents: PublicEndpoint[EventImportRequest, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "events" / "import")
      .in(jsonBody[EventImportRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val rederive: PublicEndpoint[EventRederiveRequest, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "events" / "rederive")
      .in(jsonBody[EventRederiveRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val corrections
      : PublicEndpoint[(Option[com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part1], Option[com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part2], Option[com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part3], Option[com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part4]), Api.Error, Correction.List, Any] =
    (endpoint.get
      .in("api" / "events" / "corrections")
      .in(query[Option[String]]("status"))
      .in(query[Option[String]]("event"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .errorOut(Api.writeErrors)
      .out(jsonBody[Correction.List])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part1], _root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part4])]((refactorDecoded4: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded4)._1).map(refactorElement9 => _root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part1(refactorElement9)), ((refactorDecoded4)._2).map(refactorElement10 => _root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part2(refactorElement10)), ((refactorDecoded4)._3).map(refactorElement11 => _root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part3(refactorElement11)), ((refactorDecoded4)._4).map(refactorElement12 => _root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part4(refactorElement12))))((refactorEncoded4: (_root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part1], _root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.catalogue.EventsApi.CorrectionsPart1Part4])) => (((refactorEncoded4)._1).map(refactorElement13 => (refactorElement13).value), ((refactorEncoded4)._2).map(refactorElement14 => (refactorElement14).value), ((refactorEncoded4)._3).map(refactorElement15 => (refactorElement15).value), ((refactorEncoded4)._4).map(refactorElement16 => (refactorElement16).value)))

  lazy val catalogues: PublicEndpoint[Unit, Api.Error, List[CatalogProviderInfo], Any] =
    endpoint.get
      .in("api" / "events" / "catalogues")
      .errorOut(jsonBody[Api.Error])
      .out(jsonBody[List[CatalogProviderInfo]])

  lazy val leads: PublicEndpoint[Api.EventQuery, Api.Error, EventLead.List, Any] =
    endpoint.get
      .in("api" / "events" / "leads")
      .in(eventQuery)
      .errorOut(jsonBody[Api.Error])
      .out(jsonBody[EventLead.List])

  lazy val candidatesJob: PublicEndpoint[EventCandidatesRequest, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "events" / "candidates")
      .in(jsonBody[EventCandidatesRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val links: PublicEndpoint[com.histmersion.api.catalogue.EventsApi.LinksPart1, Api.Error, EventLinkList, Any] =
    (endpoint.get
      .in("api" / "events" / path[String]("id") / "links")
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinkList])).mapIn[_root_.com.histmersion.api.catalogue.EventsApi.LinksPart1]((refactorDecoded2: _root_.java.lang.String) => _root_.com.histmersion.api.catalogue.EventsApi.LinksPart1(refactorDecoded2))((refactorEncoded2: _root_.com.histmersion.api.catalogue.EventsApi.LinksPart1) => (refactorEncoded2).value)

  lazy val applyLinks: PublicEndpoint[(com.histmersion.api.catalogue.EventsApi.ApplyLinksPart1Part1, EventLinksRequest), Api.Error, EventLinksResult, Any] =
    (endpoint.post
      .in("api" / "events" / path[String]("id") / "links")
      .in(jsonBody[EventLinksRequest])
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinksResult])).mapIn[(_root_.com.histmersion.api.catalogue.EventsApi.ApplyLinksPart1Part1, _root_.com.histmersion.api.catalogue.EventLinksRequest)]((refactorDecoded3: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.EventLinksRequest)) => (_root_.com.histmersion.api.catalogue.EventsApi.ApplyLinksPart1Part1((refactorDecoded3)._1), (refactorDecoded3)._2))((refactorEncoded3: (_root_.com.histmersion.api.catalogue.EventsApi.ApplyLinksPart1Part1, _root_.com.histmersion.api.catalogue.EventLinksRequest)) => (((refactorEncoded3)._1).value, (refactorEncoded3)._2))

  lazy val refineSiteTypes: PublicEndpoint[SiteTypes.Request, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "site-types" / "refine")
      .in(jsonBody[SiteTypes.Request])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val all: List[AnyEndpoint] =
    List(importEvents, rederive, corrections, catalogues, leads, candidatesJob, links, applyLinks, refineSiteTypes)
