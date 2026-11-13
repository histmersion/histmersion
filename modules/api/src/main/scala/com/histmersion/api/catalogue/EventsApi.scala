package com.histmersion
package api.catalogue
import com.histmersion.api.json.NewtypeCodecs.given
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.StatusCode
import com.histmersion.api.Api
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given
import com.histmersion.api.personal.UnreadPage

/** Appended to [[Api.all]] before the event routes: otherwise `events/leads` would be read as the event `leads`. */
object EventsApi:

  private lazy val eventQuery: EndpointInput[Api.EventQuery] =
    Api.filterQuery
      .and(query[Option[String]]("text"))
      .and(query[Option[String]]("kind"))
      .and(query[Option[String]]("historicity"))
      .and(query[Option[String]]("placeId"))
      .and(query[Option[String]]("tier"))
      .and(query[Option[Boolean]]("context"))
      .and(query[Option[String]]("area"))
      .map(Api.EventQuery.read)(Api.EventQuery.write)

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

  lazy val corrections: PublicEndpoint[
    (Option[Event.Id], Option[EventCorrection.Status], Option[UnreadPage.Offset], Option[EventList.Limit]),
    Api.Error,
    Correction.List,
    Any
  ] =
    (endpoint.get
      .in("api" / "events" / "corrections")
      .in(query[Option[String]]("status"))
      .in(query[Option[String]]("event"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .errorOut(Api.writeErrors)
      .out(jsonBody[Correction.List]))
      .mapIn[(Option[Event.Id], Option[EventCorrection.Status], Option[UnreadPage.Offset], Option[EventList.Limit])](
        (refactorDecoded4: (Option[String], Option[String], Option[Int], Option[Int])) =>
          (
            ((refactorDecoded4)._1).map(refactorElement9 => Event.Id(refactorElement9)),
            ((refactorDecoded4)._2).map(refactorElement10 => EventCorrection.Status(refactorElement10)),
            ((refactorDecoded4)._3).map(refactorElement11 => UnreadPage.Offset(refactorElement11)),
            ((refactorDecoded4)._4).map(refactorElement12 => EventList.Limit(refactorElement12))
          )
      )(
        (refactorEncoded4: (
            Option[Event.Id],
            Option[EventCorrection.Status],
            Option[UnreadPage.Offset],
            Option[EventList.Limit]
        )) =>
          (
            ((refactorEncoded4)._1).map(refactorElement13 => (refactorElement13).value),
            ((refactorEncoded4)._2).map(refactorElement14 => (refactorElement14).value),
            ((refactorEncoded4)._3).map(refactorElement15 => (refactorElement15).value),
            ((refactorEncoded4)._4).map(refactorElement16 => (refactorElement16).value)
          )
      )

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

  lazy val links: PublicEndpoint[Event.Id, Api.Error, EventLinkList, Any] =
    (endpoint.get
      .in("api" / "events" / path[String]("id") / "links")
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinkList]))
      .mapIn[Event.Id]((refactorDecoded2: String) => Event.Id(refactorDecoded2))((refactorEncoded2: Event.Id) =>
        (refactorEncoded2).value
      )

  lazy val applyLinks: PublicEndpoint[(Event.Id, EventLinksRequest), Api.Error, EventLinksResult, Any] =
    (endpoint.post
      .in("api" / "events" / path[String]("id") / "links")
      .in(jsonBody[EventLinksRequest])
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinksResult]))
      .mapIn[(Event.Id, EventLinksRequest)]((refactorDecoded3: (String, EventLinksRequest)) =>
        (Event.Id((refactorDecoded3)._1), (refactorDecoded3)._2)
      )((refactorEncoded3: (Event.Id, EventLinksRequest)) => (((refactorEncoded3)._1).value, (refactorEncoded3)._2))

  lazy val refineSiteTypes: PublicEndpoint[SiteTypes.Request, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "site-types" / "refine")
      .in(jsonBody[SiteTypes.Request])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val all: List[AnyEndpoint] =
    List(importEvents, rederive, corrections, catalogues, leads, candidatesJob, links, applyLinks, refineSiteTypes)
