package com.histmersion.api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given
import com.histmersion.api.Api

/** A DOI contains a `/`, so identifiers travel as `id=` query parameters rather than in the path. */
object AcquireApi:

  type DiscoverPart1Part1 = DiscoverPart1Part1.T
  object DiscoverPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverPart1Part2 = DiscoverPart1Part2.T
  object DiscoverPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverPart1Part3 = DiscoverPart1Part3.T
  object DiscoverPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverPart1Part4 = DiscoverPart1Part4.T
  object DiscoverPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverPart1Part5 = DiscoverPart1Part5.T
  object DiscoverPart1Part5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaPart1 = MetaPart1.T
  object MetaPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikirefsPart1 = WikirefsPart1.T
  object WikirefsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchPart1Part1 = ArchiveSearchPart1Part1.T
  object ArchiveSearchPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchPart1Part2 = ArchiveSearchPart1Part2.T
  object ArchiveSearchPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveInfoPart1 = ArchiveInfoPart1.T
  object ArchiveInfoPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreGapsPart1 = CalibreGapsPart1.T
  object CalibreGapsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreMetaPart1 = CalibreMetaPart1.T
  object CalibreMetaPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesPart1Part1 = CandidatesPart1Part1.T
  object CandidatesPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesPart1Part2 = CandidatesPart1Part2.T
  object CandidatesPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesPart1Part3 = CandidatesPart1Part3.T
  object CandidatesPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesPart1Part4 = CandidatesPart1Part4.T
  object CandidatesPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private lazy val base = endpoint.in("api" / "acquire").errorOut(jsonBody[Api.Error])

  lazy val discover
      : PublicEndpoint[(com.histmersion.api.library.AcquireApi.DiscoverPart1Part1, Option[com.histmersion.api.library.AcquireApi.DiscoverPart1Part2], Option[com.histmersion.api.library.AcquireApi.DiscoverPart1Part3], Option[com.histmersion.api.library.AcquireApi.DiscoverPart1Part4], com.histmersion.api.library.AcquireApi.DiscoverPart1Part5), Api.Error, DiscoverResult, Any] =
    (base.get
      .in("discover")
      .in(query[String]("q"))
      .in(query[Option[Int]]("since"))
      .in(query[Option[String]]("sort"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("notInDb").default(false))
      .out(jsonBody[DiscoverResult])).mapIn[(_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part4], _root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part5)]((refactorDecoded9: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part1((refactorDecoded9)._1), ((refactorDecoded9)._2).map(refactorElement19 => _root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part2(refactorElement19)), ((refactorDecoded9)._3).map(refactorElement20 => _root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part3(refactorElement20)), ((refactorDecoded9)._4).map(refactorElement21 => _root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part4(refactorElement21)), _root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part5((refactorDecoded9)._5)))((refactorEncoded9: (_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part4], _root_.com.histmersion.api.library.AcquireApi.DiscoverPart1Part5)) => (((refactorEncoded9)._1).value, ((refactorEncoded9)._2).map(refactorElement22 => (refactorElement22).value), ((refactorEncoded9)._3).map(refactorElement23 => (refactorElement23).value), ((refactorEncoded9)._4).map(refactorElement24 => (refactorElement24).value), ((refactorEncoded9)._5).value))

  lazy val meta: PublicEndpoint[com.histmersion.api.library.AcquireApi.MetaPart1, Api.Error, WorkRef, Any] =
    (base.get.in("meta").in(query[String]("id")).out(jsonBody[WorkRef])).mapIn[_root_.com.histmersion.api.library.AcquireApi.MetaPart1]((refactorDecoded10: _root_.java.lang.String) => _root_.com.histmersion.api.library.AcquireApi.MetaPart1(refactorDecoded10))((refactorEncoded10: _root_.com.histmersion.api.library.AcquireApi.MetaPart1) => (refactorEncoded10).value)

  lazy val wikirefs: PublicEndpoint[com.histmersion.api.library.AcquireApi.WikirefsPart1, Api.Error, WikirefsResult, Any] =
    (base.get.in("wikirefs").in(query[String]("url")).out(jsonBody[WikirefsResult])).mapIn[_root_.com.histmersion.api.library.AcquireApi.WikirefsPart1]((refactorDecoded3: _root_.java.lang.String) => _root_.com.histmersion.api.library.AcquireApi.WikirefsPart1(refactorDecoded3))((refactorEncoded3: _root_.com.histmersion.api.library.AcquireApi.WikirefsPart1) => (refactorEncoded3).value)

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

  lazy val archiveSearch: PublicEndpoint[(com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part1, Option[com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part2]), Api.Error, ArchiveSearchResult, Any] =
    (base.get
      .in("archive" / "search")
      .in(query[String]("q"))
      .in(query[Option[Int]]("rows"))
      .out(jsonBody[ArchiveSearchResult])).mapIn[(_root_.com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part2])]((refactorDecoded12: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part1((refactorDecoded12)._1), ((refactorDecoded12)._2).map(refactorElement25 => _root_.com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part2(refactorElement25))))((refactorEncoded12: (_root_.com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.ArchiveSearchPart1Part2])) => (((refactorEncoded12)._1).value, ((refactorEncoded12)._2).map(refactorElement26 => (refactorElement26).value)))

  lazy val archiveInfo: PublicEndpoint[com.histmersion.api.library.AcquireApi.ArchiveInfoPart1, Api.Error, ArchiveItem, Any] =
    (base.get.in("archive" / "info").in(query[String]("id")).out(jsonBody[ArchiveItem])).mapIn[_root_.com.histmersion.api.library.AcquireApi.ArchiveInfoPart1]((refactorDecoded13: _root_.java.lang.String) => _root_.com.histmersion.api.library.AcquireApi.ArchiveInfoPart1(refactorDecoded13))((refactorEncoded13: _root_.com.histmersion.api.library.AcquireApi.ArchiveInfoPart1) => (refactorEncoded13).value)

  lazy val archiveImport: PublicEndpoint[ArchiveImportRequest, Api.Error, Ingest.Report, Any] =
    base.post.in("archive" / "import").in(jsonBody[ArchiveImportRequest]).out(jsonBody[Ingest.Report])

  /** The library's Calibre gaps and push metadata, kept for a CLI that runs the push on its own machine; the server's
    * own push is the collections' (`CollectionsApi`).
    */
  lazy val calibreGaps: PublicEndpoint[Option[com.histmersion.api.library.AcquireApi.CalibreGapsPart1], Api.Error, CalibreGaps, Any] =
    (base.get.in("calibre" / "gaps").in(query[Option[Int]]("limit")).out(jsonBody[CalibreGaps])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CalibreGapsPart1]]((refactorDecoded6: _root_.scala.Option[_root_.scala.Int]) => (refactorDecoded6).map(refactorElement9 => _root_.com.histmersion.api.library.AcquireApi.CalibreGapsPart1(refactorElement9)))((refactorEncoded6: _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CalibreGapsPart1]) => (refactorEncoded6).map(refactorElement10 => (refactorElement10).value))

  lazy val calibreMeta: PublicEndpoint[com.histmersion.api.library.AcquireApi.CalibreMetaPart1, Api.Error, CalibreGap, Any] =
    (base.get.in("calibre" / "meta").in(query[String]("citekey")).out(jsonBody[CalibreGap])).mapIn[_root_.com.histmersion.api.library.AcquireApi.CalibreMetaPart1]((refactorDecoded7: _root_.java.lang.String) => _root_.com.histmersion.api.library.AcquireApi.CalibreMetaPart1(refactorDecoded7))((refactorEncoded7: _root_.com.histmersion.api.library.AcquireApi.CalibreMetaPart1) => (refactorEncoded7).value)

  lazy val calibrePushed: PublicEndpoint[CalibrePushRequest, Api.Error, CalibrePushResult, Any] =
    base.post.in("calibre" / "pushed").in(jsonBody[CalibrePushRequest]).out(jsonBody[CalibrePushResult])

  lazy val candidates
      : PublicEndpoint[(Option[com.histmersion.api.library.AcquireApi.CandidatesPart1Part1], Option[com.histmersion.api.library.AcquireApi.CandidatesPart1Part2], Option[com.histmersion.api.library.AcquireApi.CandidatesPart1Part3], Option[com.histmersion.api.library.AcquireApi.CandidatesPart1Part4]), Api.Error, CandidateList, Any] =
    (base.get
      .in("candidates")
      .in(query[Option[String]]("goal"))
      .in(query[Option[String]]("state"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .out(jsonBody[CandidateList])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part1], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part4])]((refactorDecoded16: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded16)._1).map(refactorElement29 => _root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part1(refactorElement29)), ((refactorDecoded16)._2).map(refactorElement30 => _root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part2(refactorElement30)), ((refactorDecoded16)._3).map(refactorElement31 => _root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part3(refactorElement31)), ((refactorDecoded16)._4).map(refactorElement32 => _root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part4(refactorElement32))))((refactorEncoded16: (_root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part1], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part3], _root_.scala.Option[_root_.com.histmersion.api.library.AcquireApi.CandidatesPart1Part4])) => (((refactorEncoded16)._1).map(refactorElement33 => (refactorElement33).value), ((refactorEncoded16)._2).map(refactorElement34 => (refactorElement34).value), ((refactorEncoded16)._3).map(refactorElement35 => (refactorElement35).value), ((refactorEncoded16)._4).map(refactorElement36 => (refactorElement36).value)))

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
