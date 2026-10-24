package com.histmersion.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The catalogue side of events (#1449): imports, proposed links, leads, the catalogues' terms and
  * the refinement of site types. Definitions only, appended to [[Api.all]] BEFORE the event routes
  * (`events/leads` must not be read as the event `leads`). */
object EventsApi:

  /** The shared filter plus an event's own narrowing, as `GET /api/events` takes them. */
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

  /** `POST /api/events/import` — read a catalogue, as a JOB (`histmersion jobs` follows it). A dry run
    * stores nothing and leaves its report on the job; a real one merges what it reads with the events
    * there, part by part, so a stopped import resumes after the last part. */
  lazy val importEvents: PublicEndpoint[EventImportRequest, ApiError, JobInfo, Any] =
    endpoint.post.in("api" / "events" / "import").in(jsonBody[EventImportRequest])
      .errorOut(Api.writeErrors).out(statusCode(StatusCode.Accepted)).out(jsonBody[JobInfo])

  /** `POST /api/events/rederive` — compute the stored catalogue records' dating again from the records
    * as read (no network) and refresh the events the catalogues created, as a JOB. */
  lazy val rederive: PublicEndpoint[EventRederiveRequest, ApiError, JobInfo, Any] =
    endpoint.post.in("api" / "events" / "rederive").in(jsonBody[EventRederiveRequest])
      .errorOut(Api.writeErrors).out(statusCode(StatusCode.Accepted)).out(jsonBody[JobInfo])

  /** `GET /api/events/corrections` — the fields of catalogue events a person corrected by hand
    * ([[Corrections]]): `status` active | agreed | conflict (default: all but the retired `agreed`),
    * `event` one event's. */
  lazy val corrections: PublicEndpoint[(Option[String], Option[String], Option[Int], Option[Int]), ApiError, CorrectionList, Any] =
    endpoint.get.in("api" / "events" / "corrections")
      .in(query[Option[String]]("status")).in(query[Option[String]]("event"))
      .in(query[Option[Int]]("limit")).in(query[Option[Int]]("offset"))
      .errorOut(Api.writeErrors).out(jsonBody[CorrectionList])

  /** `GET /api/events/catalogues` — the catalogues, their licences and the citations they ask for. */
  lazy val catalogues: PublicEndpoint[Unit, ApiError, List[CatalogProviderInfo], Any] =
    endpoint.get.in("api" / "events" / "catalogues").errorOut(jsonBody[ApiError]).out(jsonBody[List[CatalogProviderInfo]])

  /** `GET /api/events/leads` — the questions the selected events raise (catalogue events no source
    * discusses, with the corpus's findings around them), the largest first. */
  lazy val leads: PublicEndpoint[Api.EventQuery, ApiError, EventLeadList, Any] =
    endpoint.get.in("api" / "events" / "leads").in(eventQuery).errorOut(jsonBody[ApiError]).out(jsonBody[EventLeadList])

  /** `POST /api/events/candidates` — compute the proposed links of many events, as a job. */
  lazy val candidatesJob: PublicEndpoint[EventCandidatesRequest, ApiError, JobInfo, Any] =
    endpoint.post.in("api" / "events" / "candidates").in(jsonBody[EventCandidatesRequest])
      .errorOut(Api.writeErrors).out(statusCode(StatusCode.Accepted)).out(jsonBody[JobInfo])

  /** `GET /api/events/{id}/links` — the sources that possibly discuss an event (computed on first ask). */
  lazy val links: PublicEndpoint[String, ApiError, EventLinkList, Any] =
    endpoint.get.in("api" / "events" / path[String]("id") / "links").errorOut(Api.writeErrors).out(jsonBody[EventLinkList])

  /** `POST /api/events/{id}/links` — recompute, confirm (an attestation from a passage that names it)
    * or reject proposed sources. */
  lazy val applyLinks: PublicEndpoint[(String, EventLinksRequest), ApiError, EventLinksResult, Any] =
    endpoint.post.in("api" / "events" / path[String]("id") / "links").in(jsonBody[EventLinksRequest])
      .errorOut(Api.writeErrors).out(jsonBody[EventLinksResult])

  /** `POST /api/site-types/refine` — refine the type of the Wikidata sites on the map from their items'
    * classes, as a job (`dryRun` counts what it would ask about). */
  lazy val refineSiteTypes: PublicEndpoint[SiteTypesRequest, ApiError, JobInfo, Any] =
    endpoint.post.in("api" / "site-types" / "refine").in(jsonBody[SiteTypesRequest])
      .errorOut(Api.writeErrors).out(statusCode(StatusCode.Accepted)).out(jsonBody[JobInfo])

  lazy val all: List[AnyEndpoint] = List(importEvents, rederive, corrections, catalogues, leads, candidatesJob, links, applyLinks,
    refineSiteTypes)
