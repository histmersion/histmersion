package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.Api
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given

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
      : PublicEndpoint[(Option[String], Option[String], Option[Int], Option[Int]), Api.Error, Correction.List, Any] =
    endpoint.get
      .in("api" / "events" / "corrections")
      .in(query[Option[String]]("status"))
      .in(query[Option[String]]("event"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .errorOut(Api.writeErrors)
      .out(jsonBody[Correction.List])

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

  lazy val links: PublicEndpoint[String, Api.Error, EventLinkList, Any] =
    endpoint.get
      .in("api" / "events" / path[String]("id") / "links")
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinkList])

  lazy val applyLinks: PublicEndpoint[(String, EventLinksRequest), Api.Error, EventLinksResult, Any] =
    endpoint.post
      .in("api" / "events" / path[String]("id") / "links")
      .in(jsonBody[EventLinksRequest])
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinksResult])

  lazy val refineSiteTypes: PublicEndpoint[SiteTypes.Request, Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "site-types" / "refine")
      .in(jsonBody[SiteTypes.Request])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val all: List[AnyEndpoint] =
    List(importEvents, rederive, corrections, catalogues, leads, candidatesJob, links, applyLinks, refineSiteTypes)
