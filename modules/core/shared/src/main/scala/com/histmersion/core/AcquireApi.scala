package com.histmersion.core

import sttp.tapir.*
import sttp.tapir.json.circe.*

/** The REST contract of the acquisition & assessment toolset (plan §5.2 stream 2.6), as Tapir
  * endpoint DEFINITIONS. Interpreted by the server and turned into an sttp client by the CLI from the
  * very same values, exactly like [[Api]] — these live in their own object only so the two streams
  * touching `Api.scala` do not fight over one list.
  *
  * Reads are `GET` with query parameters (a DOI contains a `/`, so identifiers travel as `id=` rather
  * than in the path). Everything that can WRITE — reference edges, an assessed `status`, a new
  * source, the Retraction Watch mirror — is a `POST` with a JSON body, even when `apply=false`, so a
  * caller can never trigger a write by following a link.
  */
object AcquireApi:

  private lazy val base = endpoint.in("api" / "acquire").errorOut(jsonBody[ApiError])

  /** `GET /api/acquire/discover?q=…` — OpenAlex search (`sort` = relevance|cited). `notInDb` drops
    * works already stored, which is what `update-topic` wants. */
  lazy val discover: PublicEndpoint[(String, Option[Int], Option[String], Option[Int], Boolean), ApiError, DiscoverResult, Any] =
    base.get.in("discover")
      .in(query[String]("q"))
      .in(query[Option[Int]]("since"))
      .in(query[Option[String]]("sort"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("notInDb").default(false))
      .out(jsonBody[DiscoverResult])

  /** `GET /api/acquire/meta?id=<doi>` — normalised metadata for one DOI (OpenAlex, Crossref fallback). */
  lazy val meta: PublicEndpoint[String, ApiError, WorkRef, Any] =
    base.get.in("meta").in(query[String]("id")).out(jsonBody[WorkRef])

  /** `GET /api/acquire/wikirefs?url=…` — the DOIs a Wikipedia article cites (the trailhead). */
  lazy val wikirefs: PublicEndpoint[String, ApiError, WikirefsResult, Any] =
    base.get.in("wikirefs").in(query[String]("url")).out(jsonBody[WikirefsResult])

  /** `POST /api/acquire/refs` — the deposited reference list; `apply` writes `references` edges to the
    * sources already stored (matched by DOI, then by citekey prefix, as `resolveEdges` does). */
  lazy val refs: PublicEndpoint[RefsRequest, ApiError, RefsResult, Any] =
    base.post.in("refs").in(jsonBody[RefsRequest]).out(jsonBody[RefsResult])

  /** `POST /api/acquire/assess` — Retraction Watch + Crossref notices + citation metrics; `apply`
    * patches the stored source's `status`. */
  lazy val assess: PublicEndpoint[AssessRequest, ApiError, Assessment, Any] =
    base.post.in("assess").in(jsonBody[AssessRequest]).out(jsonBody[Assessment])

  /** `POST /api/acquire/retractions` — START a refresh of the Retraction Watch mirror. Returns at
    * once; the download and the ~60 000-row rewrite continue in the background (see
    * [[RetractionsUpdate]]). */
  lazy val updateRetractions: PublicEndpoint[Unit, ApiError, RetractionsUpdate, Any] =
    base.post.in("retractions").out(jsonBody[RetractionsUpdate])

  /** `GET /api/acquire/retractions` — how the mirror is doing; poll until `running` is false. */
  lazy val retractionsStatus: PublicEndpoint[Unit, ApiError, RetractionsUpdate, Any] =
    base.get.in("retractions").out(jsonBody[RetractionsUpdate])

  /** `POST /api/acquire/followups` — papers citing this one, with intents and a proposed edge kind. */
  lazy val followups: PublicEndpoint[FollowupsRequest, ApiError, FollowupsResult, Any] =
    base.post.in("followups").in(jsonBody[FollowupsRequest]).out(jsonBody[FollowupsResult])

  /** `POST /api/acquire/ingest` — metadata → legal OA PDF → ingest job → reference edges → assessment. */
  lazy val ingest: PublicEndpoint[IngestRequest, ApiError, IngestReport, Any] =
    base.post.in("ingest").in(jsonBody[IngestRequest]).out(jsonBody[IngestReport])

  lazy val archiveSearch: PublicEndpoint[(String, Option[Int]), ApiError, ArchiveSearchResult, Any] =
    base.get.in("archive" / "search").in(query[String]("q")).in(query[Option[Int]]("rows"))
      .out(jsonBody[ArchiveSearchResult])

  lazy val archiveInfo: PublicEndpoint[String, ApiError, ArchiveItem, Any] =
    base.get.in("archive" / "info").in(query[String]("id")).out(jsonBody[ArchiveItem])

  /** `POST /api/acquire/archive/import` — public files only; a lending-only item is refused. */
  lazy val archiveImport: PublicEndpoint[ArchiveImportRequest, ApiError, IngestReport, Any] =
    base.post.in("archive" / "import").in(jsonBody[ArchiveImportRequest]).out(jsonBody[IngestReport])

  /** `GET /api/acquire/calibre/gaps` — stored sources that are not in Calibre yet. The PUSH itself is
    * host-side (`histmersion calibre sync`): Calibre lives on the user's machine, not in the container. */
  lazy val calibreGaps: PublicEndpoint[Option[Int], ApiError, CalibreGaps, Any] =
    base.get.in("calibre" / "gaps").in(query[Option[Int]]("limit")).out(jsonBody[CalibreGaps])

  /** `GET /api/acquire/calibre/meta?citekey=…` — the metadata a host-side `calibre push` sends into
    * Calibre, with the authors as a proper list. */
  lazy val calibreMeta: PublicEndpoint[String, ApiError, CalibreGap, Any] =
    base.get.in("calibre" / "meta").in(query[String]("citekey")).out(jsonBody[CalibreGap])

  /** `POST /api/acquire/calibre/pushed` — record the Calibre id a host-side push created. */
  lazy val calibrePushed: PublicEndpoint[CalibrePushRequest, ApiError, CalibrePushResult, Any] =
    base.post.in("calibre" / "pushed").in(jsonBody[CalibrePushRequest]).out(jsonBody[CalibrePushResult])

  // ── deterministic new-source search (#3761) ──────────────────────────────────────────────────

  /** `GET /api/acquire/candidates?goal=&state=&limit=&offset=` — the works found for a goal (or every
    * goal), by state (`new` by default: the unseen ones), with the counts per state. */
  lazy val candidates: PublicEndpoint[(Option[String], Option[String], Option[Int], Option[Int]), ApiError, CandidateList, Any] =
    base.get.in("candidates")
      .in(query[Option[String]]("goal"))
      .in(query[Option[String]]("state"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .out(jsonBody[CandidateList])

  /** `POST /api/acquire/candidates/sweep` — read every watch of a goal from its watermark (no model):
    * new candidates are stored, the watermarks moved (a dry run stores neither). */
  lazy val sweepCandidates: PublicEndpoint[CandidateSweepRequest, ApiError, CandidateSweep, Any] =
    base.post.in("candidates" / "sweep").in(jsonBody[CandidateSweepRequest]).out(jsonBody[CandidateSweep])

  /** `POST /api/acquire/candidates/mark` — set candidates' state (seen, queued, dismissed, new). */
  lazy val markCandidates: PublicEndpoint[CandidateMark, ApiError, CandidateMarked, Any] =
    base.post.in("candidates" / "mark").in(jsonBody[CandidateMark]).out(jsonBody[CandidateMarked])

  lazy val all = List(discover, meta, wikirefs, refs, assess, updateRetractions, followups, ingest,
    archiveSearch, archiveInfo, archiveImport, calibreGaps, calibrePushed, retractionsStatus, calibreMeta,
    candidates, sweepCandidates, markCandidates)
