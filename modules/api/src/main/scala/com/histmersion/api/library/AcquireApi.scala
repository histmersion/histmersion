package com.histmersion.api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given
import com.histmersion.api.Api

/** A DOI contains a `/`, so identifiers travel as `id=` query parameters rather than in the path. */
object AcquireApi:
  type Filter = Filter.T
  object Filter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Identifier = Identifier.T
  object Identifier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private lazy val base = endpoint.in("api" / "acquire").errorOut(jsonBody[Api.Error])

  lazy val discover
      : PublicEndpoint[(com.histmersion.api.library.AcquireApi.Query, Option[com.histmersion.api.library.AcquireApi.Limit], Option[com.histmersion.api.library.AcquireApi.Filter], Option[com.histmersion.api.catalogue.Year], Boolean), Api.Error, DiscoverResult, Any] =
    (base.get
      .in("discover")
      .in(query[String]("q"))
      .in(query[Option[Int]]("since"))
      .in(query[Option[String]]("sort"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("notInDb").default(false))
      .out(jsonBody[DiscoverResult])).mapIn[(_root_.com.histmersion.api.library.AcquireApi.Query, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Filter], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Year], Boolean)]((refactorDecoded9: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.AcquireApi.Query((refactorDecoded9)._1), ((refactorDecoded9)._2).map(refactorElement19 => _root_.com.histmersion.api.library.AcquireApi.Limit(refactorElement19)), ((refactorDecoded9)._3).map(refactorElement20 => _root_.com.histmersion.api.library.AcquireApi.Filter(refactorElement20)), ((refactorDecoded9)._4).map(refactorElement21 => _root_.com.histmersion.api.catalogue.Year(refactorElement21)), ((refactorDecoded9)._5)))((refactorEncoded9: (_root_.com.histmersion.api.library.AcquireApi.Query, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Filter], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Year], Boolean)) => (((refactorEncoded9)._1).value, ((refactorEncoded9)._2).map(refactorElement22 => (refactorElement22).value), ((refactorEncoded9)._3).map(refactorElement23 => (refactorElement23).value), ((refactorEncoded9)._4).map(refactorElement24 => (refactorElement24).value), ((refactorEncoded9)._5)))

  lazy val meta: PublicEndpoint[com.histmersion.api.library.AcquireApi.Identifier, Api.Error, WorkRef, Any] =
    (base.get.in("meta").in(query[String]("id")).out(jsonBody[WorkRef])).mapIn[_root_.com.histmersion.api.library.AcquireApi.Identifier]((refactorDecoded10: _root_.java.lang.String) => _root_.com.histmersion.api.library.AcquireApi.Identifier(refactorDecoded10))((refactorEncoded10: _root_.com.histmersion.api.library.AcquireApi.Identifier) => (refactorEncoded10).value)

  lazy val wikirefs: PublicEndpoint[com.histmersion.api.library.Title, Api.Error, WikirefsResult, Any] =
    (base.get.in("wikirefs").in(query[String]("url")).out(jsonBody[WikirefsResult])).mapIn[_root_.com.histmersion.api.library.Title]((refactorDecoded3: _root_.java.lang.String) => _root_.com.histmersion.api.library.Title(refactorDecoded3))((refactorEncoded3: _root_.com.histmersion.api.library.Title) => (refactorEncoded3).value)

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

  lazy val archiveSearch: PublicEndpoint[(com.histmersion.api.library.AcquireApi.Query, Option[com.histmersion.api.library.AcquireApi.Limit]), Api.Error, ArchiveSearchResult, Any] =
    (base.get
      .in("archive" / "search")
      .in(query[String]("q"))
      .in(query[Option[Int]]("rows"))
      .out(jsonBody[ArchiveSearchResult])).mapIn[(_root_.com.histmersion.api.library.AcquireApi.Query, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit])]((refactorDecoded12: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.library.AcquireApi.Query((refactorDecoded12)._1), ((refactorDecoded12)._2).map(refactorElement25 => _root_.com.histmersion.api.library.AcquireApi.Limit(refactorElement25))))((refactorEncoded12: (_root_.com.histmersion.api.library.AcquireApi.Query, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit])) => (((refactorEncoded12)._1).value, ((refactorEncoded12)._2).map(refactorElement26 => (refactorElement26).value)))

  lazy val archiveInfo: PublicEndpoint[com.histmersion.api.library.ArchiveId, Api.Error, ArchiveItem, Any] =
    (base.get.in("archive" / "info").in(query[String]("id")).out(jsonBody[ArchiveItem])).mapIn[_root_.com.histmersion.api.library.ArchiveId]((refactorDecoded13: _root_.java.lang.String) => _root_.com.histmersion.api.library.ArchiveId(refactorDecoded13))((refactorEncoded13: _root_.com.histmersion.api.library.ArchiveId) => (refactorEncoded13).value)

  lazy val archiveImport: PublicEndpoint[ArchiveImportRequest, Api.Error, Ingest.Report, Any] =
    base.post.in("archive" / "import").in(jsonBody[ArchiveImportRequest]).out(jsonBody[Ingest.Report])

  /** The library's Calibre gaps and push metadata, kept for a CLI that runs the push on its own machine; the server's
    * own push is the collections' (`CollectionsApi`).
    */
  lazy val calibreGaps: PublicEndpoint[Option[com.histmersion.api.library.AcquireApi.Limit], Api.Error, CalibreGaps, Any] =
    (base.get.in("calibre" / "gaps").in(query[Option[Int]]("limit")).out(jsonBody[CalibreGaps])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit]]((refactorDecoded6: _root_.scala.Option[_root_.scala.Int]) => (refactorDecoded6).map(refactorElement9 => _root_.com.histmersion.api.library.AcquireApi.Limit(refactorElement9)))((refactorEncoded6: _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit]) => (refactorEncoded6).map(refactorElement10 => (refactorElement10).value))

  lazy val calibreMeta: PublicEndpoint[com.histmersion.api.library.Citekey, Api.Error, CalibreGap, Any] =
    (base.get.in("calibre" / "meta").in(query[String]("citekey")).out(jsonBody[CalibreGap])).mapIn[_root_.com.histmersion.api.library.Citekey]((refactorDecoded7: _root_.java.lang.String) => _root_.com.histmersion.api.library.Citekey(refactorDecoded7))((refactorEncoded7: _root_.com.histmersion.api.library.Citekey) => (refactorEncoded7).value)

  lazy val calibrePushed: PublicEndpoint[CalibrePushRequest, Api.Error, CalibrePushResult, Any] =
    base.post.in("calibre" / "pushed").in(jsonBody[CalibrePushRequest]).out(jsonBody[CalibrePushResult])

  lazy val candidates
      : PublicEndpoint[(Option[com.histmersion.api.library.AuthorName], Option[com.histmersion.api.library.Title], Option[com.histmersion.api.catalogue.Year], Option[com.histmersion.api.library.AcquireApi.Limit]), Api.Error, CandidateList, Any] =
    (base.get
      .in("candidates")
      .in(query[Option[String]]("goal"))
      .in(query[Option[String]]("state"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .out(jsonBody[CandidateList])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.library.AuthorName], _root_.scala.Option[_root_.com.histmersion.api.library.Title], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Year], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit])]((refactorDecoded16: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded16)._1).map(refactorElement29 => _root_.com.histmersion.api.library.AuthorName(refactorElement29)), ((refactorDecoded16)._2).map(refactorElement30 => _root_.com.histmersion.api.library.Title(refactorElement30)), ((refactorDecoded16)._3).map(refactorElement31 => _root_.com.histmersion.api.catalogue.Year(refactorElement31)), ((refactorDecoded16)._4).map(refactorElement32 => _root_.com.histmersion.api.library.AcquireApi.Limit(refactorElement32))))((refactorEncoded16: (_root_.scala.Option[_root_.com.histmersion.api.library.AuthorName], _root_.scala.Option[_root_.com.histmersion.api.library.Title], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Year], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.Limit])) => (((refactorEncoded16)._1).map(refactorElement33 => (refactorElement33).value), ((refactorEncoded16)._2).map(refactorElement34 => (refactorElement34).value), ((refactorEncoded16)._3).map(refactorElement35 => (refactorElement35).value), ((refactorEncoded16)._4).map(refactorElement36 => (refactorElement36).value)))

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
