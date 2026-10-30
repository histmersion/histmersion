package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*

/** A DOI contains a `/`, so identifiers travel as `id=` query parameters rather than in the path. */
object AcquireApi:

  private lazy val base = endpoint.in("api" / "acquire").errorOut(jsonBody[ApiError])

  lazy val discover
      : PublicEndpoint[(String, Option[Int], Option[String], Option[Int], Boolean), ApiError, DiscoverResult, Any] =
    base.get
      .in("discover")
      .in(query[String]("q"))
      .in(query[Option[Int]]("since"))
      .in(query[Option[String]]("sort"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("notInDb").default(false))
      .out(jsonBody[DiscoverResult])

  lazy val meta: PublicEndpoint[String, ApiError, WorkRef, Any] =
    base.get.in("meta").in(query[String]("id")).out(jsonBody[WorkRef])

  lazy val wikirefs: PublicEndpoint[String, ApiError, WikirefsResult, Any] =
    base.get.in("wikirefs").in(query[String]("url")).out(jsonBody[WikirefsResult])

  lazy val refs: PublicEndpoint[RefsRequest, ApiError, RefsResult, Any] =
    base.post.in("refs").in(jsonBody[RefsRequest]).out(jsonBody[RefsResult])

  lazy val assess: PublicEndpoint[AssessRequest, ApiError, Assessment, Any] =
    base.post.in("assess").in(jsonBody[AssessRequest]).out(jsonBody[Assessment])

  lazy val updateRetractions: PublicEndpoint[Unit, ApiError, RetractionsUpdate, Any] =
    base.post.in("retractions").out(jsonBody[RetractionsUpdate])

  lazy val retractionsStatus: PublicEndpoint[Unit, ApiError, RetractionsUpdate, Any] =
    base.get.in("retractions").out(jsonBody[RetractionsUpdate])

  lazy val followups: PublicEndpoint[FollowupsRequest, ApiError, FollowupsResult, Any] =
    base.post.in("followups").in(jsonBody[FollowupsRequest]).out(jsonBody[FollowupsResult])

  lazy val ingest: PublicEndpoint[IngestRequest, ApiError, IngestReport, Any] =
    base.post.in("ingest").in(jsonBody[IngestRequest]).out(jsonBody[IngestReport])

  lazy val archiveSearch: PublicEndpoint[(String, Option[Int]), ApiError, ArchiveSearchResult, Any] =
    base.get
      .in("archive" / "search")
      .in(query[String]("q"))
      .in(query[Option[Int]]("rows"))
      .out(jsonBody[ArchiveSearchResult])

  lazy val archiveInfo: PublicEndpoint[String, ApiError, ArchiveItem, Any] =
    base.get.in("archive" / "info").in(query[String]("id")).out(jsonBody[ArchiveItem])

  lazy val archiveImport: PublicEndpoint[ArchiveImportRequest, ApiError, IngestReport, Any] =
    base.post.in("archive" / "import").in(jsonBody[ArchiveImportRequest]).out(jsonBody[IngestReport])

  /** The push itself is host-side: Calibre lives on the user's machine, out of the server's reach. */
  lazy val calibreGaps: PublicEndpoint[Option[Int], ApiError, CalibreGaps, Any] =
    base.get.in("calibre" / "gaps").in(query[Option[Int]]("limit")).out(jsonBody[CalibreGaps])

  lazy val calibreMeta: PublicEndpoint[String, ApiError, CalibreGap, Any] =
    base.get.in("calibre" / "meta").in(query[String]("citekey")).out(jsonBody[CalibreGap])

  lazy val calibrePushed: PublicEndpoint[CalibrePushRequest, ApiError, CalibrePushResult, Any] =
    base.post.in("calibre" / "pushed").in(jsonBody[CalibrePushRequest]).out(jsonBody[CalibrePushResult])

  lazy val candidates
      : PublicEndpoint[(Option[String], Option[String], Option[Int], Option[Int]), ApiError, CandidateList, Any] =
    base.get
      .in("candidates")
      .in(query[Option[String]]("goal"))
      .in(query[Option[String]]("state"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .out(jsonBody[CandidateList])

  lazy val sweepCandidates: PublicEndpoint[CandidateSweepRequest, ApiError, CandidateSweep, Any] =
    base.post.in("candidates" / "sweep").in(jsonBody[CandidateSweepRequest]).out(jsonBody[CandidateSweep])

  lazy val markCandidates: PublicEndpoint[CandidateMark, ApiError, CandidateMarked, Any] =
    base.post.in("candidates" / "mark").in(jsonBody[CandidateMark]).out(jsonBody[CandidateMarked])

  lazy val all = List(
    discover,
    meta,
    wikirefs,
    refs,
    assess,
    updateRetractions,
    followups,
    ingest,
    archiveSearch,
    archiveInfo,
    archiveImport,
    calibreGaps,
    calibrePushed,
    retractionsStatus,
    calibreMeta,
    candidates,
    sweepCandidates,
    markCandidates
  )
