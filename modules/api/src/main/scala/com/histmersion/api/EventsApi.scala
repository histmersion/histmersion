package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

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

  lazy val importEvents: PublicEndpoint[EventImportRequest, ApiError, JobInfo, Any] =
    endpoint.post
      .in("api" / "events" / "import")
      .in(jsonBody[EventImportRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val rederive: PublicEndpoint[EventRederiveRequest, ApiError, JobInfo, Any] =
    endpoint.post
      .in("api" / "events" / "rederive")
      .in(jsonBody[EventRederiveRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val corrections
      : PublicEndpoint[(Option[String], Option[String], Option[Int], Option[Int]), ApiError, CorrectionList, Any] =
    endpoint.get
      .in("api" / "events" / "corrections")
      .in(query[Option[String]]("status"))
      .in(query[Option[String]]("event"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .errorOut(Api.writeErrors)
      .out(jsonBody[CorrectionList])

  lazy val catalogues: PublicEndpoint[Unit, ApiError, List[CatalogProviderInfo], Any] =
    endpoint.get
      .in("api" / "events" / "catalogues")
      .errorOut(jsonBody[ApiError])
      .out(jsonBody[List[CatalogProviderInfo]])

  lazy val leads: PublicEndpoint[Api.EventQuery, ApiError, EventLeadList, Any] =
    endpoint.get.in("api" / "events" / "leads").in(eventQuery).errorOut(jsonBody[ApiError]).out(jsonBody[EventLeadList])

  lazy val candidatesJob: PublicEndpoint[EventCandidatesRequest, ApiError, JobInfo, Any] =
    endpoint.post
      .in("api" / "events" / "candidates")
      .in(jsonBody[EventCandidatesRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val links: PublicEndpoint[String, ApiError, EventLinkList, Any] =
    endpoint.get
      .in("api" / "events" / path[String]("id") / "links")
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinkList])

  lazy val applyLinks: PublicEndpoint[(String, EventLinksRequest), ApiError, EventLinksResult, Any] =
    endpoint.post
      .in("api" / "events" / path[String]("id") / "links")
      .in(jsonBody[EventLinksRequest])
      .errorOut(Api.writeErrors)
      .out(jsonBody[EventLinksResult])

  lazy val refineSiteTypes: PublicEndpoint[SiteTypesRequest, ApiError, JobInfo, Any] =
    endpoint.post
      .in("api" / "site-types" / "refine")
      .in(jsonBody[SiteTypesRequest])
      .errorOut(Api.writeErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val all: List[AnyEndpoint] =
    List(importEvents, rederive, corrections, catalogues, leads, candidatesJob, links, applyLinks, refineSiteTypes)
