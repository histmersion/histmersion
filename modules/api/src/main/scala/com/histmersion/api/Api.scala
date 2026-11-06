package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.aliases.{InstanceInfo, LabelRename, LabelRenamed}
import com.histmersion.api.automation.{
  Activity,
  AgentsApi,
  AgentStateDeleted,
  AgentStateEntry,
  AgentStatePut,
  Goal,
  ListFilters,
  ManagersApi
}
import com.histmersion.api.backup.{Backup, RestoreRequest, RestoreResult}
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.{
  Attestation,
  EventDeleted,
  EventList,
  EventPatch,
  EventPut,
  EventsApi,
  HistoricalPeriod,
  ManualPlaceName,
  TilesApi
}
import com.histmersion.api.checks.FormatReport
import com.histmersion.api.exchange.{
  MergeApplyJob,
  MergeApplyRequest,
  MergeDetail,
  MergeImportRequest,
  MergeInfo,
  MergeLedger,
  MergePlan,
  MergeResolutions,
  MergeSession
}
import com.histmersion.api.instance.{About, Health, UiApi, UiConfig}
import com.histmersion.api.jobs.{JobActions, JobInfo}
import com.histmersion.api.library.{
  AcquireApi,
  ContradictionsResult,
  EdgesResolved,
  Facet,
  Filter,
  GeoHit,
  GeoHitPage,
  Ingest,
  InstanceLabel,
  NeighborRow,
  NoteImport,
  PassageResult,
  SearchHit,
  SearchResult,
  SeedRequest,
  SeedResult,
  TimeHistogram
}
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetInput,
  AssetLink,
  AssetPatch,
  AssetsStored,
  FigureLicenceFill,
  MediaCoversReport,
  MediaCoversRequest,
  MediaFacets,
  MediaGeoHit,
  MediaImportReport,
  MediaImportRequest,
  MediaItem,
  MediaList,
  MediaProvider,
  SourceCoverParams
}
import com.histmersion.api.research.{Backlink, Coverage}
import com.histmersion.api.settings.{ConfigEditApi, ReloadReport}
import com.histmersion.api.views.{AutopilotApi, EventDetail, PlaceDetail, ResolvedId}
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.instance.Version
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.api.research.Article
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.AnyEndpoint
import sttp.tapir.Schema

/** Every endpoint here and in the other `*Api` objects is a `lazy val`: initialising an object runs all of its strict
  * vals, so Scala.js would link every endpoint and every codec it names into the web bundle as soon as the page used
  * one of them.
  */
object Api:
  object Search:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Reindex:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Scope = Scope.T
    object Scope extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object PlacesGeo:
    type Json = Json.T
    object Json extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Merge:
    type Entity = Entity.T
    object Entity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Lookup:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Jobs:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object JobControl:
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object HttpMethod:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Facets:
    type Field = Field.T
    object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Prefix = Prefix.T
    object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object ExternalPlaces:
    type Format = Format.T
    object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Locator = Locator.T
    object Locator extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Assets:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Owner = Owner.T
    object Owner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object AgentState:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Apply = Apply.T
  object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentType = ContentType.T
  object ContentType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileBytes = FileBytes.T
  object FileBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileName = FileName.T
  object FileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Overwrite = Overwrite.T
  object Overwrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `region` is still sent by older links and clients; it is one more place name. */


  final case class FindingsQuery(filter: Filter, region: Option[FindingsQuery.Region]):
    def toFilter: Filter =
      region.map((_).value.trim).filter(_.nonEmpty).fold(filter)(r => filter.copy(places = filter.places :+ _root_.com.histmersion.api.library.Filter.Places(r)))

  object FindingsQuery:
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(filter: Filter): FindingsQuery = FindingsQuery(filter, None)

  private lazy val base = endpoint.in("api").errorOut(jsonBody[com.histmersion.api.Api.Error])

  /** A `def`, not a `val`: endpoints defined above `writeErrors` use it too. */
  private def writeBase = endpoint.in("api").errorOut(writeErrors)

  /** The parameter names are fixed: saved links, the web UI and the CLI rely on them. */
  lazy val filterQuery: EndpointInput[Filter] =
    import com.histmersion.api.library.Filter.Docs as D
    def q[A](p: D.Param)(using Codec[List[String], A, CodecFormat.TextPlain]) =
      query[A](p.rest).description(p.doc)
    q[Option[String]](D.query)
      .and(q[List[String]](D.tags))
      .and(q[List[String]](D.authors))
      .and(q[List[String]](D.places))
      .and(q[List[String]](D.topics))
      .and(q[List[String]](D.status))
      .and(q[Option[Long]](D.yearFrom))
      .and(q[Option[Long]](D.yearTo))
      .and(q[Option[Long]](D.from))
      .and(q[Option[Long]](D.to))
      .and(q[Option[Double]](D.minLon))
      .and(q[Option[Double]](D.minLat))
      .and(q[Option[Double]](D.maxLon))
      .and(q[Option[Double]](D.maxLat))
      .and(q[Option[String]](D.sort))
      .and(q[Option[Int]](D.limit))
      .and(q[Option[Int]](D.offset))
      .and(q[Option[String]](D.title))
      .and(q[Option[String]](D.goal))
      .mapTo[Filter.Query]
      .map(_.toFilter)(Filter.Query.from)

  lazy val version: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Version.Info, Any] =
    base.get.in("version").out(jsonBody[Version.Info])

  lazy val health: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  lazy val about: PublicEndpoint[Unit, com.histmersion.api.Api.Error, About, Any] =
    base.get.in("about").out(jsonBody[About])

  lazy val config: PublicEndpoint[Unit, com.histmersion.api.Api.Error, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  lazy val search: PublicEndpoint[(Option[com.histmersion.api.Api.Search.Text], Option[com.histmersion.api.Api.Search.Limit]), com.histmersion.api.Api.Error, List[SearchHit], Any] =
    (base.get
      .in("search")
      .in(query[Option[String]]("q"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.Api.Search.Text], _root_.scala.Option[_root_.com.histmersion.api.Api.Search.Limit])]((refactorDecoded107: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded107)._1).map(refactorElement93 => _root_.com.histmersion.api.Api.Search.Text(refactorElement93)), ((refactorDecoded107)._2).map(refactorElement94 => _root_.com.histmersion.api.Api.Search.Limit(refactorElement94))))((refactorEncoded107: (_root_.scala.Option[_root_.com.histmersion.api.Api.Search.Text], _root_.scala.Option[_root_.com.histmersion.api.Api.Search.Limit])) => (((refactorEncoded107)._1).map(refactorElement95 => (refactorElement95).value), ((refactorEncoded107)._2).map(refactorElement96 => (refactorElement96).value)))

  lazy val resolveId: PublicEndpoint[com.histmersion.api.Api.Lookup.Text, com.histmersion.api.Api.Error, ResolvedId, Any] =
    (base.get.in("resolve" / path[String]("id")).out(jsonBody[ResolvedId])).mapIn[_root_.com.histmersion.api.Api.Lookup.Text]((refactorDecoded108: _root_.java.lang.String) => _root_.com.histmersion.api.Api.Lookup.Text(refactorDecoded108))((refactorEncoded108: _root_.com.histmersion.api.Api.Lookup.Text) => (refactorEncoded108).value)

  lazy val getSource: PublicEndpoint[(com.histmersion.api.library.Citekey, com.histmersion.api.Api.SourceViewQuery.FullText), com.histmersion.api.Api.Error, Source.WithFindings, Any] =
    (base.get
      .in("sources" / path[String]("citekey"))
      .in(query[Boolean]("body").default(true))
      .out(jsonBody[Source.WithFindings])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.Api.SourceViewQuery.FullText)]((refactorDecoded109: (_root_.java.lang.String, _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded109)._1), _root_.com.histmersion.api.Api.SourceViewQuery.FullText((refactorDecoded109)._2)))((refactorEncoded109: (_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.Api.SourceViewQuery.FullText)) => (((refactorEncoded109)._1).value, ((refactorEncoded109)._2).value))

  lazy val sourceArticles: PublicEndpoint[com.histmersion.api.library.Citekey, com.histmersion.api.Api.Error, List[Backlink], Any] =
    (base.get
      .in("sources" / path[String]("citekey") / "articles")
      .out(jsonBody[List[Backlink]])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[_root_.com.histmersion.api.library.Citekey]((refactorDecoded4: _root_.java.lang.String) => _root_.com.histmersion.api.library.Citekey(refactorDecoded4))((refactorEncoded4: _root_.com.histmersion.api.library.Citekey) => (refactorEncoded4).value)

  final case class SourceViewQuery(
      citekey: com.histmersion.api.library.Citekey,
      fullText: SourceViewQuery.FullText = _root_.com.histmersion.api.Api.SourceViewQuery.FullText(false),
      section: Option[SourceViewQuery.Section] = None,
      heading: Option[SourceViewQuery.Heading] = None,
      offset: Option[SourceViewQuery.Offset] = None,
      chars: Option[SourceViewQuery.Chars] = None
  )

  object SourceViewQuery:
    object Heading:
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FullText = FullText.T
    object FullText extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Heading = Heading.T
    object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Chars = Chars.T
    object Chars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val sourceView: PublicEndpoint[SourceViewQuery, com.histmersion.api.Api.Error, com.histmersion.api.library.Source.View, Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "view")
      .in(query[Boolean]("fullText").default(false))
      .in(query[Option[String]]("section"))
      .in(query[Option[String]]("heading"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("chars"))
      .mapInTo[SourceViewQuery]
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(stringBody)).mapOut[_root_.com.histmersion.api.library.Source.View]((refactorDecoded: _root_.java.lang.String) => _root_.com.histmersion.api.library.Source.View(refactorDecoded))((refactorEncoded: _root_.com.histmersion.api.library.Source.View) => (refactorEncoded).value)

  final case class PassagesQuery(
      citekey: com.histmersion.api.library.Citekey,
      q: PassagesQuery.Q,
      regex: PassagesQuery.Regex = _root_.com.histmersion.api.Api.PassagesQuery.Regex(false),
      limit: Option[PassagesQuery.Limit] = None,
      context: Option[PassagesQuery.Context] = None
  )

  object PassagesQuery:
    type Q = Q.T
    object Q extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regex = Regex.T
    object Regex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val passages: PublicEndpoint[PassagesQuery, com.histmersion.api.Api.Error, PassageResult, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "passages")
      .in(
        query[String]("q").description(
          "the text to find (literal, case- and accent-insensitive), or a regex with regex=true"
        )
      )
      .in(query[Boolean]("regex").default(false))
      .in(query[Option[Int]]("limit").description("passages shown (default and cap from config)"))
      .in(query[Option[Int]]("context").description("characters of context around a match"))
      .mapInTo[PassagesQuery]
      .errorOut(writeErrors)
      .out(jsonBody[PassageResult])

  lazy val findings: PublicEndpoint[FindingsQuery, com.histmersion.api.Api.Error, List[GeoHit], Any] =
    base.get
      .in("findings")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[GeoHit]])

  lazy val findingsPage: PublicEndpoint[FindingsQuery, com.histmersion.api.Api.Error, GeoHitPage, Any] =
    base.get
      .in("findings" / "page")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[GeoHitPage])

  final case class AggregateQuery(
      by: Option[AggregateQuery.By],
      unit: Option[AggregateQuery.Unit],
      filter: Filter,
      depth: Option[AggregateQuery.Depth] = None,
      asOf: Option[AggregateQuery.AsOf] = None,
      samples: Option[AggregateQuery.Samples] = None,
      size: Option[AggregateQuery.Size] = None
  )

  object AggregateQuery:
    type TimeUnit = _root_.com.histmersion.api.Api.AggregateQuery.Unit
    val TimeUnit: _root_.com.histmersion.api.Api.AggregateQuery.Unit.type = _root_.com.histmersion.api.Api.AggregateQuery.Unit
    type TreeUnit = _root_.com.histmersion.api.Api.AggregateQuery.Unit
    val TreeUnit: _root_.com.histmersion.api.Api.AggregateQuery.Unit.type = _root_.com.histmersion.api.Api.AggregateQuery.Unit
    type TreeAsOf = _root_.com.histmersion.api.Api.AggregateQuery.AsOf
    val TreeAsOf: _root_.com.histmersion.api.Api.AggregateQuery.AsOf.type = _root_.com.histmersion.api.Api.AggregateQuery.AsOf
    type TreeSamples = _root_.com.histmersion.api.Api.AggregateQuery.Samples
    val TreeSamples: _root_.com.histmersion.api.Api.AggregateQuery.Samples.type = _root_.com.histmersion.api.Api.AggregateQuery.Samples

    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unit = Unit.T
    object Unit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Samples = Samples.T
    object Samples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Size = Size.T
    object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def time(filter: Filter, unit: Option[com.histmersion.api.Api.AggregateQuery.TimeUnit] = None): AggregateQuery =
      AggregateQuery(Some(_root_.com.histmersion.api.Api.AggregateQuery.By("time")), unit, filter)

    def tree(
        by: com.histmersion.api.Api.AggregateQuery.By,
        filter: Filter,
        unit: Option[com.histmersion.api.Api.AggregateQuery.TreeUnit] = None,
        depth: Option[com.histmersion.api.Api.AggregateQuery.Depth] = None,
        asOf: Option[com.histmersion.api.Api.AggregateQuery.TreeAsOf] = None,
        samples: Option[com.histmersion.api.Api.AggregateQuery.TreeSamples] = None
    ): AggregateQuery =
      AggregateQuery(Some(_root_.com.histmersion.api.Api.AggregateQuery.By((by).value)), unit, filter, depth.orElse(Some(0)), asOf, samples)

  lazy val aggregate: PublicEndpoint[AggregateQuery, com.histmersion.api.Api.Error, TimeHistogram, Any] =
    base.get
      .in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .in(query[Option[Int]]("depth"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("samples"))
      .in(query[Option[Long]]("size"))
      .mapInTo[AggregateQuery]
      .out(jsonBody[TimeHistogram])

  lazy val neighbors: PublicEndpoint[(com.histmersion.api.library.Citekey, List[com.histmersion.api.library.Edge.Kind.Wire]), com.histmersion.api.Api.Error, List[NeighborRow], Any] =
    (base.get
      .in("neighbors" / path[String]("citekey"))
      .in(query[List[String]]("kind"))
      .out(jsonBody[List[NeighborRow]])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.scala.List[_root_.com.histmersion.api.library.Edge.Kind.Wire])]((refactorDecoded5: (_root_.java.lang.String, _root_.scala.List[_root_.java.lang.String])) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded5)._1), ((refactorDecoded5)._2).map(refactorElement5 => _root_.com.histmersion.api.library.Edge.Kind.Wire(refactorElement5))))((refactorEncoded5: (_root_.com.histmersion.api.library.Citekey, _root_.scala.List[_root_.com.histmersion.api.library.Edge.Kind.Wire])) => (((refactorEncoded5)._1).value, ((refactorEncoded5)._2).map(refactorElement6 => (refactorElement6).value)))

  lazy val graph: PublicEndpoint[(com.histmersion.api.library.Citekey, Option[com.histmersion.api.library.Source.Graph.DepthCount]), com.histmersion.api.Api.Error, Source.Graph, Any] =
    (base.get
      .in("graph" / path[String]("citekey"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[Source.Graph])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.scala.Option[_root_.com.histmersion.api.library.Source.Graph.DepthCount])]((refactorDecoded112: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded112)._1), ((refactorDecoded112)._2).map(refactorElement99 => _root_.com.histmersion.api.library.Source.Graph.DepthCount(refactorElement99))))((refactorEncoded112: (_root_.com.histmersion.api.library.Citekey, _root_.scala.Option[_root_.com.histmersion.api.library.Source.Graph.DepthCount])) => (((refactorEncoded112)._1).value, ((refactorEncoded112)._2).map(refactorElement100 => (refactorElement100).value)))

  lazy val contradictions
      : PublicEndpoint[(Option[com.histmersion.api.library.Citekey], Option[com.histmersion.api.Api.AggregateQuery.AsOf]), com.histmersion.api.Api.Error, ContradictionsResult, Any] =
    (base.get
      .in("contradictions")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[Long]]("since"))
      .out(jsonBody[ContradictionsResult])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.library.Citekey], _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf])]((refactorDecoded7: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Long])) => (((refactorDecoded7)._1).map(refactorElement9 => _root_.com.histmersion.api.library.Citekey(refactorElement9)), ((refactorDecoded7)._2).map(refactorElement10 => _root_.com.histmersion.api.Api.AggregateQuery.AsOf(refactorElement10))))((refactorEncoded7: (_root_.scala.Option[_root_.com.histmersion.api.library.Citekey], _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf])) => (((refactorEncoded7)._1).map(refactorElement11 => (refactorElement11).value), ((refactorEncoded7)._2).map(refactorElement12 => (refactorElement12).value)))

  lazy val seed: PublicEndpoint[SeedRequest, com.histmersion.api.Api.Error, SeedResult, Any] =
    writeBase.post.in("seed").in(jsonBody[SeedRequest]).out(jsonBody[SeedResult])

  lazy val reindex: PublicEndpoint[(Option[com.histmersion.api.Api.Reindex.Scope], Option[com.histmersion.api.Api.Reindex.Limit]), com.histmersion.api.Api.Error, ReindexResult, Any] =
    (writeBase.post
      .in("reindex")
      .in(query[Option[String]]("after"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ReindexResult])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.Api.Reindex.Scope], _root_.scala.Option[_root_.com.histmersion.api.Api.Reindex.Limit])]((refactorDecoded114: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded114)._1).map(refactorElement105 => _root_.com.histmersion.api.Api.Reindex.Scope(refactorElement105)), ((refactorDecoded114)._2).map(refactorElement106 => _root_.com.histmersion.api.Api.Reindex.Limit(refactorElement106))))((refactorEncoded114: (_root_.scala.Option[_root_.com.histmersion.api.Api.Reindex.Scope], _root_.scala.Option[_root_.com.histmersion.api.Api.Reindex.Limit])) => (((refactorEncoded114)._1).map(refactorElement107 => (refactorElement107).value), ((refactorEncoded114)._2).map(refactorElement108 => (refactorElement108).value)))

  lazy val sources: PublicEndpoint[Filter, com.histmersion.api.Api.Error, SearchResult, Any] =
    base.get.in("sources").in(filterQuery).out(jsonBody[SearchResult])

  lazy val facets: PublicEndpoint[(com.histmersion.api.Api.Facets.Field, Option[com.histmersion.api.Api.Facets.Prefix], Filter), com.histmersion.api.Api.Error, List[Facet], Any] =
    (base.get
      .in("facets")
      .in(query[String]("field"))
      .in(query[Option[String]]("prefix"))
      .in(filterQuery)
      .out(jsonBody[List[Facet]])).mapIn[(_root_.com.histmersion.api.Api.Facets.Field, _root_.scala.Option[_root_.com.histmersion.api.Api.Facets.Prefix], _root_.com.histmersion.api.library.Filter)]((refactorDecoded115: (_root_.java.lang.String, _root_.scala.Option[_root_.java.lang.String], _root_.com.histmersion.api.library.Filter)) => (_root_.com.histmersion.api.Api.Facets.Field((refactorDecoded115)._1), ((refactorDecoded115)._2).map(refactorElement109 => _root_.com.histmersion.api.Api.Facets.Prefix(refactorElement109)), (refactorDecoded115)._3))((refactorEncoded115: (_root_.com.histmersion.api.Api.Facets.Field, _root_.scala.Option[_root_.com.histmersion.api.Api.Facets.Prefix], _root_.com.histmersion.api.library.Filter)) => (((refactorEncoded115)._1).value, ((refactorEncoded115)._2).map(refactorElement110 => (refactorElement110).value), (refactorEncoded115)._3))

  private[api] def writeErrors =
    oneOf[com.histmersion.api.Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[com.histmersion.api.Api.Error])(
        (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound))).andThen(refactorResult => ((refactorResult) == (true)))
      ),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[com.histmersion.api.Api.Error])(
        (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Backup.CodeMaintenance))).andThen(refactorResult2 => ((refactorResult2) == (true)))
      ),
      oneOfVariantValueMatcher(StatusCode.PreconditionFailed, jsonBody[com.histmersion.api.Api.Error])(
        (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodePrecondition))).andThen(refactorResult3 => ((refactorResult3) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[com.histmersion.api.Api.Error]))
    )

  private def strictQuery = query[Boolean]("strict")
    .default(false)
    .description("refuse the write when its Markdown has a formatting error (default: report only)")

  lazy val putSource
      : PublicEndpoint[(com.histmersion.api.library.Citekey, Source.Put, com.histmersion.api.Api.Overwrite), com.histmersion.api.Api.Error, Source.WithFindings, Any] =
    (endpoint.put
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[Source.Put])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Source.WithFindings])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Source.Put, _root_.com.histmersion.api.Api.Overwrite)]((refactorDecoded10: (_root_.java.lang.String, _root_.com.histmersion.api.library.Source.Put, _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded10)._1), (refactorDecoded10)._2, _root_.com.histmersion.api.Api.Overwrite((refactorDecoded10)._3)))((refactorEncoded10: (_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Source.Put, _root_.com.histmersion.api.Api.Overwrite)) => (((refactorEncoded10)._1).value, (refactorEncoded10)._2, ((refactorEncoded10)._3).value))

  lazy val patchSource
      : PublicEndpoint[(com.histmersion.api.library.Citekey, Source.Patch, com.histmersion.api.Api.Apply), com.histmersion.api.Api.Error, Source.WithFindings, Any] =
    (endpoint.patch
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[Source.Patch])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Source.WithFindings])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Source.Patch, _root_.com.histmersion.api.Api.Apply)]((refactorDecoded117: (_root_.java.lang.String, _root_.com.histmersion.api.library.Source.Patch, _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded117)._1), (refactorDecoded117)._2, _root_.com.histmersion.api.Api.Apply((refactorDecoded117)._3)))((refactorEncoded117: (_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Source.Patch, _root_.com.histmersion.api.Api.Apply)) => (((refactorEncoded117)._1).value, (refactorEncoded117)._2, ((refactorEncoded117)._3).value))

  lazy val lintSource: PublicEndpoint[com.histmersion.api.library.Citekey, com.histmersion.api.Api.Error, FormatReport, Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "lint")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])).mapIn[_root_.com.histmersion.api.library.Citekey]((refactorDecoded118: _root_.java.lang.String) => _root_.com.histmersion.api.library.Citekey(refactorDecoded118))((refactorEncoded118: _root_.com.histmersion.api.library.Citekey) => (refactorEncoded118).value)

  lazy val fixSourceFormat: PublicEndpoint[com.histmersion.api.library.Citekey, com.histmersion.api.Api.Error, FormatReport, Any] =
    (endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "lint" / "fix")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])).mapIn[_root_.com.histmersion.api.library.Citekey]((refactorDecoded13: _root_.java.lang.String) => _root_.com.histmersion.api.library.Citekey(refactorDecoded13))((refactorEncoded13: _root_.com.histmersion.api.library.Citekey) => (refactorEncoded13).value)

  lazy val renameSource: PublicEndpoint[(com.histmersion.api.library.Citekey, Rename.Request), com.histmersion.api.Api.Error, Source.Renamed, Any] =
    (writeBase.post
      .in("sources" / path[String]("citekey") / "rename")
      .in(jsonBody[Rename.Request])
      .out(jsonBody[Source.Renamed])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Rename.Request)]((refactorDecoded120: (_root_.java.lang.String, _root_.com.histmersion.api.library.Rename.Request)) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded120)._1), (refactorDecoded120)._2))((refactorEncoded120: (_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Rename.Request)) => (((refactorEncoded120)._1).value, (refactorEncoded120)._2))

  lazy val deleteSource
      : PublicEndpoint[(com.histmersion.api.library.Citekey, Source.DeleteRequest), com.histmersion.api.Api.Error, Source.Deletion, Any] =
    (writeBase.post
      .in("sources" / path[String]("citekey") / "delete")
      .in(jsonBody[Source.DeleteRequest])
      .out(jsonBody[Source.Deletion])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Source.DeleteRequest)]((refactorDecoded121: (_root_.java.lang.String, _root_.com.histmersion.api.library.Source.DeleteRequest)) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded121)._1), (refactorDecoded121)._2))((refactorEncoded121: (_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.library.Source.DeleteRequest)) => (((refactorEncoded121)._1).value, (refactorEncoded121)._2))

  lazy val sourceAliases: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[Source.Alias], Any] =
    base.get.in("source-aliases").out(jsonBody[List[Source.Alias]])

  lazy val importNote: PublicEndpoint[NoteImport, com.histmersion.api.Api.Error, SeedResult, Any] =
    writeBase.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  lazy val exportSource: PublicEndpoint[com.histmersion.api.library.Citekey, com.histmersion.api.Api.Error, com.histmersion.api.library.Source.Export, Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(writeErrors)
      .out(stringBody)).mapIn[_root_.com.histmersion.api.library.Citekey]((refactorDecoded16: _root_.java.lang.String) => _root_.com.histmersion.api.library.Citekey(refactorDecoded16))((refactorEncoded16: _root_.com.histmersion.api.library.Citekey) => (refactorEncoded16).value).mapOut[_root_.com.histmersion.api.library.Source.Export]((refactorDecoded17: _root_.java.lang.String) => _root_.com.histmersion.api.library.Source.Export(refactorDecoded17))((refactorEncoded17: _root_.com.histmersion.api.library.Source.Export) => (refactorEncoded17).value)

  lazy val citekeys: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[com.histmersion.api.library.Citekey], Any] =
    (base.get.in("citekeys").out(jsonBody[List[String]])).mapOut[_root_.scala.List[_root_.com.histmersion.api.library.Citekey]]((refactorDecoded2: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded2).map(refactorElement => _root_.com.histmersion.api.library.Citekey(refactorElement)))((refactorEncoded2: _root_.scala.List[_root_.com.histmersion.api.library.Citekey]) => (refactorEncoded2).map(refactorElement2 => (refactorElement2).value))

  lazy val resolveEdges: PublicEndpoint[Unit, com.histmersion.api.Api.Error, EdgesResolved, Any] =
    writeBase.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  lazy val configReload: PublicEndpoint[Unit, com.histmersion.api.Api.Error, ReloadReport, Any] =
    writeBase.post.in("config" / "reload").out(jsonBody[ReloadReport])

  lazy val backup: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Backup.Status, Any] =
    writeBase.post.in("backup").out(jsonBody[Backup.Status])

  /** A job, not a request: Postgres cannot run `VACUUM` inside a transaction. */
  lazy val maintenanceVacuum: PublicEndpoint[Vacuum.Request, com.histmersion.api.Api.Error, JobInfo, Any] =
    writeBase.post.in("maintenance" / "vacuum").in(jsonBody[Vacuum.Request]).out(jsonBody[JobInfo])

  lazy val backupStatus: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Backup.Status, Any] =
    endpoint.get.in("api" / "backup" / "status").errorOut(writeErrors).out(jsonBody[Backup.Status])

  lazy val backups: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[Backup.Info], Any] =
    base.get.in("backups").out(jsonBody[List[Backup.Info]])

  lazy val downloadBackup: PublicEndpoint[com.histmersion.api.backup.Backup.Name, com.histmersion.api.Api.Error, TapirFile, Any] =
    endpoint.get.in("api" / "backups" / path[String]("file")).errorOut(writeErrors).out(fileBody)

  lazy val restore: PublicEndpoint[RestoreRequest, com.histmersion.api.Api.Error, RestoreResult, Any] =
    writeBase.post.in("restore").in(jsonBody[RestoreRequest]).out(jsonBody[RestoreResult])

  lazy val instance: PublicEndpoint[Unit, com.histmersion.api.Api.Error, InstanceInfo, Any] =
    base.get.in("instance").out(jsonBody[InstanceInfo])

  lazy val setInstance: PublicEndpoint[InstanceLabel, com.histmersion.api.Api.Error, InstanceInfo, Any] =
    writeBase.put.in("instance").in(jsonBody[InstanceLabel]).out(jsonBody[InstanceInfo])

  lazy val exportBundle: PublicEndpoint[Option[com.histmersion.api.exchange.Bundle.Name], com.histmersion.api.Api.Error, (TapirFile, com.histmersion.api.Api.FileName), Any] =
    endpoint.get
      .in("api" / "bundle")
      .in(query[Option[String]]("since"))
      .errorOut(writeErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  lazy val verifyBundle: PublicEndpoint[TapirFile, com.histmersion.api.Api.Error, Bundle.VerifyResult, Any] =
    writeBase.post.in("bundle" / "verify").in(fileBody).out(jsonBody[Bundle.VerifyResult])

  /** Not an MCP tool: its input is a file upload, which an MCP call cannot carry. */
  lazy val importBundle: PublicEndpoint[(com.histmersion.api.Api.Apply, TapirFile), com.histmersion.api.Api.Error, Bundle.ImportResult, Any] =
    writeBase.post
      .in("bundle" / "import")
      .in(query[Boolean]("dryRun").default(false))
      .in(fileBody)
      .out(jsonBody[Bundle.ImportResult])

  /** Kept on the server under its sha256 so `merge plan` and `merge import` can name it: their body is the resolution
    * set, and a request carries one body.
    */
  lazy val uploadBundle: PublicEndpoint[TapirFile, com.histmersion.api.Api.Error, Bundle.Upload, Any] =
    writeBase.post.in("bundle" / "upload").in(fileBody).out(jsonBody[Bundle.Upload])

  lazy val mergePlan: PublicEndpoint[MergePlan.Request, com.histmersion.api.Api.Error, MergePlan, Any] =
    writeBase.post.in("merge" / "plan").in(jsonBody[MergePlan.Request]).out(jsonBody[MergePlan])

  lazy val mergeImport: PublicEndpoint[MergeImportRequest, com.histmersion.api.Api.Error, Bundle.ImportResult, Any] =
    writeBase.post.in("merge" / "import").in(jsonBody[MergeImportRequest]).out(jsonBody[Bundle.ImportResult])

  lazy val mergeApply: PublicEndpoint[MergeApplyRequest, com.histmersion.api.Api.Error, MergeApplyJob, Any] =
    writeBase.post.in("merge" / "apply").in(jsonBody[MergeApplyRequest]).out(jsonBody[MergeApplyJob])

  lazy val mergeApplyStatus: PublicEndpoint[com.histmersion.api.Api.Merge.Id, com.histmersion.api.Api.Error, MergeApplyJob, Any] =
    (writeBase.get.in("merge" / "apply" / path[String]("job")).out(jsonBody[MergeApplyJob])).mapIn[_root_.com.histmersion.api.Api.Merge.Id]((refactorDecoded23: _root_.java.lang.String) => _root_.com.histmersion.api.Api.Merge.Id(refactorDecoded23))((refactorEncoded23: _root_.com.histmersion.api.Api.Merge.Id) => (refactorEncoded23).value)

  lazy val mergeSession: PublicEndpoint[com.histmersion.api.Api.Merge.Id, com.histmersion.api.Api.Error, MergeSession, Any] =
    (writeBase.get.in("merge" / "sessions" / path[String]("bundle")).out(jsonBody[MergeSession])).mapIn[_root_.com.histmersion.api.Api.Merge.Id]((refactorDecoded130: _root_.java.lang.String) => _root_.com.histmersion.api.Api.Merge.Id(refactorDecoded130))((refactorEncoded130: _root_.com.histmersion.api.Api.Merge.Id) => (refactorEncoded130).value)

  lazy val putMergeSession
      : PublicEndpoint[(com.histmersion.api.Api.Merge.Id, MergeResolutions), com.histmersion.api.Api.Error, MergeSession, Any] =
    (writeBase.put
      .in("merge" / "sessions" / path[String]("bundle"))
      .in(jsonBody[MergeResolutions])
      .out(jsonBody[MergeSession])).mapIn[(_root_.com.histmersion.api.Api.Merge.Id, _root_.com.histmersion.api.exchange.MergeResolutions)]((refactorDecoded131: (_root_.java.lang.String, _root_.com.histmersion.api.exchange.MergeResolutions)) => (_root_.com.histmersion.api.Api.Merge.Id((refactorDecoded131)._1), (refactorDecoded131)._2))((refactorEncoded131: (_root_.com.histmersion.api.Api.Merge.Id, _root_.com.histmersion.api.exchange.MergeResolutions)) => (((refactorEncoded131)._1).value, (refactorEncoded131)._2))

  lazy val parseResolutions: PublicEndpoint[com.histmersion.api.Api.Merge.Id, com.histmersion.api.Api.Error, MergeResolutions, Any] =
    (writeBase.post.in("merge" / "resolutions").in(stringBody).out(jsonBody[MergeResolutions])).mapIn[_root_.com.histmersion.api.Api.Merge.Id]((refactorDecoded26: _root_.java.lang.String) => _root_.com.histmersion.api.Api.Merge.Id(refactorDecoded26))((refactorEncoded26: _root_.com.histmersion.api.Api.Merge.Id) => (refactorEncoded26).value)

  lazy val merges: PublicEndpoint[Option[com.histmersion.api.Api.Merge.Limit], com.histmersion.api.Api.Error, List[MergeInfo], Any] =
    (base.get.in("merges").in(query[Option[Int]]("limit")).out(jsonBody[List[MergeInfo]])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Limit]]((refactorDecoded133: _root_.scala.Option[_root_.scala.Int]) => (refactorDecoded133).map(refactorElement113 => _root_.com.histmersion.api.Api.Merge.Limit(refactorElement113)))((refactorEncoded133: _root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Limit]) => (refactorEncoded133).map(refactorElement114 => (refactorElement114).value))

  lazy val mergeLedger
      : PublicEndpoint[(Option[com.histmersion.api.Api.Merge.Id], Option[com.histmersion.api.Api.Merge.Entity], Option[com.histmersion.api.Api.Merge.Limit]), com.histmersion.api.Api.Error, MergeLedger, Any] =
    (writeBase.get
      .in("merges" / "ledger")
      .in(query[Option[String]]("uid"))
      .in(query[Option[String]]("merge"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[MergeLedger])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Id], _root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Entity], _root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Limit])]((refactorDecoded134: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded134)._1).map(refactorElement115 => _root_.com.histmersion.api.Api.Merge.Id(refactorElement115)), ((refactorDecoded134)._2).map(refactorElement116 => _root_.com.histmersion.api.Api.Merge.Entity(refactorElement116)), ((refactorDecoded134)._3).map(refactorElement117 => _root_.com.histmersion.api.Api.Merge.Limit(refactorElement117))))((refactorEncoded134: (_root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Id], _root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Entity], _root_.scala.Option[_root_.com.histmersion.api.Api.Merge.Limit])) => (((refactorEncoded134)._1).map(refactorElement118 => (refactorElement118).value), ((refactorEncoded134)._2).map(refactorElement119 => (refactorElement119).value), ((refactorEncoded134)._3).map(refactorElement120 => (refactorElement120).value)))

  lazy val mergeDetail: PublicEndpoint[com.histmersion.api.Api.Merge.Id, com.histmersion.api.Api.Error, MergeDetail, Any] =
    (writeBase.get.in("merges" / path[String]("id")).out(jsonBody[MergeDetail])).mapIn[_root_.com.histmersion.api.Api.Merge.Id]((refactorDecoded29: _root_.java.lang.String) => _root_.com.histmersion.api.Api.Merge.Id(refactorDecoded29))((refactorEncoded29: _root_.com.histmersion.api.Api.Merge.Id) => (refactorEncoded29).value)

  final case class ArticleQuery(
      filter: Filter = Filter(),
      kind: Option[ArticleQuery.Kind] = None,
      text: Option[ArticleQuery.Text] = None
  )

  object ArticleQuery:
    object Kind:
      type Wire = Wire.T
      object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val articles: PublicEndpoint[ArticleQuery, com.histmersion.api.Api.Error, Article.List, Any] =
    base.get
      .in("articles")
      .in(filterQuery)
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("text"))
      .mapInTo[ArticleQuery]
      .out(jsonBody[Article.List])

  lazy val renameArticle: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    (writeBase.post
      .in("articles" / path[String]("slug") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.aliases.LabelRename)]((refactorDecoded136: (_root_.java.lang.String, _root_.com.histmersion.api.aliases.LabelRename)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded136)._1), (refactorDecoded136)._2))((refactorEncoded136: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.aliases.LabelRename)) => (((refactorEncoded136)._1).value, (refactorEncoded136)._2))

  lazy val getArticle: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.research.Article.FullText), com.histmersion.api.Api.Error, Article.View, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug"))
      .in(
        query[Boolean]("brief")
          .default(false)
          .description("leave out the formatting lint and the coverage (a hover card's read)")
      )
      .errorOut(writeErrors)
      .out(jsonBody[Article.View])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.FullText)]((refactorDecoded137: (_root_.java.lang.String, _root_.scala.Boolean)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded137)._1), _root_.com.histmersion.api.research.Article.FullText((refactorDecoded137)._2)))((refactorEncoded137: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.FullText)) => (((refactorEncoded137)._1).value, ((refactorEncoded137)._2).value))

  lazy val putArticle
      : PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, Article.Put, com.histmersion.api.Api.Overwrite), com.histmersion.api.Api.Error, Article.View, Any] =
    (endpoint.put
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[Article.Put])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Article.View])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.Put, _root_.com.histmersion.api.Api.Overwrite)]((refactorDecoded138: (_root_.java.lang.String, _root_.com.histmersion.api.research.Article.Put, _root_.scala.Boolean)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded138)._1), (refactorDecoded138)._2, _root_.com.histmersion.api.Api.Overwrite((refactorDecoded138)._3)))((refactorEncoded138: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.Put, _root_.com.histmersion.api.Api.Overwrite)) => (((refactorEncoded138)._1).value, (refactorEncoded138)._2, ((refactorEncoded138)._3).value))

  lazy val patchArticle
      : PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, Article.Patch, com.histmersion.api.Api.Apply), com.histmersion.api.Api.Error, Article.View, Any] =
    (endpoint.patch
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[Article.Patch])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[Article.View])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.Patch, _root_.com.histmersion.api.Api.Apply)]((refactorDecoded33: (_root_.java.lang.String, _root_.com.histmersion.api.research.Article.Patch, _root_.scala.Boolean)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded33)._1), (refactorDecoded33)._2, _root_.com.histmersion.api.Api.Apply((refactorDecoded33)._3)))((refactorEncoded33: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.Patch, _root_.com.histmersion.api.Api.Apply)) => (((refactorEncoded33)._1).value, (refactorEncoded33)._2, ((refactorEncoded33)._3).value))

  lazy val lintArticle: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, FormatReport, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "lint")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded140: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded140))((refactorEncoded140: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded140).value)

  lazy val fixArticleFormat: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, FormatReport, Any] =
    (endpoint.post
      .in("api" / "articles" / path[String]("slug") / "lint" / "fix")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded35: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded35))((refactorEncoded35: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded35).value)

  lazy val checkArticle: PublicEndpoint[
    (com.histmersion.api.research.ReviewGoal.Slug, Article.Edits.ArticleCheckRequest),
    com.histmersion.api.Api.Error,
    Article.Edits.ArticleCheck,
    Any
  ] =
    (endpoint.post
      .in("api" / "articles" / path[String]("slug") / "check")
      .in(jsonBody[Article.Edits.ArticleCheckRequest])
      .errorOut(writeErrors)
      .out(jsonBody[Article.Edits.ArticleCheck])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.Edits.ArticleCheckRequest)]((refactorDecoded142: (_root_.java.lang.String, _root_.com.histmersion.api.research.Article.Edits.ArticleCheckRequest)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded142)._1), (refactorDecoded142)._2))((refactorEncoded142: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.Edits.ArticleCheckRequest)) => (((refactorEncoded142)._1).value, (refactorEncoded142)._2))

  lazy val articleCoverage: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, Coverage, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "coverage")
      .errorOut(writeErrors)
      .out(jsonBody[Coverage])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded37: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded37))((refactorEncoded37: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded37).value)

  lazy val articleReview: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, Article.ReviewView, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "review")
      .errorOut(writeErrors)
      .out(jsonBody[Article.ReviewView])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded38: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded38))((refactorEncoded38: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded38).value)

  lazy val deleteArticle: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, Article.Deleted, Any] =
    (endpoint.delete
      .in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Article.Deleted])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded145: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded145))((refactorEncoded145: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded145).value)

  lazy val exportArticle: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, com.histmersion.api.research.Article.Export, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "export")
      .errorOut(writeErrors)
      .out(stringBody)).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded146: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded146))((refactorEncoded146: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded146).value).mapOut[_root_.com.histmersion.api.research.Article.Export]((refactorDecoded147: _root_.java.lang.String) => _root_.com.histmersion.api.research.Article.Export(refactorDecoded147))((refactorEncoded147: _root_.com.histmersion.api.research.Article.Export) => (refactorEncoded147).value)

  lazy val importArticle: PublicEndpoint[Article.Import, com.histmersion.api.Api.Error, SeedResult, Any] =
    base.post.in("articles" / "import").in(jsonBody[Article.Import]).out(jsonBody[SeedResult])

  lazy val articleVersions: PublicEndpoint[com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.Api.Error, List[Article.Version], Any] =
    (base.get.in("articles" / path[String]("slug") / "versions").out(jsonBody[List[Article.Version]])).mapIn[_root_.com.histmersion.api.research.ReviewGoal.Slug]((refactorDecoded148: _root_.java.lang.String) => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorDecoded148))((refactorEncoded148: _root_.com.histmersion.api.research.ReviewGoal.Slug) => (refactorEncoded148).value)

  lazy val articleVersion: PublicEndpoint[(com.histmersion.api.research.ReviewGoal.Slug, com.histmersion.api.research.Article.VersionId), com.histmersion.api.Api.Error, Article.VersionFull, Any] =
    (base.get
      .in("articles" / path[String]("slug") / "versions" / path[Long]("versionId"))
      .out(jsonBody[Article.VersionFull])).mapIn[(_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.VersionId)]((refactorDecoded149: (_root_.java.lang.String, _root_.scala.Long)) => (_root_.com.histmersion.api.research.ReviewGoal.Slug((refactorDecoded149)._1), _root_.com.histmersion.api.research.Article.VersionId((refactorDecoded149)._2)))((refactorEncoded149: (_root_.com.histmersion.api.research.ReviewGoal.Slug, _root_.com.histmersion.api.research.Article.VersionId)) => (((refactorEncoded149)._1).value, ((refactorEncoded149)._2).value))

  lazy val articleSlugs: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[com.histmersion.api.research.ReviewGoal.Slug], Any] =
    (base.get.in("article-slugs").out(jsonBody[List[String]])).mapOut[_root_.scala.List[_root_.com.histmersion.api.research.ReviewGoal.Slug]]((refactorDecoded28: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded28).map(refactorElement17 => _root_.com.histmersion.api.research.ReviewGoal.Slug(refactorElement17)))((refactorEncoded28: _root_.scala.List[_root_.com.histmersion.api.research.ReviewGoal.Slug]) => (refactorEncoded28).map(refactorElement18 => (refactorElement18).value))

  // lazy: [[EventsApi]] builds on this object's inputs, so the list waits until both exist
  lazy val all: List[AnyEndpoint] = List(
    version,
    health,
    about,
    config,
    search,
    sources,
    facets,
    // Tapir tries endpoints in order: the longer `sources/{ck}/…` paths go before `sources/{ck}`
    sourceArticles,
    sourceView,
    passages,
    lintSource,
    fixSourceFormat,
    getSource,
    findingsPage,
    findings,
    aggregate,
    neighbors,
    graph,
    contradictions,
    seed,
    putSource,
    patchSource,
    renameSource,
    deleteSource,
    sourceAliases,
    importNote,
    exportSource,
    citekeys,
    resolveEdges,
    reindex,
    configReload,
    backup,
    backups,
    downloadBackup,
    restore,
    maintenanceVacuum,
    instance,
    setInstance,
    exportBundle,
    verifyBundle,
    importBundle,
    uploadBundle,
    mergePlan,
    mergeImport,
    mergeApply,
    mergeApplyStatus,
    mergeSession,
    putMergeSession,
    parseResolutions,
    // `merges/ledger` before `merges/{id}`
    merges,
    mergeLedger,
    mergeDetail,
    renameArticle,
    renameEvent,
    renameGoal,
    renamePlace,
    // `articles/import` and the longer `articles/{slug}/…` paths go before `articles/{slug}`
    articles,
    articleSlugs,
    importArticle,
    exportArticle,
    articleVersions,
    articleVersion,
    lintArticle,
    fixArticleFormat,
    checkArticle,
    articleCoverage,
    articleReview,
    getArticle,
    putArticle,
    patchArticle,
    deleteArticle,
    // `events/geo` before `events/{id}`
    events,
    eventsGeo,
    getEvent,
    putEvent,
    patchEvent,
    deleteEvent,
    attestations,
    getAttestation,
    createAttestation,
    patchAttestation,
    deleteAttestation,
    goals,
    getGoal,
    putGoal,
    patchGoal,
    deleteGoal,
    agentStateList,
    agentStateGet,
    agentStateSet,
    agentStateDelete,
    // `/{id}/children` and `/{id}/tree` before `/{id}`
    places,
    placesGeo,
    placeNames,
    placeOverrides,
    placeSources,
    placeChildren,
    placeTree,
    placeFootprints,
    placeLinks,
    place,
    addPlaceName,
    removePlaceName,
    setPlaceOverride,
    mergePlace,
    removePlace,
    addPlace,
    importPlaces,
    rebuildPlaces,
    fetchExternalPlaces,
    loadPlaces,
    buildHierarchy,
    periods,
    // `periods/seed|import|fetch` are POSTs, so the GET `periods/{id}` never shadows them
    period,
    seedPeriods,
    importPeriods,
    fetchPeriods,
    init,
    // `sources/{ck}/assets` before `sources/{ck}`
    ingest,
    reocr,
    original,
    job,
    jobControl,
    jobs,
    activity,
    assets,
    putAssets,
    assetDetail,
    patchAsset,
    asset,
    mediaGeo,
    assetLinks,
    createAssetLink,
    deleteAssetLink,
    // `media/facets|import|fill-licences` before `media/{uid}`
    mediaFacets,
    mediaProviders,
    mediaImport,
    mediaImportJob,
    mediaCovers,
    mediaCoversJob,
    putSourceCover,
    fillFigureLicences,
    mediaList,
    mediaShow,
    mediaThumb,
    mediaFile,
    // IIIF `info.json` before the image endpoint
    iiifInfo,
    iiifImage,
    resolveId,
    backupStatus
  ) ++ OpsApi.all ++ AgentsApi.all ++ ManagersApi.all ++ AutopilotApi.switches ++ library.AcquisitionWorkApi.all ++
    ManagersApi.steering ++ AutopilotApi.draining ++ ManagersApi.stalled ++ UiApi.all ++ EventsApi.all ++ AcquireApi.all ++ BasemapApi.all ++ TilesApi.all ++ ConfigEditApi.all ++ personal.PersonalApi.all ++
    library.CollectionsApi.all

  lazy val places: PublicEndpoint[(Option[com.histmersion.api.catalogue.Place.Kind.Wire], Option[com.histmersion.api.Api.AggregateQuery.AsOf], Option[com.histmersion.api.Api.PlacesGeoQuery.Limit]), com.histmersion.api.Api.Error, List[
    Place.Hit
  ], Any] =
    (base.get
      .in("places")
      .in(query[Option[String]]("q"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[Place.Hit]])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Kind.Wire], _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf], _root_.scala.Option[_root_.com.histmersion.api.Api.PlacesGeoQuery.Limit])]((refactorDecoded150: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Long], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded150)._1).map(refactorElement121 => _root_.com.histmersion.api.catalogue.Place.Kind.Wire(refactorElement121)), ((refactorDecoded150)._2).map(refactorElement122 => _root_.com.histmersion.api.Api.AggregateQuery.AsOf(refactorElement122)), ((refactorDecoded150)._3).map(refactorElement123 => _root_.com.histmersion.api.Api.PlacesGeoQuery.Limit(refactorElement123))))((refactorEncoded150: (_root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Kind.Wire], _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf], _root_.scala.Option[_root_.com.histmersion.api.Api.PlacesGeoQuery.Limit])) => (((refactorEncoded150)._1).map(refactorElement124 => (refactorElement124).value), ((refactorEncoded150)._2).map(refactorElement125 => (refactorElement125).value), ((refactorEncoded150)._3).map(refactorElement126 => (refactorElement126).value)))

  final case class PlacesGeoQuery(
      kind: Option[PlacesGeoQuery.Kind] = None,
      asOf: Option[PlacesGeoQuery.AsOf] = None,
      limit: Option[PlacesGeoQuery.Limit] = None,
      from: Option[PlacesGeoQuery.From] = None,
      to: Option[PlacesGeoQuery.To] = None,
      pick: Option[PlacesGeoQuery.Pick] = None
  )

  object PlacesGeoQuery:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val placesGeo: PublicEndpoint[PlacesGeoQuery, com.histmersion.api.Api.Error, com.histmersion.api.Api.PlacesGeo.Json, Any] =
    (base.get
      .in("places" / "geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("asOf"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[String]]("pick"))
          .mapTo[PlacesGeoQuery]
      )
      .out(stringBody)).mapOut[_root_.com.histmersion.api.Api.PlacesGeo.Json]((refactorDecoded14: _root_.java.lang.String) => _root_.com.histmersion.api.Api.PlacesGeo.Json(refactorDecoded14))((refactorEncoded14: _root_.com.histmersion.api.Api.PlacesGeo.Json) => (refactorEncoded14).value)

  lazy val placeChildren
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Option[com.histmersion.api.Api.AggregateQuery.AsOf]), com.histmersion.api.Api.Error, List[Place.Summary], Any] =
    (base.get
      .in("places" / path[String]("id") / "children")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[Place.Summary]])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf])]((refactorDecoded45: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Long])) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded45)._1), ((refactorDecoded45)._2).map(refactorElement35 => _root_.com.histmersion.api.Api.AggregateQuery.AsOf(refactorElement35))))((refactorEncoded45: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf])) => (((refactorEncoded45)._1).value, ((refactorEncoded45)._2).map(refactorElement36 => (refactorElement36).value)))

  lazy val placeTree
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Option[com.histmersion.api.Api.AggregateQuery.AsOf], Option[com.histmersion.api.catalogue.Place.Depth]), com.histmersion.api.Api.Error, List[Place.TreeRow], Any] =
    (base.get
      .in("places" / path[String]("id") / "tree")
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[List[Place.TreeRow]])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Depth])]((refactorDecoded152: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Long], _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded152)._1), ((refactorDecoded152)._2).map(refactorElement129 => _root_.com.histmersion.api.Api.AggregateQuery.AsOf(refactorElement129)), ((refactorDecoded152)._3).map(refactorElement130 => _root_.com.histmersion.api.catalogue.Place.Depth(refactorElement130))))((refactorEncoded152: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Depth])) => (((refactorEncoded152)._1).value, ((refactorEncoded152)._2).map(refactorElement131 => (refactorElement131).value), ((refactorEncoded152)._3).map(refactorElement132 => (refactorElement132).value)))

  lazy val place
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Option[com.histmersion.api.Api.AggregateQuery.AsOf], Option[com.histmersion.api.catalogue.Place.Detail]), com.histmersion.api.Api.Error, PlaceDetail, Any] =
    (base.get
      .in("places" / path[String]("id"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Boolean]]("counts"))
      .out(jsonBody[PlaceDetail])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Detail])]((refactorDecoded47: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Long], _root_.scala.Option[_root_.scala.Boolean])) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded47)._1), ((refactorDecoded47)._2).map(refactorElement41 => _root_.com.histmersion.api.Api.AggregateQuery.AsOf(refactorElement41)), ((refactorDecoded47)._3).map(refactorElement42 => _root_.com.histmersion.api.catalogue.Place.Detail(refactorElement42))))((refactorEncoded47: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf], _root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Detail])) => (((refactorEncoded47)._1).value, ((refactorEncoded47)._2).map(refactorElement43 => (refactorElement43).value), ((refactorEncoded47)._3).map(refactorElement44 => (refactorElement44).value)))

  lazy val placeFootprints
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Option[com.histmersion.api.Api.AggregateQuery.AsOf]), com.histmersion.api.Api.Error, List[Place.Footprint], Any] =
    (base.get
      .in("places" / path[String]("id") / "footprints")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[Place.Footprint]])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf])]((refactorDecoded154: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Long])) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded154)._1), ((refactorDecoded154)._2).map(refactorElement137 => _root_.com.histmersion.api.Api.AggregateQuery.AsOf(refactorElement137))))((refactorEncoded154: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.scala.Option[_root_.com.histmersion.api.Api.AggregateQuery.AsOf])) => (((refactorEncoded154)._1).value, ((refactorEncoded154)._2).map(refactorElement138 => (refactorElement138).value)))

  lazy val placeLinks: PublicEndpoint[com.histmersion.api.catalogue.Place.Id, com.histmersion.api.Api.Error, List[Place.Link], Any] =
    (base.get
      .in("places" / path[String]("id") / "links")
      .out(jsonBody[List[Place.Link]])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[_root_.com.histmersion.api.catalogue.Place.Id]((refactorDecoded49: _root_.java.lang.String) => _root_.com.histmersion.api.catalogue.Place.Id(refactorDecoded49))((refactorEncoded49: _root_.com.histmersion.api.catalogue.Place.Id) => (refactorEncoded49).value)

  lazy val periods: PublicEndpoint[
    (Option[com.histmersion.api.instance.CorpusCounts.Periods.Source], Option[com.histmersion.api.instance.CorpusCounts.Periods.Collection], Option[com.histmersion.api.Api.PlacesGeoQuery.From], Option[com.histmersion.api.Api.PlacesGeoQuery.To]),
    com.histmersion.api.Api.Error,
    List[
      HistoricalPeriod
    ],
    Any
  ] =
    (base.get
      .in("periods")
      .in(query[Option[String]]("q"))
      .in(query[Option[String]]("region"))
      .in(query[Option[Long]]("from"))
      .in(query[Option[Long]]("to"))
      .out(jsonBody[List[HistoricalPeriod]])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source], _root_.scala.Option[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection], _root_.scala.Option[_root_.com.histmersion.api.Api.PlacesGeoQuery.From], _root_.scala.Option[_root_.com.histmersion.api.Api.PlacesGeoQuery.To])]((refactorDecoded156: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Long], _root_.scala.Option[_root_.scala.Long])) => (((refactorDecoded156)._1).map(refactorElement139 => _root_.com.histmersion.api.instance.CorpusCounts.Periods.Source(refactorElement139)), ((refactorDecoded156)._2).map(refactorElement140 => _root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection(refactorElement140)), ((refactorDecoded156)._3).map(refactorElement141 => _root_.com.histmersion.api.Api.PlacesGeoQuery.From(refactorElement141)), ((refactorDecoded156)._4).map(refactorElement142 => _root_.com.histmersion.api.Api.PlacesGeoQuery.To(refactorElement142))))((refactorEncoded156: (_root_.scala.Option[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source], _root_.scala.Option[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection], _root_.scala.Option[_root_.com.histmersion.api.Api.PlacesGeoQuery.From], _root_.scala.Option[_root_.com.histmersion.api.Api.PlacesGeoQuery.To])) => (((refactorEncoded156)._1).map(refactorElement143 => (refactorElement143).value), ((refactorEncoded156)._2).map(refactorElement144 => (refactorElement144).value), ((refactorEncoded156)._3).map(refactorElement145 => (refactorElement145).value), ((refactorEncoded156)._4).map(refactorElement146 => (refactorElement146).value)))

  lazy val period: PublicEndpoint[com.histmersion.api.catalogue.Period.Id, com.histmersion.api.Api.Error, Period.Detail, Any] =
    (base.get
      .in("periods" / path[String]("id"))
      .out(jsonBody[Period.Detail])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[_root_.com.histmersion.api.catalogue.Period.Id]((refactorDecoded157: _root_.java.lang.String) => _root_.com.histmersion.api.catalogue.Period.Id(refactorDecoded157))((refactorEncoded157: _root_.com.histmersion.api.catalogue.Period.Id) => (refactorEncoded157).value)

  lazy val seedPeriods: PublicEndpoint[com.histmersion.api.Api.Overwrite, com.histmersion.api.Api.Error, Period.ImportResult, Any] =
    (writeBase.post
      .in("periods" / "seed")
      .in(query[Boolean]("replace").default(false))
      .out(jsonBody[Period.ImportResult])).mapIn[_root_.com.histmersion.api.Api.Overwrite]((refactorDecoded158: _root_.scala.Boolean) => _root_.com.histmersion.api.Api.Overwrite(refactorDecoded158))((refactorEncoded158: _root_.com.histmersion.api.Api.Overwrite) => (refactorEncoded158).value)

  lazy val importPeriods: PublicEndpoint[
    (List[com.histmersion.api.instance.CorpusCounts.Periods.Source], Option[com.histmersion.api.instance.CorpusCounts.Periods.Collection], com.histmersion.api.instance.CorpusCounts.Periods.Name),
    com.histmersion.api.Api.Error,
    Period.ImportResult,
    Any
  ] =
    (writeBase.post
      .in("periods" / "import")
      .in(query[List[String]]("collection"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[Period.ImportResult])).mapIn[(_root_.scala.List[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source], _root_.scala.Option[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection], _root_.com.histmersion.api.instance.CorpusCounts.Periods.Name)]((refactorDecoded53: (_root_.scala.List[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.java.lang.String)) => (((refactorDecoded53)._1).map(refactorElement55 => _root_.com.histmersion.api.instance.CorpusCounts.Periods.Source(refactorElement55)), ((refactorDecoded53)._2).map(refactorElement56 => _root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection(refactorElement56)), _root_.com.histmersion.api.instance.CorpusCounts.Periods.Name((refactorDecoded53)._3)))((refactorEncoded53: (_root_.scala.List[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source], _root_.scala.Option[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection], _root_.com.histmersion.api.instance.CorpusCounts.Periods.Name)) => (((refactorEncoded53)._1).map(refactorElement57 => (refactorElement57).value), ((refactorEncoded53)._2).map(refactorElement58 => (refactorElement58).value), ((refactorEncoded53)._3).value))

  lazy val fetchPeriods
      : PublicEndpoint[(com.histmersion.api.instance.CorpusCounts.Periods.Source, List[com.histmersion.api.instance.CorpusCounts.Periods.Collection]), com.histmersion.api.Api.Error, Period.ImportResult, Any] =
    (writeBase.post
      .in("periods" / "fetch")
      .in(query[String]("source"))
      .in(query[List[String]]("collection"))
      .out(jsonBody[Period.ImportResult])).mapIn[(_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source, _root_.scala.List[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection])]((refactorDecoded160: (_root_.java.lang.String, _root_.scala.List[_root_.java.lang.String])) => (_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source((refactorDecoded160)._1), ((refactorDecoded160)._2).map(refactorElement151 => _root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection(refactorElement151))))((refactorEncoded160: (_root_.com.histmersion.api.instance.CorpusCounts.Periods.Source, _root_.scala.List[_root_.com.histmersion.api.instance.CorpusCounts.Periods.Collection])) => (((refactorEncoded160)._1).value, ((refactorEncoded160)._2).map(refactorElement152 => (refactorElement152).value)))

  lazy val init: PublicEndpoint[com.histmersion.api.Api.Overwrite, com.histmersion.api.Api.Error, JobInfo, Any] =
    (writeBase.post.in("init").in(query[Boolean]("fetch").default(false)).out(jsonBody[JobInfo])).mapIn[_root_.com.histmersion.api.Api.Overwrite]((refactorDecoded161: _root_.scala.Boolean) => _root_.com.histmersion.api.Api.Overwrite(refactorDecoded161))((refactorEncoded161: _root_.com.histmersion.api.Api.Overwrite) => (refactorEncoded161).value)

  lazy val importPlaces: PublicEndpoint[
    (Option[com.histmersion.api.Api.ExternalPlaces.Source], Option[com.histmersion.api.Api.ExternalPlaces.Locator], com.histmersion.api.Api.ExternalPlaces.Format),
    com.histmersion.api.Api.Error,
    Place.ImportResult,
    Any
  ] =
    (writeBase.post
      .in("places" / "import")
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[Place.ImportResult])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.Api.ExternalPlaces.Source], _root_.scala.Option[_root_.com.histmersion.api.Api.ExternalPlaces.Locator], _root_.com.histmersion.api.Api.ExternalPlaces.Format)]((refactorDecoded162: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.java.lang.String)) => (((refactorDecoded162)._1).map(refactorElement153 => _root_.com.histmersion.api.Api.ExternalPlaces.Source(refactorElement153)), ((refactorDecoded162)._2).map(refactorElement154 => _root_.com.histmersion.api.Api.ExternalPlaces.Locator(refactorElement154)), _root_.com.histmersion.api.Api.ExternalPlaces.Format((refactorDecoded162)._3)))((refactorEncoded162: (_root_.scala.Option[_root_.com.histmersion.api.Api.ExternalPlaces.Source], _root_.scala.Option[_root_.com.histmersion.api.Api.ExternalPlaces.Locator], _root_.com.histmersion.api.Api.ExternalPlaces.Format)) => (((refactorEncoded162)._1).map(refactorElement155 => (refactorElement155).value), ((refactorEncoded162)._2).map(refactorElement156 => (refactorElement156).value), ((refactorEncoded162)._3).value))

  lazy val rebuildPlaces: PublicEndpoint[Unit, com.histmersion.api.Api.Error, Place.RebuildResult, Any] =
    writeBase.post.in("places" / "rebuild").out(jsonBody[Place.RebuildResult])

  lazy val fetchExternalPlaces
      : PublicEndpoint[(com.histmersion.api.Api.ExternalPlaces.Source, com.histmersion.api.Api.Apply), com.histmersion.api.Api.Error, Place.ImportResult, Any] =
    (writeBase.post
      .in("places" / "fetch")
      .in(query[String]("source"))
      .in(query[Boolean]("dryRun"))
      .out(jsonBody[Place.ImportResult])).mapIn[(_root_.com.histmersion.api.Api.ExternalPlaces.Source, _root_.com.histmersion.api.Api.Apply)]((refactorDecoded57: (_root_.java.lang.String, _root_.scala.Boolean)) => (_root_.com.histmersion.api.Api.ExternalPlaces.Source((refactorDecoded57)._1), _root_.com.histmersion.api.Api.Apply((refactorDecoded57)._2)))((refactorEncoded57: (_root_.com.histmersion.api.Api.ExternalPlaces.Source, _root_.com.histmersion.api.Api.Apply)) => (((refactorEncoded57)._1).value, ((refactorEncoded57)._2).value))

  /** Declared before `places/{id}` so `sources` is never read as an id. */
  lazy val placeSources: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[Gazetteer.SourceInfo], Any] =
    base.get.in("places" / "sources").out(jsonBody[List[Gazetteer.SourceInfo]])

  lazy val loadPlaces: PublicEndpoint[(com.histmersion.api.Api.ExternalPlaces.Source, com.histmersion.api.Api.Apply), com.histmersion.api.Api.Error, JobInfo, Any] =
    (writeBase.post
      .in("places" / "load")
      .in(query[String]("source"))
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[JobInfo])).mapIn[(_root_.com.histmersion.api.Api.ExternalPlaces.Source, _root_.com.histmersion.api.Api.Apply)]((refactorDecoded58: (_root_.java.lang.String, _root_.scala.Boolean)) => (_root_.com.histmersion.api.Api.ExternalPlaces.Source((refactorDecoded58)._1), _root_.com.histmersion.api.Api.Apply((refactorDecoded58)._2)))((refactorEncoded58: (_root_.com.histmersion.api.Api.ExternalPlaces.Source, _root_.com.histmersion.api.Api.Apply)) => (((refactorEncoded58)._1).value, ((refactorEncoded58)._2).value))

  /** Declared before `places/{id}` so `names` is never read as an id. */
  lazy val placeNames: PublicEndpoint[Option[com.histmersion.api.catalogue.Place.Id], com.histmersion.api.Api.Error, List[ManualPlaceName], Any] =
    (base.get.in("places" / "names").in(query[Option[String]]("place")).out(jsonBody[List[ManualPlaceName]])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Id]]((refactorDecoded59: _root_.scala.Option[_root_.java.lang.String]) => (refactorDecoded59).map(refactorElement65 => _root_.com.histmersion.api.catalogue.Place.Id(refactorElement65)))((refactorEncoded59: _root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Id]) => (refactorEncoded59).map(refactorElement66 => (refactorElement66).value))

  lazy val addPlaceName
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Place.NameAdd), com.histmersion.api.Api.Error, Place.NameChanged, Any] =
    (writeBase.post
      .in("places" / path[String]("id") / "names")
      .in(jsonBody[Place.NameAdd])
      .out(jsonBody[Place.NameChanged])).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.NameAdd)]((refactorDecoded60: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.Place.NameAdd)) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded60)._1), (refactorDecoded60)._2))((refactorEncoded60: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.NameAdd)) => (((refactorEncoded60)._1).value, (refactorEncoded60)._2))

  lazy val removePlaceName
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Place.NameRemove), com.histmersion.api.Api.Error, Place.NameChanged, Any] =
    (writeBase.post
      .in("places" / path[String]("id") / "names" / "remove")
      .in(jsonBody[Place.NameRemove])
      .out(jsonBody[Place.NameChanged])).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.NameRemove)]((refactorDecoded167: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.Place.NameRemove)) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded167)._1), (refactorDecoded167)._2))((refactorEncoded167: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.NameRemove)) => (((refactorEncoded167)._1).value, (refactorEncoded167)._2))

  /** Declared before `places/{id}` so `overrides` is never read as an id. */
  lazy val placeOverrides: PublicEndpoint[Option[com.histmersion.api.catalogue.Place.Id], com.histmersion.api.Api.Error, List[Place.Override], Any] =
    (base.get.in("places" / "overrides").in(query[Option[String]]("place")).out(jsonBody[List[Place.Override]])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Id]]((refactorDecoded168: _root_.scala.Option[_root_.java.lang.String]) => (refactorDecoded168).map(refactorElement159 => _root_.com.histmersion.api.catalogue.Place.Id(refactorElement159)))((refactorEncoded168: _root_.scala.Option[_root_.com.histmersion.api.catalogue.Place.Id]) => (refactorEncoded168).map(refactorElement160 => (refactorElement160).value))

  lazy val setPlaceOverride
      : PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Place.OverrideSet), com.histmersion.api.Api.Error, Place.OverrideChanged, Any] =
    (writeBase.post
      .in("places" / path[String]("id") / "override")
      .in(jsonBody[Place.OverrideSet])
      .out(jsonBody[Place.OverrideChanged])).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.OverrideSet)]((refactorDecoded169: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.Place.OverrideSet)) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded169)._1), (refactorDecoded169)._2))((refactorEncoded169: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.OverrideSet)) => (((refactorEncoded169)._1).value, (refactorEncoded169)._2))

  lazy val mergePlace: PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Place.MergeIn), com.histmersion.api.Api.Error, Place.Gone, Any] =
    (writeBase.post.in("places" / path[String]("id") / "merge").in(jsonBody[Place.MergeIn]).out(jsonBody[Place.Gone])).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.MergeIn)]((refactorDecoded64: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.Place.MergeIn)) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded64)._1), (refactorDecoded64)._2))((refactorEncoded64: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.MergeIn)) => (((refactorEncoded64)._1).value, (refactorEncoded64)._2))

  lazy val removePlace: PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, Place.RemoveIn), com.histmersion.api.Api.Error, Place.Gone, Any] =
    (writeBase.post.in("places" / path[String]("id") / "remove").in(jsonBody[Place.RemoveIn]).out(jsonBody[Place.Gone])).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.RemoveIn)]((refactorDecoded171: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.Place.RemoveIn)) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded171)._1), (refactorDecoded171)._2))((refactorEncoded171: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.catalogue.Place.RemoveIn)) => (((refactorEncoded171)._1).value, (refactorEncoded171)._2))

  lazy val addPlace: PublicEndpoint[Place.Add, com.histmersion.api.Api.Error, Place.Summary, Any] =
    writeBase.post.in("places").in(jsonBody[Place.Add]).out(jsonBody[Place.Summary])

  lazy val buildHierarchy: PublicEndpoint[Unit, com.histmersion.api.Api.Error, SpatialHierarchyResult, Any] =
    writeBase.post.in("places" / "build-hierarchy").out(jsonBody[SpatialHierarchyResult])

  final case class EventQuery(
      filter: Filter = Filter(),
      text: Option[EventQuery.Text] = None,
      kind: Option[EventQuery.Kind] = None,
      historicity: Option[EventQuery.Historicity] = None,
      placeId: Option[EventQuery.PlaceId] = None,
      tier: Option[EventQuery.Tier] = None,
      context: Option[EventQuery.Context] = None,
      area: Option[EventQuery.Area] = None
  )

  object EventQuery:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tier = Tier.T
    object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Area = Area.T
    object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val events: PublicEndpoint[EventQuery, com.histmersion.api.Api.Error, EventList, Any] =
    base.get
      .in("events")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("area"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventList])

  lazy val eventsGeo: PublicEndpoint[EventQuery, com.histmersion.api.Api.Error, EventGeo.List, Any] =
    base.get
      .in("events" / "geo")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("area"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventGeo.List])

  lazy val renameEvent: PublicEndpoint[(com.histmersion.api.catalogue.Event.Uid, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    (writeBase.post.in("events" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])).mapIn[(_root_.com.histmersion.api.catalogue.Event.Uid, _root_.com.histmersion.api.aliases.LabelRename)]((refactorDecoded66: (_root_.java.lang.String, _root_.com.histmersion.api.aliases.LabelRename)) => (_root_.com.histmersion.api.catalogue.Event.Uid((refactorDecoded66)._1), (refactorDecoded66)._2))((refactorEncoded66: (_root_.com.histmersion.api.catalogue.Event.Uid, _root_.com.histmersion.api.aliases.LabelRename)) => (((refactorEncoded66)._1).value, (refactorEncoded66)._2))

  lazy val getEvent: PublicEndpoint[com.histmersion.api.catalogue.Event.Uid, com.histmersion.api.Api.Error, EventDetail, Any] =
    (endpoint.get
      .in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])).mapIn[_root_.com.histmersion.api.catalogue.Event.Uid]((refactorDecoded67: _root_.java.lang.String) => _root_.com.histmersion.api.catalogue.Event.Uid(refactorDecoded67))((refactorEncoded67: _root_.com.histmersion.api.catalogue.Event.Uid) => (refactorEncoded67).value)

  lazy val putEvent: PublicEndpoint[(com.histmersion.api.catalogue.Event.Uid, EventPut), com.histmersion.api.Api.Error, EventDetail, Any] =
    (endpoint.put
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPut])
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])).mapIn[(_root_.com.histmersion.api.catalogue.Event.Uid, _root_.com.histmersion.api.catalogue.EventPut)]((refactorDecoded174: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.EventPut)) => (_root_.com.histmersion.api.catalogue.Event.Uid((refactorDecoded174)._1), (refactorDecoded174)._2))((refactorEncoded174: (_root_.com.histmersion.api.catalogue.Event.Uid, _root_.com.histmersion.api.catalogue.EventPut)) => (((refactorEncoded174)._1).value, (refactorEncoded174)._2))

  lazy val patchEvent: PublicEndpoint[(com.histmersion.api.catalogue.Event.Uid, EventPatch), com.histmersion.api.Api.Error, EventDetail, Any] =
    (endpoint.patch
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPatch])
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])).mapIn[(_root_.com.histmersion.api.catalogue.Event.Uid, _root_.com.histmersion.api.catalogue.EventPatch)]((refactorDecoded175: (_root_.java.lang.String, _root_.com.histmersion.api.catalogue.EventPatch)) => (_root_.com.histmersion.api.catalogue.Event.Uid((refactorDecoded175)._1), (refactorDecoded175)._2))((refactorEncoded175: (_root_.com.histmersion.api.catalogue.Event.Uid, _root_.com.histmersion.api.catalogue.EventPatch)) => (((refactorEncoded175)._1).value, (refactorEncoded175)._2))

  lazy val deleteEvent: PublicEndpoint[com.histmersion.api.catalogue.Event.Uid, com.histmersion.api.Api.Error, EventDeleted, Any] =
    (endpoint.delete
      .in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[EventDeleted])).mapIn[_root_.com.histmersion.api.catalogue.Event.Uid]((refactorDecoded176: _root_.java.lang.String) => _root_.com.histmersion.api.catalogue.Event.Uid(refactorDecoded176))((refactorEncoded176: _root_.com.histmersion.api.catalogue.Event.Uid) => (refactorEncoded176).value)

  final case class AttestationQuery(
      citekey: Option[com.histmersion.api.library.Citekey] = None,
      targetType: Option[AttestationQuery.TargetType] = None,
      targetId: Option[AttestationQuery.TargetId] = None,
      limit: Option[AttestationQuery.Limit] = None,
      offset: Option[AttestationQuery.Offset] = None
  )

  object AttestationQuery:
    type TargetType = TargetType.T
    object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val attestations: PublicEndpoint[AttestationQuery, com.histmersion.api.Api.Error, Attestation.List, Any] =
    base.get
      .in("attestations")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("targetType"))
      .in(query[Option[String]]("targetId"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[AttestationQuery]
      .out(jsonBody[Attestation.List])

  lazy val getAttestation: PublicEndpoint[com.histmersion.api.catalogue.Attestation.Id, com.histmersion.api.Api.Error, Attestation, Any] =
    (endpoint.get
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])).mapIn[_root_.com.histmersion.api.catalogue.Attestation.Id]((refactorDecoded71: _root_.scala.Long) => _root_.com.histmersion.api.catalogue.Attestation.Id(refactorDecoded71))((refactorEncoded71: _root_.com.histmersion.api.catalogue.Attestation.Id) => (refactorEncoded71).value)

  lazy val createAttestation: PublicEndpoint[Attestation.Put, com.histmersion.api.Api.Error, Attestation, Any] =
    endpoint.post
      .in("api" / "attestations")
      .in(jsonBody[Attestation.Put])
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val patchAttestation
      : PublicEndpoint[(com.histmersion.api.catalogue.Attestation.Id, Attestation.Patch), com.histmersion.api.Api.Error, Attestation, Any] =
    (endpoint.patch
      .in("api" / "attestations" / path[Long]("id"))
      .in(jsonBody[Attestation.Patch])
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])).mapIn[(_root_.com.histmersion.api.catalogue.Attestation.Id, _root_.com.histmersion.api.catalogue.Attestation.Patch)]((refactorDecoded72: (_root_.scala.Long, _root_.com.histmersion.api.catalogue.Attestation.Patch)) => (_root_.com.histmersion.api.catalogue.Attestation.Id((refactorDecoded72)._1), (refactorDecoded72)._2))((refactorEncoded72: (_root_.com.histmersion.api.catalogue.Attestation.Id, _root_.com.histmersion.api.catalogue.Attestation.Patch)) => (((refactorEncoded72)._1).value, (refactorEncoded72)._2))

  lazy val deleteAttestation: PublicEndpoint[com.histmersion.api.catalogue.Attestation.Id, com.histmersion.api.Api.Error, Attestation.Deleted, Any] =
    (endpoint.delete
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[Attestation.Deleted])).mapIn[_root_.com.histmersion.api.catalogue.Attestation.Id]((refactorDecoded179: _root_.scala.Long) => _root_.com.histmersion.api.catalogue.Attestation.Id(refactorDecoded179))((refactorEncoded179: _root_.com.histmersion.api.catalogue.Attestation.Id) => (refactorEncoded179).value)

  final case class GoalQuery(
      status: Option[GoalQuery.Status] = None,
      limit: Option[GoalQuery.Limit] = None,
      offset: Option[GoalQuery.Offset] = None
  )

  object GoalQuery:
    object Status:
      type Wire = Wire.T
      object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val goals: PublicEndpoint[GoalQuery, com.histmersion.api.Api.Error, Goal.List, Any] =
    base.get
      .in("goals")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[GoalQuery]
      .out(jsonBody[Goal.List])

  lazy val renameGoal: PublicEndpoint[(com.histmersion.api.automation.Goal.Uid, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    (writeBase.post.in("goals" / path[String]("slug") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])).mapIn[(_root_.com.histmersion.api.automation.Goal.Uid, _root_.com.histmersion.api.aliases.LabelRename)]((refactorDecoded180: (_root_.java.lang.String, _root_.com.histmersion.api.aliases.LabelRename)) => (_root_.com.histmersion.api.automation.Goal.Uid((refactorDecoded180)._1), (refactorDecoded180)._2))((refactorEncoded180: (_root_.com.histmersion.api.automation.Goal.Uid, _root_.com.histmersion.api.aliases.LabelRename)) => (((refactorEncoded180)._1).value, (refactorEncoded180)._2))

  lazy val renamePlace: PublicEndpoint[(com.histmersion.api.catalogue.Place.Id, LabelRename), com.histmersion.api.Api.Error, LabelRenamed, Any] =
    (writeBase.post.in("places" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])).mapIn[(_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.aliases.LabelRename)]((refactorDecoded181: (_root_.java.lang.String, _root_.com.histmersion.api.aliases.LabelRename)) => (_root_.com.histmersion.api.catalogue.Place.Id((refactorDecoded181)._1), (refactorDecoded181)._2))((refactorEncoded181: (_root_.com.histmersion.api.catalogue.Place.Id, _root_.com.histmersion.api.aliases.LabelRename)) => (((refactorEncoded181)._1).value, (refactorEncoded181)._2))

  lazy val getGoal: PublicEndpoint[com.histmersion.api.automation.Goal.Uid, com.histmersion.api.Api.Error, Goal, Any] =
    (endpoint.get
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Goal])).mapIn[_root_.com.histmersion.api.automation.Goal.Uid]((refactorDecoded182: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Goal.Uid(refactorDecoded182))((refactorEncoded182: _root_.com.histmersion.api.automation.Goal.Uid) => (refactorEncoded182).value)

  lazy val putGoal: PublicEndpoint[(com.histmersion.api.automation.Goal.Uid, Goal.Put), com.histmersion.api.Api.Error, Goal, Any] =
    (endpoint.put
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[Goal.Put])
      .errorOut(writeErrors)
      .out(jsonBody[Goal])).mapIn[(_root_.com.histmersion.api.automation.Goal.Uid, _root_.com.histmersion.api.automation.Goal.Put)]((refactorDecoded183: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Goal.Put)) => (_root_.com.histmersion.api.automation.Goal.Uid((refactorDecoded183)._1), (refactorDecoded183)._2))((refactorEncoded183: (_root_.com.histmersion.api.automation.Goal.Uid, _root_.com.histmersion.api.automation.Goal.Put)) => (((refactorEncoded183)._1).value, (refactorEncoded183)._2))

  lazy val patchGoal: PublicEndpoint[(com.histmersion.api.automation.Goal.Uid, Goal.Patch), com.histmersion.api.Api.Error, Goal, Any] =
    (endpoint.patch
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[Goal.Patch])
      .errorOut(writeErrors)
      .out(jsonBody[Goal])).mapIn[(_root_.com.histmersion.api.automation.Goal.Uid, _root_.com.histmersion.api.automation.Goal.Patch)]((refactorDecoded78: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Goal.Patch)) => (_root_.com.histmersion.api.automation.Goal.Uid((refactorDecoded78)._1), (refactorDecoded78)._2))((refactorEncoded78: (_root_.com.histmersion.api.automation.Goal.Uid, _root_.com.histmersion.api.automation.Goal.Patch)) => (((refactorEncoded78)._1).value, (refactorEncoded78)._2))

  lazy val deleteGoal: PublicEndpoint[com.histmersion.api.automation.Goal.Uid, com.histmersion.api.Api.Error, Goal.Deleted, Any] =
    (endpoint.delete
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Goal.Deleted])).mapIn[_root_.com.histmersion.api.automation.Goal.Uid]((refactorDecoded185: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Goal.Uid(refactorDecoded185))((refactorEncoded185: _root_.com.histmersion.api.automation.Goal.Uid) => (refactorEncoded185).value)

  lazy val agentStateList: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[AgentStateEntry], Any] =
    base.get.in("agent-state").out(jsonBody[List[AgentStateEntry]])

  lazy val agentStateGet: PublicEndpoint[com.histmersion.api.Api.AgentState.Key, com.histmersion.api.Api.Error, AgentStateEntry, Any] =
    (endpoint.get
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateEntry])).mapIn[_root_.com.histmersion.api.Api.AgentState.Key]((refactorDecoded80: _root_.java.lang.String) => _root_.com.histmersion.api.Api.AgentState.Key(refactorDecoded80))((refactorEncoded80: _root_.com.histmersion.api.Api.AgentState.Key) => (refactorEncoded80).value)

  lazy val agentStateSet: PublicEndpoint[(com.histmersion.api.Api.AgentState.Key, AgentStatePut), com.histmersion.api.Api.Error, AgentStateEntry, Any] =
    (endpoint.put
      .in("api" / "agent-state" / path[String]("key"))
      .in(jsonBody[AgentStatePut])
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateEntry])).mapIn[(_root_.com.histmersion.api.Api.AgentState.Key, _root_.com.histmersion.api.automation.AgentStatePut)]((refactorDecoded187: (_root_.java.lang.String, _root_.com.histmersion.api.automation.AgentStatePut)) => (_root_.com.histmersion.api.Api.AgentState.Key((refactorDecoded187)._1), (refactorDecoded187)._2))((refactorEncoded187: (_root_.com.histmersion.api.Api.AgentState.Key, _root_.com.histmersion.api.automation.AgentStatePut)) => (((refactorEncoded187)._1).value, (refactorEncoded187)._2))

  lazy val agentStateDelete: PublicEndpoint[com.histmersion.api.Api.AgentState.Key, com.histmersion.api.Api.Error, AgentStateDeleted, Any] =
    (endpoint.delete
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateDeleted])).mapIn[_root_.com.histmersion.api.Api.AgentState.Key]((refactorDecoded82: _root_.java.lang.String) => _root_.com.histmersion.api.Api.AgentState.Key(refactorDecoded82))((refactorEncoded82: _root_.com.histmersion.api.Api.AgentState.Key) => (refactorEncoded82).value)

  private lazy val ingestErrors =
    oneOf[com.histmersion.api.Api.Error](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[com.histmersion.api.Api.Error])(
        (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Ingest.CodeConflict))).andThen(refactorResult4 => ((refactorResult4) == (true)))
      ),
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[com.histmersion.api.Api.Error])(
        (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound))).andThen(refactorResult5 => ((refactorResult5) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[com.histmersion.api.Api.Error]))
    )

  /** Query parameters, not a multipart part list: tapir's netty server has no multipart request-body support
    * (`RawBodyType.MultipartBody` throws `UnsupportedOperationException`), so the document travels as the raw body.
    */
  lazy val ingestParams: EndpointInput[Ingest.Params] =
    query[String]("citekey")
      .and(query[Option[String]]("filename"))
      .and(query[Boolean]("quality").default(false))
      .and(query[Boolean]("force").default(false))
      .and(query[Option[String]]("title"))
      .and(query[List[String]]("author"))
      .and(query[Option[Int]]("year"))
      .and(query[Option[String]]("doi"))
      .and(query[Option[String]]("provenance"))
      .and(
        query[Option[Int]]("acquireItem")
          .description("id of the acquisition item this upload fulfils; the OCR job ticks it off when it succeeds")
      )
      .mapTo[Ingest.Params]

  lazy val ingest: PublicEndpoint[(Ingest.Params, TapirFile), com.histmersion.api.Api.Error, Ingest.Accepted, Any] =
    endpoint.post
      .in("api" / "ingest")
      .in(ingestParams)
      .in(fileBody)
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Ingest.Accepted])

  lazy val reocr: PublicEndpoint[(com.histmersion.api.library.Citekey, com.histmersion.api.Api.Overwrite), com.histmersion.api.Api.Error, Ingest.Accepted, Any] =
    (endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "reocr")
      .in(query[Boolean]("quality").default(false))
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Ingest.Accepted])).mapIn[(_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.Api.Overwrite)]((refactorDecoded189: (_root_.java.lang.String, _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.Citekey((refactorDecoded189)._1), _root_.com.histmersion.api.Api.Overwrite((refactorDecoded189)._2)))((refactorEncoded189: (_root_.com.histmersion.api.library.Citekey, _root_.com.histmersion.api.Api.Overwrite)) => (((refactorEncoded189)._1).value, ((refactorEncoded189)._2).value))

  lazy val original: PublicEndpoint[com.histmersion.api.library.Citekey, com.histmersion.api.Api.Error, (TapirFile, com.histmersion.api.Api.FileName), Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "original")
      .errorOut(ingestErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  lazy val job: PublicEndpoint[com.histmersion.api.SpatialHierarchyResult.Job.Id, com.histmersion.api.Api.Error, JobInfo, Any] =
    (base.get
      .in("jobs" / path[String]("id"))
      .out(jsonBody[JobInfo])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[_root_.com.histmersion.api.SpatialHierarchyResult.Job.Id]((refactorDecoded192: _root_.java.lang.String) => _root_.com.histmersion.api.SpatialHierarchyResult.Job.Id(refactorDecoded192))((refactorEncoded192: _root_.com.histmersion.api.SpatialHierarchyResult.Job.Id) => (refactorEncoded192).value)

  lazy val jobControl: PublicEndpoint[(com.histmersion.api.SpatialHierarchyResult.Job.Id, com.histmersion.api.Api.JobControl.Action), com.histmersion.api.Api.Error, JobInfo, Any] =
    endpoint.post
      .in("api" / "jobs" / path[String]("id") / path[String]("action"))
      .errorOut(
        oneOf[com.histmersion.api.Api.Error](
          oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[com.histmersion.api.Api.Error])(
            (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound))).andThen(refactorResult6 => ((refactorResult6) == (true)))
          ),
          oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[com.histmersion.api.Api.Error])(
            (com.histmersion.api.Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(JobActions.CodeConflict))).andThen(refactorResult9 => ((refactorResult9) == (true)))
          ),
          oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[com.histmersion.api.Api.Error]))
        )
      )
      .out(jsonBody[JobInfo])

  lazy val activity: PublicEndpoint[
    (Option[com.histmersion.api.automation.Activity.Kind], Option[com.histmersion.api.automation.Activity.Actor], Option[com.histmersion.api.automation.Activity.Subject], Option[com.histmersion.api.automation.Activity.Limit], Option[com.histmersion.api.automation.Activity.Cursor]),
    com.histmersion.api.Api.Error,
    Activity.Page,
    Any
  ] =
    (base.get
      .in("activity")
      .in(
        query[Option[String]]("show").description(
          s"one of: ${Activity.Show.values.map(_.wire).mkString(", ")} (default all)"
        )
      )
      .in(query[Option[String]]("since").description("from: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago"))
      .in(query[Option[String]]("goal").description("only what concerns this research goal (its slug)"))
      .in(
        query[Option[Int]]("limit")
          .description(s"entries per page (default ${Activity.DefaultLimit}, at most ${Activity.MaxLimit})")
      )
      .in(query[Option[String]]("before").description("the next older page: the cursor the last page gave"))
      .out(jsonBody[Activity.Page])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Kind], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Actor], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Subject], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Limit], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Cursor])]((refactorDecoded193: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String])) => (((refactorDecoded193)._1).map(refactorElement161 => _root_.com.histmersion.api.automation.Activity.Kind(refactorElement161)), ((refactorDecoded193)._2).map(refactorElement162 => _root_.com.histmersion.api.automation.Activity.Actor(refactorElement162)), ((refactorDecoded193)._3).map(refactorElement163 => _root_.com.histmersion.api.automation.Activity.Subject(refactorElement163)), ((refactorDecoded193)._4).map(refactorElement164 => _root_.com.histmersion.api.automation.Activity.Limit(refactorElement164)), ((refactorDecoded193)._5).map(refactorElement165 => _root_.com.histmersion.api.automation.Activity.Cursor(refactorElement165))))((refactorEncoded193: (_root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Kind], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Actor], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Subject], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Limit], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Cursor])) => (((refactorEncoded193)._1).map(refactorElement166 => (refactorElement166).value), ((refactorEncoded193)._2).map(refactorElement167 => (refactorElement167).value), ((refactorEncoded193)._3).map(refactorElement168 => (refactorElement168).value), ((refactorEncoded193)._4).map(refactorElement169 => (refactorElement169).value), ((refactorEncoded193)._5).map(refactorElement170 => (refactorElement170).value)))

  lazy val jobs: PublicEndpoint[
    (ListFilters.JobFilter, Option[com.histmersion.api.Api.Jobs.Limit], Option[com.histmersion.api.Api.Jobs.Status], Option[com.histmersion.api.Jobs.Filter]),
    com.histmersion.api.Api.Error,
    (List[JobInfo], Paging.Page),
    Any
  ] =
    (base.get
      .in("jobs")
      .in(ListFilters.jobQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .out(jsonBody[List[JobInfo]])
      .out(Paging.headers)).mapIn[(_root_.com.histmersion.api.automation.ListFilters.JobFilter, _root_.scala.Option[_root_.com.histmersion.api.Api.Jobs.Limit], _root_.scala.Option[_root_.com.histmersion.api.Api.Jobs.Status], _root_.scala.Option[_root_.com.histmersion.api.Jobs.Filter])]((refactorDecoded194: (_root_.com.histmersion.api.automation.ListFilters.JobFilter, _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => ((refactorDecoded194)._1, ((refactorDecoded194)._2).map(refactorElement171 => _root_.com.histmersion.api.Api.Jobs.Limit(refactorElement171)), ((refactorDecoded194)._3).map(refactorElement172 => _root_.com.histmersion.api.Api.Jobs.Status(refactorElement172)), ((refactorDecoded194)._4).map(refactorElement173 => _root_.com.histmersion.api.Jobs.Filter(refactorElement173))))((refactorEncoded194: (_root_.com.histmersion.api.automation.ListFilters.JobFilter, _root_.scala.Option[_root_.com.histmersion.api.Api.Jobs.Limit], _root_.scala.Option[_root_.com.histmersion.api.Api.Jobs.Status], _root_.scala.Option[_root_.com.histmersion.api.Jobs.Filter])) => ((refactorEncoded194)._1, ((refactorEncoded194)._2).map(refactorElement174 => (refactorElement174).value), ((refactorEncoded194)._3).map(refactorElement175 => (refactorElement175).value), ((refactorEncoded194)._4).map(refactorElement176 => (refactorElement176).value)))

  lazy val assets: PublicEndpoint[com.histmersion.api.Api.Assets.Kind, com.histmersion.api.Api.Error, List[AssetInfo], Any] =
    (base.get.in("sources" / path[String]("citekey") / "assets").out(jsonBody[List[AssetInfo]])).mapIn[_root_.com.histmersion.api.Api.Assets.Kind]((refactorDecoded89: _root_.java.lang.String) => _root_.com.histmersion.api.Api.Assets.Kind(refactorDecoded89))((refactorEncoded89: _root_.com.histmersion.api.Api.Assets.Kind) => (refactorEncoded89).value)

  lazy val putAssets: PublicEndpoint[(com.histmersion.api.Api.Assets.Owner, List[AssetInput]), com.histmersion.api.Api.Error, AssetsStored, Any] =
    (endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "assets")
      .in(jsonBody[List[AssetInput]])
      .errorOut(ingestErrors)
      .out(jsonBody[AssetsStored])).mapIn[(_root_.com.histmersion.api.Api.Assets.Owner, _root_.scala.List[_root_.com.histmersion.api.media.AssetInput])]((refactorDecoded90: (_root_.java.lang.String, _root_.scala.List[_root_.com.histmersion.api.media.AssetInput])) => (_root_.com.histmersion.api.Api.Assets.Owner((refactorDecoded90)._1), (refactorDecoded90)._2))((refactorEncoded90: (_root_.com.histmersion.api.Api.Assets.Owner, _root_.scala.List[_root_.com.histmersion.api.media.AssetInput])) => (((refactorEncoded90)._1).value, (refactorEncoded90)._2))

  lazy val asset: PublicEndpoint[(com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.exchange.Bundle.Asset.Variant), com.histmersion.api.Api.Error, (com.histmersion.api.Api.FileBytes, com.histmersion.api.Api.ContentType), Any] =
    (endpoint.get
      .in("source" / path[String]("citekey") / "assets" / path[String]("name"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.AssetCacheControl)))).mapIn[(_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant)]((refactorDecoded91: (_root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid((refactorDecoded91)._1), _root_.com.histmersion.api.exchange.Bundle.Asset.Variant((refactorDecoded91)._2)))((refactorEncoded91: (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant)) => (((refactorEncoded91)._1).value, ((refactorEncoded91)._2).value)).mapOut[(_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)]((refactorDecoded92: (_root_.scala.Array[_root_.scala.Byte], _root_.java.lang.String)) => (_root_.com.histmersion.api.Api.FileBytes((refactorDecoded92)._1), _root_.com.histmersion.api.Api.ContentType((refactorDecoded92)._2)))((refactorEncoded92: (_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)) => (((refactorEncoded92)._1).value, ((refactorEncoded92)._2).value))

  lazy val assetDetail: PublicEndpoint[(com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.exchange.Bundle.Asset.Variant), com.histmersion.api.Api.Error, AssetDetail, Any] =
    (base.get
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name") / "detail")
      .out(jsonBody[AssetDetail])).mapIn[(_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant)]((refactorDecoded93: (_root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid((refactorDecoded93)._1), _root_.com.histmersion.api.exchange.Bundle.Asset.Variant((refactorDecoded93)._2)))((refactorEncoded93: (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant)) => (((refactorEncoded93)._1).value, ((refactorEncoded93)._2).value))

  lazy val patchAsset: PublicEndpoint[(com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.exchange.Bundle.Asset.Variant, AssetPatch), com.histmersion.api.Api.Error, AssetDetail, Any] =
    (base.patch
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name"))
      .in(jsonBody[AssetPatch])
      .out(jsonBody[AssetDetail])).mapIn[(_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant, _root_.com.histmersion.api.media.AssetPatch)]((refactorDecoded200: (_root_.java.lang.String, _root_.java.lang.String, _root_.com.histmersion.api.media.AssetPatch)) => (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid((refactorDecoded200)._1), _root_.com.histmersion.api.exchange.Bundle.Asset.Variant((refactorDecoded200)._2), (refactorDecoded200)._3))((refactorEncoded200: (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant, _root_.com.histmersion.api.media.AssetPatch)) => (((refactorEncoded200)._1).value, ((refactorEncoded200)._2).value, (refactorEncoded200)._3))

  final case class MediaGeoQuery(
      kind: Option[MediaGeoQuery.Kind] = None,
      from: Option[MediaGeoQuery.From] = None,
      to: Option[MediaGeoQuery.To] = None,
      limit: Option[MediaGeoQuery.Limit] = None,
      provider: Option[MediaGeoQuery.Provider] = None,
      license: Option[MediaGeoQuery.License] = None
  )

  object MediaGeoQuery:
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val mediaGeo: PublicEndpoint[MediaGeoQuery, com.histmersion.api.Api.Error, List[MediaGeoHit], Any] =
    base.get
      .in("media-geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[String]]("provider"))
          .and(query[Option[String]]("license"))
          .mapTo[MediaGeoQuery]
      )
      .out(jsonBody[List[MediaGeoHit]])

  final case class MediaQuery(
      provider: Option[MediaQuery.Provider] = None,
      license: Option[MediaQuery.License] = None,
      place: Option[MediaQuery.Place] = None,
      event: Option[MediaQuery.Event] = None,
      q: Option[MediaQuery.Q] = None,
      limit: Option[MediaQuery.Limit] = None,
      offset: Option[MediaQuery.Offset] = None
  )

  object MediaQuery:
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Q = Q.T
    object Q extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val mediaList: PublicEndpoint[MediaQuery, com.histmersion.api.Api.Error, MediaList, Any] =
    base.get
      .in("media")
      .in(
        query[Option[String]]("provider")
          .and(query[Option[String]]("license"))
          .and(query[Option[String]]("place"))
          .and(query[Option[String]]("event"))
          .and(query[Option[String]]("q"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Int]]("offset"))
          .mapTo[MediaQuery]
      )
      .out(jsonBody[MediaList])

  lazy val mediaFacets: PublicEndpoint[Unit, com.histmersion.api.Api.Error, MediaFacets, Any] =
    base.get.in("media" / "facets").out(jsonBody[MediaFacets])

  lazy val mediaProviders: PublicEndpoint[Unit, com.histmersion.api.Api.Error, List[MediaProvider.Info], Any] =
    base.get.in("media" / "providers").out(jsonBody[List[MediaProvider.Info]])

  lazy val mediaShow: PublicEndpoint[com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.Api.Error, MediaItem, Any] =
    (base.get
      .in("media" / path[String]("uid"))
      .out(jsonBody[MediaItem])
      .errorOut(statusCode(StatusCode.NotFound))).mapIn[_root_.com.histmersion.api.exchange.Bundle.Asset.Uid]((refactorDecoded95: _root_.java.lang.String) => _root_.com.histmersion.api.exchange.Bundle.Asset.Uid(refactorDecoded95))((refactorEncoded95: _root_.com.histmersion.api.exchange.Bundle.Asset.Uid) => (refactorEncoded95).value)

  lazy val mediaImport: PublicEndpoint[MediaImportRequest, com.histmersion.api.Api.Error, MediaImportReport, Any] =
    base.post.in("media" / "import").in(jsonBody[MediaImportRequest]).out(jsonBody[MediaImportReport])

  lazy val mediaImportJob: PublicEndpoint[MediaImportRequest, com.histmersion.api.Api.Error, JobInfo, Any] =
    base.post
      .in("media" / "import" / "job")
      .in(jsonBody[MediaImportRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val mediaCovers: PublicEndpoint[MediaCoversRequest, com.histmersion.api.Api.Error, MediaCoversReport, Any] =
    base.post.in("media" / "covers").in(jsonBody[MediaCoversRequest]).out(jsonBody[MediaCoversReport])

  lazy val mediaCoversJob: PublicEndpoint[MediaCoversRequest, com.histmersion.api.Api.Error, JobInfo, Any] =
    base.post
      .in("media" / "covers" / "job")
      .in(jsonBody[MediaCoversRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val putSourceCover
      : PublicEndpoint[(SourceCoverParams, com.histmersion.api.Api.FileBytes), com.histmersion.api.Api.Error, MediaItem, Any] =
    (writeBase.put
      .in("media" / "sources" / path[String]("citekey") / "cover")
      .in(query[String]("provider").default(MediaProvider.Calibre))
      .in(query[String]("key"))
      .in(query[Option[String]]("mime"))
      .mapInTo[SourceCoverParams]
      .in(byteArrayBody)
      .out(jsonBody[MediaItem])).mapIn[(_root_.com.histmersion.api.media.SourceCoverParams, _root_.com.histmersion.api.Api.FileBytes)]((refactorDecoded96: (_root_.com.histmersion.api.media.SourceCoverParams, _root_.scala.Array[_root_.scala.Byte])) => ((refactorDecoded96)._1, _root_.com.histmersion.api.Api.FileBytes((refactorDecoded96)._2)))((refactorEncoded96: (_root_.com.histmersion.api.media.SourceCoverParams, _root_.com.histmersion.api.Api.FileBytes)) => ((refactorEncoded96)._1, ((refactorEncoded96)._2).value))

  lazy val fillFigureLicences: PublicEndpoint[com.histmersion.api.Api.Apply, com.histmersion.api.Api.Error, FigureLicenceFill, Any] =
    (base.post
      .in("media" / "fill-licences")
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[FigureLicenceFill])).mapIn[_root_.com.histmersion.api.Api.Apply]((refactorDecoded97: _root_.scala.Boolean) => _root_.com.histmersion.api.Api.Apply(refactorDecoded97))((refactorEncoded97: _root_.com.histmersion.api.Api.Apply) => (refactorEncoded97).value)

  lazy val mediaThumb: PublicEndpoint[com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.Api.Error, (com.histmersion.api.Api.FileBytes, com.histmersion.api.Api.ContentType), Any] =
    (endpoint.get
      .in("media" / path[String]("uid") / "thumb")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl)))).mapIn[_root_.com.histmersion.api.exchange.Bundle.Asset.Uid]((refactorDecoded204: _root_.java.lang.String) => _root_.com.histmersion.api.exchange.Bundle.Asset.Uid(refactorDecoded204))((refactorEncoded204: _root_.com.histmersion.api.exchange.Bundle.Asset.Uid) => (refactorEncoded204).value).mapOut[(_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)]((refactorDecoded205: (_root_.scala.Array[_root_.scala.Byte], _root_.java.lang.String)) => (_root_.com.histmersion.api.Api.FileBytes((refactorDecoded205)._1), _root_.com.histmersion.api.Api.ContentType((refactorDecoded205)._2)))((refactorEncoded205: (_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)) => (((refactorEncoded205)._1).value, ((refactorEncoded205)._2).value))

  lazy val mediaFile: PublicEndpoint[com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.Api.Error, (com.histmersion.api.Api.FileBytes, com.histmersion.api.Api.ContentType), Any] =
    (endpoint.get
      .in("media" / path[String]("uid") / "file")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl)))).mapIn[_root_.com.histmersion.api.exchange.Bundle.Asset.Uid]((refactorDecoded100: _root_.java.lang.String) => _root_.com.histmersion.api.exchange.Bundle.Asset.Uid(refactorDecoded100))((refactorEncoded100: _root_.com.histmersion.api.exchange.Bundle.Asset.Uid) => (refactorEncoded100).value).mapOut[(_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)]((refactorDecoded101: (_root_.scala.Array[_root_.scala.Byte], _root_.java.lang.String)) => (_root_.com.histmersion.api.Api.FileBytes((refactorDecoded101)._1), _root_.com.histmersion.api.Api.ContentType((refactorDecoded101)._2)))((refactorEncoded101: (_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)) => (((refactorEncoded101)._1).value, ((refactorEncoded101)._2).value))

  lazy val assetLinks: PublicEndpoint[
    (Option[com.histmersion.api.exchange.Bundle.Asset.Uid], Option[com.histmersion.api.media.AssetLinks.Kind], Option[com.histmersion.api.media.AssetLinks.TargetId], Option[com.histmersion.api.media.AssetLinks.TargetKind]),
    com.histmersion.api.Api.Error,
    AssetLink.List,
    Any
  ] =
    (base.get
      .in("asset-links")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("asset"))
      .in(query[Option[String]]("target_type"))
      .in(query[Option[String]]("target_id"))
      .out(jsonBody[AssetLink.List])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.exchange.Bundle.Asset.Uid], _root_.scala.Option[_root_.com.histmersion.api.media.AssetLinks.Kind], _root_.scala.Option[_root_.com.histmersion.api.media.AssetLinks.TargetId], _root_.scala.Option[_root_.com.histmersion.api.media.AssetLinks.TargetKind])]((refactorDecoded208: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => (((refactorDecoded208)._1).map(refactorElement177 => _root_.com.histmersion.api.exchange.Bundle.Asset.Uid(refactorElement177)), ((refactorDecoded208)._2).map(refactorElement178 => _root_.com.histmersion.api.media.AssetLinks.Kind(refactorElement178)), ((refactorDecoded208)._3).map(refactorElement179 => _root_.com.histmersion.api.media.AssetLinks.TargetId(refactorElement179)), ((refactorDecoded208)._4).map(refactorElement180 => _root_.com.histmersion.api.media.AssetLinks.TargetKind(refactorElement180))))((refactorEncoded208: (_root_.scala.Option[_root_.com.histmersion.api.exchange.Bundle.Asset.Uid], _root_.scala.Option[_root_.com.histmersion.api.media.AssetLinks.Kind], _root_.scala.Option[_root_.com.histmersion.api.media.AssetLinks.TargetId], _root_.scala.Option[_root_.com.histmersion.api.media.AssetLinks.TargetKind])) => (((refactorEncoded208)._1).map(refactorElement181 => (refactorElement181).value), ((refactorEncoded208)._2).map(refactorElement182 => (refactorElement182).value), ((refactorEncoded208)._3).map(refactorElement183 => (refactorElement183).value), ((refactorEncoded208)._4).map(refactorElement184 => (refactorElement184).value)))

  lazy val createAssetLink: PublicEndpoint[AssetLink.Put, com.histmersion.api.Api.Error, AssetLink, Any] =
    base.post.in("asset-links").in(jsonBody[AssetLink.Put]).out(jsonBody[AssetLink])

  lazy val deleteAssetLink: PublicEndpoint[com.histmersion.api.media.AssetLink.Id, com.histmersion.api.Api.Error, AssetLink.Deleted, Any] =
    (base.delete.in("asset-links" / path[Long]("id")).out(jsonBody[AssetLink.Deleted])).mapIn[_root_.com.histmersion.api.media.AssetLink.Id]((refactorDecoded103: _root_.scala.Long) => _root_.com.histmersion.api.media.AssetLink.Id(refactorDecoded103))((refactorEncoded103: _root_.com.histmersion.api.media.AssetLink.Id) => (refactorEncoded103).value)

  /** Registered before the image endpoint so Tapir matches it first. */
  lazy val iiifInfo: PublicEndpoint[(com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.exchange.Bundle.Asset.Variant), com.histmersion.api.Api.Error, io.circe.Json, Any] =
    (endpoint.get
      .in("iiif" / path[String]("citekey") / path[String]("name") / "info.json")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(jsonBody[io.circe.Json])
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))).mapIn[(_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant)]((refactorDecoded104: (_root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid((refactorDecoded104)._1), _root_.com.histmersion.api.exchange.Bundle.Asset.Variant((refactorDecoded104)._2)))((refactorEncoded104: (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.exchange.Bundle.Asset.Variant)) => (((refactorEncoded104)._1).value, ((refactorEncoded104)._2).value))

  /** IIIF Image API Level 1: region `full|x,y,w,h`, size `max|^max|w,|,h|w,h|!w,h`, rotation `0` only, quality
    * `default`, format `jpg|png`.
    */
  lazy val iiifImage: PublicEndpoint[
    (com.histmersion.api.exchange.Bundle.Asset.Uid, com.histmersion.api.media.Iiif.RegionText, com.histmersion.api.media.Iiif.SizeText, com.histmersion.api.media.Iiif.Rotation, com.histmersion.api.media.Iiif.Quality, com.histmersion.api.media.Iiif.Format),
    com.histmersion.api.Api.Error,
    (com.histmersion.api.Api.FileBytes, com.histmersion.api.Api.ContentType),
    Any
  ] =
    (endpoint.get
      .in(
        "iiif" / path[String]("citekey") / path[String]("name") /
          path[String]("region") / path[String]("size") / path[String]("rotation") /
          path[String]("filename")
      )
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[com.histmersion.api.Api.Error])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, "public, max-age=31536000, immutable")))).mapIn[(_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.media.Iiif.RegionText, _root_.com.histmersion.api.media.Iiif.SizeText, _root_.com.histmersion.api.media.Iiif.Rotation, _root_.com.histmersion.api.media.Iiif.Quality, _root_.com.histmersion.api.media.Iiif.Format)]((refactorDecoded211: (_root_.java.lang.String, _root_.java.lang.String, _root_.java.lang.String, _root_.java.lang.String, _root_.java.lang.String, _root_.java.lang.String)) => (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid((refactorDecoded211)._1), _root_.com.histmersion.api.media.Iiif.RegionText((refactorDecoded211)._2), _root_.com.histmersion.api.media.Iiif.SizeText((refactorDecoded211)._3), _root_.com.histmersion.api.media.Iiif.Rotation((refactorDecoded211)._4), _root_.com.histmersion.api.media.Iiif.Quality((refactorDecoded211)._5), _root_.com.histmersion.api.media.Iiif.Format((refactorDecoded211)._6)))((refactorEncoded211: (_root_.com.histmersion.api.exchange.Bundle.Asset.Uid, _root_.com.histmersion.api.media.Iiif.RegionText, _root_.com.histmersion.api.media.Iiif.SizeText, _root_.com.histmersion.api.media.Iiif.Rotation, _root_.com.histmersion.api.media.Iiif.Quality, _root_.com.histmersion.api.media.Iiif.Format)) => (((refactorEncoded211)._1).value, ((refactorEncoded211)._2).value, ((refactorEncoded211)._3).value, ((refactorEncoded211)._4).value, ((refactorEncoded211)._5).value, ((refactorEncoded211)._6).value)).mapOut[(_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)]((refactorDecoded212: (_root_.scala.Array[_root_.scala.Byte], _root_.java.lang.String)) => (_root_.com.histmersion.api.Api.FileBytes((refactorDecoded212)._1), _root_.com.histmersion.api.Api.ContentType((refactorDecoded212)._2)))((refactorEncoded212: (_root_.com.histmersion.api.Api.FileBytes, _root_.com.histmersion.api.Api.ContentType)) => (((refactorEncoded212)._1).value, ((refactorEncoded212)._2).value))

  final case class Error(message: Error.Message, code: Option[Error.Code] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
  object Error:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    /** Tapir hands the matcher an `Any`, and under `-source:future` a constructor pattern on `Any` warns (it is not
      * `Matchable`), so the value is checked with a type test.
      */

    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def hasCode(code: com.histmersion.api.Api.Error.Code): PartialFunction[Any, Boolean] =
      case e if e.isInstanceOf[Error] && e.asInstanceOf[Error].code.contains(code) => (true)

  object Index:
    type Block = Block.T
    object Block extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Current = Current.T
    object Current extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PathTemplate = PathTemplate.T
    object PathTemplate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Readme = Readme.T
    object Readme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RepoPath = RepoPath.T
    object RepoPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type PathResult = PathResult.T
    object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Begin = "<!-- BEGIN API INDEX (generated by `just api-docs` — do not edit by hand) -->"
    val End = "<!-- END API INDEX -->"

    def path(e: AnyEndpoint): com.histmersion.api.Api.Index.PathTemplate = _root_.com.histmersion.api.Api.Index.PathTemplate(e.showPathTemplate(showQueryParam = None))

    def method(e: AnyEndpoint): com.histmersion.api.Api.HttpMethod.Wire = _root_.com.histmersion.api.Api.HttpMethod.Wire(e.method.map(_.method).getOrElse("GET"))

    def markdown(endpoints: List[AnyEndpoint]): com.histmersion.api.Api.Index.MarkdownResult =
      val order = List("GET", "POST", "PUT", "PATCH", "DELETE")
      val byPath = endpoints.groupBy(path).view.mapValues(_.map(method).distinct.sortBy(m => order.indexOf(m))).toMap
      val rows = byPath.keys.toList.sorted.map(p => s"| ${byPath(p).map(m => s"`$m`").mkString(" · ")} | `$p` |")
      _root_.com.histmersion.api.Api.Index.MarkdownResult((s"${Plural.count(_root_.com.histmersion.api.Plural.Count((endpoints.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("endpoint"))} on ${Plural.count(_root_.com.histmersion.api.Plural.Count((byPath.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("path"))}:" ::
        "" :: "| methods | path |" :: "|---|---|" :: rows).mkString("\n"))

    val Page = "docs/user-guide/reference/rest-api.md"

    def splice(readme: com.histmersion.api.Api.Index.Readme, index: com.histmersion.api.Api.Index.Block): Either[com.histmersion.api.Api.Error.Problem, com.histmersion.api.Api.Index.Readme] =
      val (b, e) = ((readme).value.indexOf(Begin), (readme).value.indexOf(End))
      if b < 0 || e < b then Left(_root_.com.histmersion.api.Api.Error.Problem(s"$Page has no $Begin … $End block"))
      else Right(_root_.com.histmersion.api.Api.Index.Readme((readme).value.substring(0, b + Begin.length) + "\n" + index + "\n" + (readme).value.substring(e)))

    def current(readme: com.histmersion.api.Api.Index.Readme): Option[com.histmersion.api.Api.Index.Current] =
      val (b, e) = ((readme).value.indexOf(Begin), (readme).value.indexOf(End))
      Option.when(b >= 0 && e > b)(_root_.com.histmersion.api.Api.Index.Current((readme).value.substring(b + Begin.length, e).trim))

  @main def generateApiIndex(repo: com.histmersion.api.Api.Index.RepoPath): Unit =
    val p = java.nio.file.Path.of((repo).value).resolve(Index.Page)
    Index.splice(_root_.com.histmersion.api.Api.Index.Readme(java.nio.file.Files.readString(p)), Index.markdown(Api.all)) match
      case Left(e)  => throw IllegalStateException(e)
      case Right(s) => val _ = java.nio.file.Files.writeString(p, s)
