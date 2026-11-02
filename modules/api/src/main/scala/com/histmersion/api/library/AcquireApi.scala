package com.histmersion.api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given
import com.histmersion.api.Api

/** A DOI contains a `/`, so identifiers travel as `id=` query parameters rather than in the path. */
object AcquireApi:

  private lazy val base = endpoint.in("api" / "acquire").errorOut(jsonBody[Api.Error])

  lazy val discover
      : PublicEndpoint[(String, Option[Int], Option[String], Option[Int], Boolean), Api.Error, DiscoverResult, Any] =
    base.get
      .in("discover")
      .in(query[String]("q"))
      .in(query[Option[Int]]("since"))
      .in(query[Option[String]]("sort"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("notInDb").default(false))
      .out(jsonBody[DiscoverResult])

  lazy val meta: PublicEndpoint[String, Api.Error, WorkRef, Any] =
    base.get.in("meta").in(query[String]("id")).out(jsonBody[WorkRef])

  lazy val wikirefs: PublicEndpoint[String, Api.Error, WikirefsResult, Any] =
    base.get.in("wikirefs").in(query[String]("url")).out(jsonBody[WikirefsResult])

  lazy val refs: PublicEndpoint[RefsRequest, Api.Error, RefsResult, Any] =
    base.post.in("refs").in(jsonBody[RefsRequest]).out(jsonBody[RefsResult])

  lazy val assess: PublicEndpoint[AssessRequest, Api.Error, Assessment, Any] =
    base.post.in("assess").in(jsonBody[AssessRequest]).out(jsonBody[Assessment])

  lazy val updateRetractions: PublicEndpoint[Unit, Api.Error, RetractionsUpdate, Any] =
    base.post.in("retractions").out(jsonBody[RetractionsUpdate])

  lazy val retractionsStatus: PublicEndpoint[Unit, Api.Error, RetractionsUpdate, Any] =
    base.get.in("retractions").out(jsonBody[RetractionsUpdate])

  lazy val followups: PublicEndpoint[FollowupsRequest, Api.Error, FollowupsResult, Any] =
    base.post.in("followups").in(jsonBody[FollowupsRequest]).out(jsonBody[FollowupsResult])

  lazy val ingest: PublicEndpoint[Ingest.Request, Api.Error, Ingest.Report, Any] =
    base.post.in("ingest").in(jsonBody[Ingest.Request]).out(jsonBody[Ingest.Report])

  lazy val archiveSearch: PublicEndpoint[(String, Option[Int]), Api.Error, ArchiveSearchResult, Any] =
    base.get
      .in("archive" / "search")
      .in(query[String]("q"))
      .in(query[Option[Int]]("rows"))
      .out(jsonBody[ArchiveSearchResult])

  lazy val archiveInfo: PublicEndpoint[String, Api.Error, ArchiveItem, Any] =
    base.get.in("archive" / "info").in(query[String]("id")).out(jsonBody[ArchiveItem])

  lazy val archiveImport: PublicEndpoint[ArchiveImportRequest, Api.Error, Ingest.Report, Any] =
    base.post.in("archive" / "import").in(jsonBody[ArchiveImportRequest]).out(jsonBody[Ingest.Report])

  /** The library's Calibre gaps and push metadata, kept for a CLI that runs the push on its own machine; the server's
    * own push is the collections' (`CollectionsApi`).
    */
  lazy val calibreGaps: PublicEndpoint[Option[Int], Api.Error, CalibreGaps, Any] =
    base.get.in("calibre" / "gaps").in(query[Option[Int]]("limit")).out(jsonBody[CalibreGaps])

  lazy val calibreMeta: PublicEndpoint[String, Api.Error, CalibreGap, Any] =
    base.get.in("calibre" / "meta").in(query[String]("citekey")).out(jsonBody[CalibreGap])

  lazy val calibrePushed: PublicEndpoint[CalibrePushRequest, Api.Error, CalibrePushResult, Any] =
    base.post.in("calibre" / "pushed").in(jsonBody[CalibrePushRequest]).out(jsonBody[CalibrePushResult])

  lazy val candidates
      : PublicEndpoint[(Option[String], Option[String], Option[Int], Option[Int]), Api.Error, CandidateList, Any] =
    base.get
      .in("candidates")
      .in(query[Option[String]]("goal"))
      .in(query[Option[String]]("state"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .out(jsonBody[CandidateList])

  lazy val sweepCandidates: PublicEndpoint[CandidateSweep.Request, Api.Error, CandidateSweep, Any] =
    base.post.in("candidates" / "sweep").in(jsonBody[CandidateSweep.Request]).out(jsonBody[CandidateSweep])

  lazy val markCandidates: PublicEndpoint[CandidateMark, Api.Error, CandidateMarked, Any] =
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
