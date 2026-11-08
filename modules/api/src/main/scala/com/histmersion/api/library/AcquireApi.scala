package com.histmersion
package api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Goal

/** A DOI contains a `/`, so identifiers travel as `id=` query parameters rather than in the path. */
object AcquireApi:
  type Filter = Filter.T
  object Filter extends Newtype[String, Pure]
  type Identifier = Identifier.T
  object Identifier extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]


  private lazy val base = endpoint.in("api" / "acquire").errorOut(jsonBody[Api.Error])

  lazy val discover
      : PublicEndpoint[(Query, Option[Year], Option[Filter], Option[Limit], Boolean), Api.Error, DiscoverResult, Any] =
    (base.get
      .in("discover")
      .in(query[String]("q"))
      .in(query[Option[Int]]("since"))
      .in(query[Option[String]]("sort"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("notInDb").default(false))
      .out(jsonBody[DiscoverResult])).mapIn[(Query, Option[Year], Option[Filter], Option[Limit], Boolean)]((refactorDecoded9: (String, Option[Int], Option[String], Option[Int], Boolean)) => (Query((refactorDecoded9)._1), ((refactorDecoded9)._2).map(refactorElement19 => Year(refactorElement19.toLong)), ((refactorDecoded9)._3).map(refactorElement20 => Filter(refactorElement20)), ((refactorDecoded9)._4).map(refactorElement21 => Limit(refactorElement21)), ((refactorDecoded9)._5)))((refactorEncoded9: (Query, Option[Year], Option[Filter], Option[Limit], Boolean)) => (((refactorEncoded9)._1).value, ((refactorEncoded9)._2).map(refactorElement22 => (refactorElement22).value.toInt), ((refactorEncoded9)._3).map(refactorElement23 => (refactorElement23).value), ((refactorEncoded9)._4).map(refactorElement24 => (refactorElement24).value), ((refactorEncoded9)._5)))

  lazy val meta: PublicEndpoint[Identifier, Api.Error, WorkRef, Any] =
    (base.get.in("meta").in(query[String]("id")).out(jsonBody[WorkRef])).mapIn[Identifier]((refactorDecoded10: String) => Identifier(refactorDecoded10))((refactorEncoded10: Identifier) => (refactorEncoded10).value)

  lazy val wikirefs: PublicEndpoint[Title, Api.Error, WikirefsResult, Any] =
    (base.get.in("wikirefs").in(query[String]("url")).out(jsonBody[WikirefsResult])).mapIn[Title]((refactorDecoded3: String) => Title(refactorDecoded3))((refactorEncoded3: Title) => (refactorEncoded3).value)

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

  lazy val archiveSearch: PublicEndpoint[(Query, Option[Limit]), Api.Error, ArchiveSearchResult, Any] =
    (base.get
      .in("archive" / "search")
      .in(query[String]("q"))
      .in(query[Option[Int]]("rows"))
      .out(jsonBody[ArchiveSearchResult])).mapIn[(Query, Option[Limit])]((refactorDecoded12: (String, Option[Int])) => (Query((refactorDecoded12)._1), ((refactorDecoded12)._2).map(refactorElement25 => Limit(refactorElement25))))((refactorEncoded12: (Query, Option[Limit])) => (((refactorEncoded12)._1).value, ((refactorEncoded12)._2).map(refactorElement26 => (refactorElement26).value)))

  lazy val archiveInfo: PublicEndpoint[ArchiveId, Api.Error, ArchiveItem, Any] =
    (base.get.in("archive" / "info").in(query[String]("id")).out(jsonBody[ArchiveItem])).mapIn[ArchiveId]((refactorDecoded13: String) => ArchiveId(refactorDecoded13))((refactorEncoded13: ArchiveId) => (refactorEncoded13).value)

  lazy val archiveImport: PublicEndpoint[ArchiveImportRequest, Api.Error, Ingest.Report, Any] =
    base.post.in("archive" / "import").in(jsonBody[ArchiveImportRequest]).out(jsonBody[Ingest.Report])

  /** The library's Calibre gaps and push metadata, kept for a CLI that runs the push on its own machine; the server's
    * own push is the collections' (`CollectionsApi`).
    */
  lazy val calibreGaps: PublicEndpoint[Option[Limit], Api.Error, CalibreGaps, Any] =
    (base.get.in("calibre" / "gaps").in(query[Option[Int]]("limit")).out(jsonBody[CalibreGaps])).mapIn[Option[Limit]]((refactorDecoded6: Option[Int]) => (refactorDecoded6).map(refactorElement9 => Limit(refactorElement9)))((refactorEncoded6: Option[Limit]) => (refactorEncoded6).map(refactorElement10 => (refactorElement10).value))

  lazy val calibreMeta: PublicEndpoint[Citekey, Api.Error, CalibreGap, Any] =
    (base.get.in("calibre" / "meta").in(query[String]("citekey")).out(jsonBody[CalibreGap])).mapIn[Citekey]((refactorDecoded7: String) => Citekey(refactorDecoded7))((refactorEncoded7: Citekey) => (refactorEncoded7).value)

  lazy val calibrePushed: PublicEndpoint[CalibrePushRequest, Api.Error, CalibrePushResult, Any] =
    base.post.in("calibre" / "pushed").in(jsonBody[CalibrePushRequest]).out(jsonBody[CalibrePushResult])

  lazy val candidates
      : PublicEndpoint[(Option[Goal.Slug], Option[Candidates.State.Wire], Option[Limit], Option[CandidateList.Offset]), Api.Error, CandidateList, Any] =
    (base.get
      .in("candidates")
      .in(query[Option[String]]("goal"))
      .in(query[Option[String]]("state"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .out(jsonBody[CandidateList])).mapIn[(Option[Goal.Slug], Option[Candidates.State.Wire], Option[Limit], Option[CandidateList.Offset])]((refactorDecoded16: (Option[String], Option[String], Option[Int], Option[Int])) => (((refactorDecoded16)._1).map(refactorElement29 => Goal.Slug(refactorElement29)), ((refactorDecoded16)._2).map(refactorElement30 => Candidates.State.Wire(refactorElement30)), ((refactorDecoded16)._3).map(refactorElement31 => Limit(refactorElement31)), ((refactorDecoded16)._4).map(refactorElement32 => CandidateList.Offset(refactorElement32))))((refactorEncoded16: (Option[Goal.Slug], Option[Candidates.State.Wire], Option[Limit], Option[CandidateList.Offset])) => (((refactorEncoded16)._1).map(refactorElement33 => (refactorElement33).value), ((refactorEncoded16)._2).map(refactorElement34 => (refactorElement34).value), ((refactorEncoded16)._3).map(refactorElement35 => (refactorElement35).value), ((refactorEncoded16)._4).map(refactorElement36 => (refactorElement36).value)))

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
