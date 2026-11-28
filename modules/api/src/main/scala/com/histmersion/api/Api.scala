package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.api.problem.Answer
import com.histmersion.util.problem.Text.sentence
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
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
import com.histmersion.api.settings.{ConfigEdit, ConfigEditApi, ReloadReport}
import com.histmersion.api.views.{AutopilotApi, EventDetail, PlaceDetail, ResolvedId}
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.api.catalogue.Gazetteer
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.instance.Version
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.api.log.LogApi
import com.histmersion.api.research.ResearchApi
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.api.research.Article
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.AnyEndpoint
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Sort
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.library.Edge
import scala.`package`.List
import com.histmersion.api.library.AcquisitionWorkApi
import com.histmersion.api.personal.PersonalApi
import com.histmersion.api.library.CollectionsApi
import com.histmersion.api.instance.CorpusCounts
import com.histmersion.api.catalogue.Event
import com.histmersion.api.library.Title
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.media.Iiif
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.automation.Agents
import com.histmersion.api.library.Histogram

/** Every endpoint here and in the other `*Api` objects is a `lazy val`: initialising an object runs all of its strict
  * vals, so Scala.js would link every endpoint and every codec it names into the web bundle as soon as the page used
  * one of them.
  */
@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
@movesTo(FeaturePlace.Api)
object Api:
  object Search:
    export Paging.Limit
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  object Reindex:
    export Paging.Limit
    type Scope = Scope.T
    object Scope extends Newtype[String, Pure]
  object PlacesGeo:
    type Json = Json.T
    object Json extends Newtype[String, Pure]
  object Merge:
    type Entity = Entity.T
    object Entity extends Newtype[String, Pure]
    export SpatialHierarchyResult.Job.Id
    export Paging.Limit
  object Lookup:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  object Jobs:
    export Paging.Limit
    type Status = Status.T
    object Status extends Newtype[String, Pure]
  object JobControl:
    export Agents.Action
  object HttpMethod:

    /** Two sttp methods compare with `==` and `!=`, also under `-language:strictEquality`: both clients (the CLI's and
      * the web page's) tell a write from a read by its method. The one such instance, imported by name
      * (`Api.HttpMethod.equality`) where methods are compared.
      */
    given equality: CanEqual[sttp.model.Method, sttp.model.Method] = CanEqual.derived

    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  object Facets:
    type Field = Field.T
    object Field extends Newtype[String, Pure]
    type Prefix = Prefix.T
    object Prefix extends Newtype[String, Pure]
  object ExternalPlaces:
    type Format = Format.T
    object Format extends Newtype[String, Pure]
    type Locator = Locator.T
    object Locator extends Newtype[String, Pure]
    type Source = Source.T
    object Source extends Newtype[String, Pure]
  object Assets:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Owner = Owner.T
    object Owner extends Newtype[String, Pure]
  object AgentState:
    type Key = Key.T
    object Key extends Newtype[String, Pure]
  type Apply = Apply.T
  object Apply extends Newtype[Boolean, Pure]
  type ContentType = ContentType.T
  object ContentType extends Newtype[String, Pure]
  type FileBytes = FileBytes.T
  object FileBytes extends Newtype[Array[Byte], Pure]
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]
  type Overwrite = Overwrite.T
  object Overwrite extends Newtype[Boolean, Pure]

  /** `region` is still sent by older links and clients; it is one more place name. */

  final case class FindingsQuery(filter: Filter, region: Option[FindingsQuery.Region]):
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def toFilter: Filter =
      region
        .map((_).value.trim)
        .filter(_.nonEmpty)
        .fold(filter)(r => filter.copy(places = filter.places :+ Filter.Places(r)))

  object FindingsQuery:
    type Region = Region.T
    object Region extends Newtype[String, Pure]

    def of(filter: Filter): FindingsQuery = FindingsQuery(filter, None)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def read(t: (Filter, Option[String])): FindingsQuery = t match
      case (filter, region) => FindingsQuery(filter, region.map(Region(_)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def write(v: FindingsQuery): (Filter, Option[String]) = (v.filter, v.region.map(_.value))

  private lazy val base = endpoint.in("api").errorOut(problemOut)

  /** The parameter names are fixed: saved links, the web UI and the CLI rely on them. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val filterQuery: EndpointInput[Filter] =
    import com.histmersion.api.library.Filter.Docs as D
    def q[A](p: D.Param)(using Codec[List[String], A, CodecFormat.TextPlain]) =
      query[A]((p.rest).value).description((p.doc).value)
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
      .map {
        case (
              q,
              tag,
              author,
              place,
              topic,
              status,
              yearFrom,
              yearTo,
              from,
              to,
              minLon,
              minLat,
              maxLon,
              maxLat,
              sort,
              limit,
              offset,
              title,
              goal
            ) =>
          Filter.Query(
            q.map(Filter.Q(_)),
            tag.map(Filter.Tags(_)),
            author.map(Filter.Authors(_)),
            place.map(Filter.Places(_)),
            topic.map(Filter.Topics(_)),
            status.map(Filter.Status(_)),
            yearFrom.map(catalogue.Year(_)),
            yearTo.map(catalogue.Year(_)),
            from.map(catalogue.Year(_)),
            to.map(catalogue.Year(_)),
            minLon.map(Longitude(_)),
            minLat.map(Latitude(_)),
            maxLon.map(Longitude(_)),
            maxLat.map(Latitude(_)),
            sort.map(Sort.Wire(_)),
            limit.map(Filter.Limit(_)),
            offset.map(Filter.Offset(_)),
            title.map(Filter.Title(_)),
            goal.map(ReviewGoal.Slug(_))
          )
      }(v =>
        (
          v.q.map(_.value),
          v.tag.map(_.value),
          v.author.map(_.value),
          v.place.map(_.value),
          v.topic.map(_.value),
          v.status.map(_.value),
          v.yearFrom.map(_.value),
          v.yearTo.map(_.value),
          v.from.map(_.value),
          v.to.map(_.value),
          v.minLon.map(_.value),
          v.minLat.map(_.value),
          v.maxLon.map(_.value),
          v.maxLat.map(_.value),
          v.sort.map(_.value),
          v.limit.map(_.value),
          v.offset.map(_.value),
          v.title.map(_.value),
          v.goal.map(_.value)
        )
      )
      .map(_.toFilter)(Filter.Query.from)

  lazy val version: PublicEndpoint[Unit, Error, Version.Info, Any] =
    base.get.in("version").out(jsonBody[Version.Info])

  lazy val health: PublicEndpoint[Unit, Error, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  lazy val about: PublicEndpoint[Unit, Error, About, Any] =
    base.get.in("about").out(jsonBody[About])

  @tolerated(
    LintRule.GetWrites,
    "pending S1 follow-up 5 (a GET or HEAD route writes nothing): GET: File Files.deleteIfExists"
  )
  lazy val config: PublicEndpoint[Unit, Error, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val search: PublicEndpoint[(Option[Search.Text], Option[Search.Limit]), Error, List[SearchHit], Any] =
    (base.get
      .in("search")
      .in(query[Option[String]]("q"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]]))
      .mapIn[(Option[Search.Text], Option[Search.Limit])]((refactorDecoded107: (Option[String], Option[Int])) =>
        (
          ((refactorDecoded107)._1).map(refactorElement93 => Search.Text(refactorElement93)),
          ((refactorDecoded107)._2).map(refactorElement94 => Search.Limit(refactorElement94))
        )
      )((refactorEncoded107: (Option[Search.Text], Option[Search.Limit])) =>
        (
          ((refactorEncoded107)._1).map(refactorElement95 => (refactorElement95).value),
          ((refactorEncoded107)._2).map(refactorElement96 => (refactorElement96).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val resolveId: PublicEndpoint[Lookup.Text, Error, ResolvedId, Any] =
    (base.get
      .in("resolve" / path[String]("id"))
      .out(jsonBody[ResolvedId]))
      .mapIn[Lookup.Text]((refactorDecoded108: String) => Lookup.Text(refactorDecoded108))(
        (refactorEncoded108: Lookup.Text) => (refactorEncoded108).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val getSource: PublicEndpoint[
    (Citekey, SourceViewQuery.FullText),
    Error,
    Source.WithFindings,
    Any
  ] =
    (base.get
      .in("sources" / path[String]("citekey"))
      .in(query[Boolean]("body").default(true))
      .out(jsonBody[Source.WithFindings]))
      .mapIn[(Citekey, SourceViewQuery.FullText)]((refactorDecoded109: (String, Boolean)) =>
        (
          Citekey((refactorDecoded109)._1),
          SourceViewQuery.FullText((refactorDecoded109)._2)
        )
      )((refactorEncoded109: (Citekey, SourceViewQuery.FullText)) =>
        (((refactorEncoded109)._1).value, ((refactorEncoded109)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val sourceArticles: PublicEndpoint[Citekey, Error, List[Backlink], Any] =
    (base.get
      .in("sources" / path[String]("citekey") / "articles")
      .out(jsonBody[List[Backlink]]))
      .mapIn[Citekey]((refactorDecoded4: String) => Citekey(refactorDecoded4))((refactorEncoded4: Citekey) =>
        (refactorEncoded4).value
      )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class SourceViewQuery(
      citekey: Citekey,
      fullText: SourceViewQuery.FullText = SourceViewQuery.FullText(false),
      section: Option[SourceViewQuery.Section] = None,
      heading: Option[SourceViewQuery.Heading] = None,
      offset: Option[SourceViewQuery.Offset] = None,
      chars: Option[SourceViewQuery.Chars] = None
  )

  object SourceViewQuery:
    type FullText = FullText.T
    object FullText extends Newtype[Boolean, Pure]
    export Source.Section.Wire as Section
    type Heading = Heading.T
    object Heading extends Newtype[String, Pure]:
      type Text = Text.T
      object Text extends Newtype[String, Pure]
    type Offset = Offset.T
    object Offset extends Newtype[Int, Pure]
    type Chars = Chars.T
    object Chars extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val sourceView: PublicEndpoint[SourceViewQuery, Error, Source.View, Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "view")
      .in(query[Boolean]("fullText").default(false))
      .in(query[Option[String]]("section"))
      .in(query[Option[String]]("heading"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("chars"))
      .mapIn { case (citekey, fullText, section, heading, offset, chars) =>
        SourceViewQuery(
          Citekey(citekey),
          SourceViewQuery.FullText(fullText),
          section.map(SourceViewQuery.Section(_)),
          heading.map(SourceViewQuery.Heading(_)),
          offset.map(SourceViewQuery.Offset(_)),
          chars.map(SourceViewQuery.Chars(_))
        )
      }(v =>
        (
          v.citekey.value,
          v.fullText.value,
          v.section.map(_.value),
          v.heading.map(_.value),
          v.offset.map(_.value),
          v.chars.map(_.value)
        )
      )
      .errorOut(problemOut)
      .out(stringBody))
      .mapOut[Source.View]((refactorDecoded: String) => Source.View(refactorDecoded))((refactorEncoded: Source.View) =>
        (refactorEncoded).value
      )

  final case class PassagesQuery(
      citekey: Citekey,
      q: PassagesQuery.Q,
      regex: PassagesQuery.Regex = PassagesQuery.Regex(false),
      limit: Option[PassagesQuery.Limit] = None,
      context: Option[PassagesQuery.Context] = None
  )

  object PassagesQuery:
    type Q = Q.T
    object Q extends Newtype[String, Pure]
    type Regex = Regex.T
    object Regex extends Newtype[Boolean, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type Context = Context.T
    object Context extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val passages: PublicEndpoint[PassagesQuery, Error, PassageResult, Any] =
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
      .mapIn { case (citekey, q, regex, limit, context) =>
        PassagesQuery(
          Citekey(citekey),
          PassagesQuery.Q(q),
          PassagesQuery.Regex(regex),
          limit.map(PassagesQuery.Limit(_)),
          context.map(PassagesQuery.Context(_))
        )
      }(v => (v.citekey.value, v.q.value, v.regex.value, v.limit.map(_.value), v.context.map(_.value)))
      .errorOut(problemOut)
      .out(jsonBody[PassageResult])

  lazy val findings: PublicEndpoint[FindingsQuery, Error, List[GeoHit], Any] =
    base.get
      .in("findings")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapIn(FindingsQuery.read)(FindingsQuery.write)
      .out(jsonBody[List[GeoHit]])

  lazy val findingsPage: PublicEndpoint[FindingsQuery, Error, GeoHitPage, Any] =
    base.get
      .in("findings" / "page")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapIn(FindingsQuery.read)(FindingsQuery.write)
      .out(jsonBody[GeoHitPage])

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type TimeUnit = Unit
    val TimeUnit: Unit.type = Unit
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type TreeUnit = Unit
    val TreeUnit: Unit.type = Unit
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type TreeAsOf = AsOf
    val TreeAsOf: AsOf.type = AsOf
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type TreeSamples = Samples
    val TreeSamples: Samples.type = Samples

    type By = By.T
    object By extends Newtype[String, Pure]
    export Histogram.Unit
    type Depth = Depth.T
    object Depth extends Newtype[Int, Pure]
    type AsOf = AsOf.T
    object AsOf extends Newtype[Long, Pure]
    type Samples = Samples.T
    object Samples extends Newtype[Int, Pure]
    type Size = Size.T
    object Size extends Newtype[Long, Pure]

    def time(filter: Filter, unit: Option[TimeUnit] = None): AggregateQuery =
      AggregateQuery(Some(By("time")), unit, filter)

    def tree(
        by: By,
        filter: Filter,
        unit: Option[TreeUnit] = None,
        depth: Option[Depth] = None,
        asOf: Option[TreeAsOf] = None,
        samples: Option[TreeSamples] = None
    ): AggregateQuery =
      AggregateQuery(Some(by), unit, filter, depth.orElse(Some(Depth(0))), asOf, samples)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val aggregate: PublicEndpoint[AggregateQuery, Error, TimeHistogram, Any] =
    base.get
      .in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .in(query[Option[Int]]("depth"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("samples"))
      .in(query[Option[Long]]("size"))
      .mapIn { case (by, unit, filter, depth, asOf, samples, size) =>
        AggregateQuery(
          by.map(AggregateQuery.By(_)),
          unit.map(AggregateQuery.Unit(_)),
          filter,
          depth.map(AggregateQuery.Depth(_)),
          asOf.map(AggregateQuery.AsOf(_)),
          samples.map(AggregateQuery.Samples(_)),
          size.map(AggregateQuery.Size(_))
        )
      }(v =>
        (
          v.by.map(_.value),
          v.unit.map(_.value),
          v.filter,
          v.depth.map(_.value),
          v.asOf.map(_.value),
          v.samples.map(_.value),
          v.size.map(_.value)
        )
      )
      .out(jsonBody[TimeHistogram])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val neighbors: PublicEndpoint[(Citekey, List[Edge.Kind.Wire]), Error, List[NeighborRow], Any] =
    (base.get
      .in("neighbors" / path[String]("citekey"))
      .in(query[List[String]]("kind"))
      .out(jsonBody[List[NeighborRow]]))
      .mapIn[(Citekey, List[Edge.Kind.Wire])]((refactorDecoded5: (String, List[String])) =>
        (
          Citekey((refactorDecoded5)._1),
          ((refactorDecoded5)._2).map(refactorElement5 => Edge.Kind.Wire(refactorElement5))
        )
      )((refactorEncoded5: (Citekey, List[Edge.Kind.Wire])) =>
        (((refactorEncoded5)._1).value, ((refactorEncoded5)._2).map(refactorElement6 => (refactorElement6).value))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val graph: PublicEndpoint[
    (Citekey, Option[Source.Graph.DepthCount]),
    Error,
    Source.Graph,
    Any
  ] =
    (base.get
      .in("graph" / path[String]("citekey"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[Source.Graph]))
      .mapIn[(Citekey, Option[Source.Graph.DepthCount])]((refactorDecoded112: (String, Option[Int])) =>
        (
          Citekey((refactorDecoded112)._1),
          ((refactorDecoded112)._2)
            .map(refactorElement99 => Source.Graph.DepthCount(refactorElement99))
        )
      )((refactorEncoded112: (Citekey, Option[Source.Graph.DepthCount])) =>
        (
          ((refactorEncoded112)._1).value,
          ((refactorEncoded112)._2).map(refactorElement100 => (refactorElement100).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val contradictions: PublicEndpoint[
    (Option[Citekey], Option[AggregateQuery.AsOf]),
    Error,
    ContradictionsResult,
    Any
  ] =
    (base.get
      .in("contradictions")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[Long]]("since"))
      .out(jsonBody[ContradictionsResult]))
      .mapIn[(Option[Citekey], Option[AggregateQuery.AsOf])]((refactorDecoded7: (Option[String], Option[Long])) =>
        (
          ((refactorDecoded7)._1).map(refactorElement9 => Citekey(refactorElement9)),
          ((refactorDecoded7)._2).map(refactorElement10 => AggregateQuery.AsOf(refactorElement10))
        )
      )((refactorEncoded7: (Option[Citekey], Option[AggregateQuery.AsOf])) =>
        (
          ((refactorEncoded7)._1).map(refactorElement11 => (refactorElement11).value),
          ((refactorEncoded7)._2).map(refactorElement12 => (refactorElement12).value)
        )
      )

  lazy val seed: PublicEndpoint[SeedRequest, Error, SeedResult, Any] =
    base.post.in("seed").in(jsonBody[SeedRequest]).out(jsonBody[SeedResult])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val reindex: PublicEndpoint[(Option[Reindex.Scope], Option[Reindex.Limit]), Error, ReindexResult, Any] =
    (base.post
      .in("reindex")
      .in(query[Option[String]]("after"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ReindexResult]))
      .mapIn[(Option[Reindex.Scope], Option[Reindex.Limit])]((refactorDecoded114: (Option[String], Option[Int])) =>
        (
          ((refactorDecoded114)._1).map(refactorElement105 => Reindex.Scope(refactorElement105)),
          ((refactorDecoded114)._2).map(refactorElement106 => Reindex.Limit(refactorElement106))
        )
      )((refactorEncoded114: (Option[Reindex.Scope], Option[Reindex.Limit])) =>
        (
          ((refactorEncoded114)._1).map(refactorElement107 => (refactorElement107).value),
          ((refactorEncoded114)._2).map(refactorElement108 => (refactorElement108).value)
        )
      )

  lazy val sources: PublicEndpoint[Filter, Error, SearchResult, Any] =
    base.get.in("sources").in(filterQuery).out(jsonBody[SearchResult])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val facets: PublicEndpoint[(Facets.Field, Option[Facets.Prefix], Filter), Error, List[Facet], Any] =
    (base.get
      .in("facets")
      .in(query[String]("field"))
      .in(query[Option[String]]("prefix"))
      .in(filterQuery)
      .out(jsonBody[List[Facet]]))
      .mapIn[(Facets.Field, Option[Facets.Prefix], Filter)]((refactorDecoded115: (String, Option[String], Filter)) =>
        (
          Facets.Field((refactorDecoded115)._1),
          ((refactorDecoded115)._2).map(refactorElement109 => Facets.Prefix(refactorElement109)),
          (refactorDecoded115)._3
        )
      )((refactorEncoded115: (Facets.Field, Option[Facets.Prefix], Filter)) =>
        (
          ((refactorEncoded115)._1).value,
          ((refactorEncoded115)._2).map(refactorElement110 => (refactorElement110).value),
          (refactorEncoded115)._3
        )
      )

  private def strictQuery = query[Boolean]("strict")
    .default(false)
    .description("refuse the write when its Markdown has a formatting error (default: report only)")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putSource: PublicEndpoint[(Citekey, Source.Put, Overwrite), Error, Source.WithFindings, Any] =
    (endpoint.put
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[Source.Put])
      .in(strictQuery)
      .errorOut(problemOut)
      .out(jsonBody[Source.WithFindings]))
      .mapIn[(Citekey, Source.Put, Overwrite)]((refactorDecoded10: (String, Source.Put, Boolean)) =>
        (
          Citekey((refactorDecoded10)._1),
          (refactorDecoded10)._2,
          Overwrite((refactorDecoded10)._3)
        )
      )((refactorEncoded10: (Citekey, Source.Put, Overwrite)) =>
        (((refactorEncoded10)._1).value, (refactorEncoded10)._2, ((refactorEncoded10)._3).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val patchSource: PublicEndpoint[(Citekey, Source.Patch.Request, Apply), Error, Source.WithFindings, Any] =
    (endpoint.patch
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[Source.Patch.Request])
      .in(strictQuery)
      .errorOut(problemOut)
      .out(jsonBody[Source.WithFindings]))
      .mapIn[(Citekey, Source.Patch.Request, Apply)]((refactorDecoded117: (String, Source.Patch.Request, Boolean)) =>
        (
          Citekey((refactorDecoded117)._1),
          (refactorDecoded117)._2,
          Apply((refactorDecoded117)._3)
        )
      )((refactorEncoded117: (Citekey, Source.Patch.Request, Apply)) =>
        (((refactorEncoded117)._1).value, (refactorEncoded117)._2, ((refactorEncoded117)._3).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val lintSource: PublicEndpoint[Citekey, Error, FormatReport, Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "lint")
      .errorOut(problemOut)
      .out(jsonBody[FormatReport]))
      .mapIn[Citekey]((refactorDecoded118: String) => Citekey(refactorDecoded118))((refactorEncoded118: Citekey) =>
        (refactorEncoded118).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val fixSourceFormat: PublicEndpoint[Citekey, Error, FormatReport, Any] =
    (endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "lint" / "fix")
      .errorOut(problemOut)
      .out(jsonBody[FormatReport]))
      .mapIn[Citekey]((refactorDecoded13: String) => Citekey(refactorDecoded13))((refactorEncoded13: Citekey) =>
        (refactorEncoded13).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val renameSource: PublicEndpoint[(Citekey, Rename.Request), Error, Source.Renamed, Any] =
    (base.post
      .in("sources" / path[String]("citekey") / "rename")
      .in(jsonBody[Rename.Request])
      .out(jsonBody[Source.Renamed]))
      .mapIn[(Citekey, Rename.Request)]((refactorDecoded120: (String, Rename.Request)) =>
        (Citekey((refactorDecoded120)._1), (refactorDecoded120)._2)
      )((refactorEncoded120: (Citekey, Rename.Request)) => (((refactorEncoded120)._1).value, (refactorEncoded120)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val deleteSource: PublicEndpoint[(Citekey, Source.DeleteRequest), Error, Source.Deletion, Any] =
    (base.post
      .in("sources" / path[String]("citekey") / "delete")
      .in(jsonBody[Source.DeleteRequest])
      .out(jsonBody[Source.Deletion]))
      .mapIn[(Citekey, Source.DeleteRequest)]((refactorDecoded121: (String, Source.DeleteRequest)) =>
        (Citekey((refactorDecoded121)._1), (refactorDecoded121)._2)
      )((refactorEncoded121: (Citekey, Source.DeleteRequest)) =>
        (((refactorEncoded121)._1).value, (refactorEncoded121)._2)
      )

  lazy val sourceAliases: PublicEndpoint[Unit, Error, List[Source.Alias], Any] =
    base.get.in("source-aliases").out(jsonBody[List[Source.Alias]])

  lazy val importNote: PublicEndpoint[NoteImport, Error, SeedResult, Any] =
    base.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val exportSource: PublicEndpoint[Citekey, Error, Source.Export, Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(problemOut)
      .out(stringBody))
      .mapIn[Citekey]((refactorDecoded16: String) => Citekey(refactorDecoded16))((refactorEncoded16: Citekey) =>
        (refactorEncoded16).value
      )
      .mapOut[Source.Export]((refactorDecoded17: String) => Source.Export(refactorDecoded17))(
        (refactorEncoded17: Source.Export) => (refactorEncoded17).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val citekeys: PublicEndpoint[Unit, Error, List[Citekey], Any] =
    (base.get
      .in("citekeys")
      .out(jsonBody[List[String]]))
      .mapOut[List[Citekey]]((refactorDecoded2: List[String]) =>
        (refactorDecoded2).map(refactorElement => Citekey(refactorElement))
      )((refactorEncoded2: List[Citekey]) => (refactorEncoded2).map(refactorElement2 => (refactorElement2).value))

  lazy val resolveEdges: PublicEndpoint[Unit, Error, EdgesResolved, Any] =
    base.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  lazy val configReload: PublicEndpoint[Unit, Error, ReloadReport, Any] =
    base.post.in("config" / "reload").out(jsonBody[ReloadReport])

  lazy val backup: PublicEndpoint[Unit, Error, Backup.Status, Any] =
    base.post.in("backup").out(jsonBody[Backup.Status])

  /** A job, not a request: Postgres cannot run `VACUUM` inside a transaction. */
  lazy val maintenanceVacuum: PublicEndpoint[Vacuum.Request, Error, JobInfo, Any] =
    base.post.in("maintenance" / "vacuum").in(jsonBody[Vacuum.Request]).out(jsonBody[JobInfo])

  lazy val backupStatus: PublicEndpoint[Unit, Error, Backup.Status, Any] =
    endpoint.get.in("api" / "backup" / "status").errorOut(problemOut).out(jsonBody[Backup.Status])

  lazy val backups: PublicEndpoint[Unit, Error, List[Backup.Info], Any] =
    base.get.in("backups").out(jsonBody[List[Backup.Info]])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val downloadBackup: PublicEndpoint[Backup.Name, Error, TapirFile, Any] =
    (endpoint.get
      .in("api" / "backups" / path[String]("file"))
      .errorOut(problemOut)
      .out(fileBody))
      .mapIn[Backup.Name]((refactorDecoded: String) => Backup.Name(refactorDecoded))((refactorEncoded: Backup.Name) =>
        (refactorEncoded).value
      )

  lazy val restore: PublicEndpoint[RestoreRequest, Error, RestoreResult, Any] =
    base.post.in("restore").in(jsonBody[RestoreRequest]).out(jsonBody[RestoreResult])

  lazy val instance: PublicEndpoint[Unit, Error, InstanceInfo, Any] =
    base.get.in("instance").out(jsonBody[InstanceInfo])

  lazy val setInstance: PublicEndpoint[InstanceLabel, Error, InstanceInfo, Any] =
    base.put.in("instance").in(jsonBody[InstanceLabel]).out(jsonBody[InstanceInfo])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.GetWrites,
    "pending S1 follow-up 5 (a GET or HEAD route writes nothing): GET: File Files.createDirectories, GET: File Files.createTempFile, GET: File Files.deleteIfExists, GET: File Files.move, GET: File Files.newOutputStream, GET: File Files.setPosixFilePermissions"
  )
  lazy val exportBundle: PublicEndpoint[Option[Bundle.Name], Error, (TapirFile, FileName), Any] =
    (endpoint.get
      .in("api" / "bundle")
      .in(query[Option[String]]("since"))
      .errorOut(problemOut)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition)))
      .mapIn[Option[Bundle.Name]]((refactorDecoded: Option[String]) =>
        refactorDecoded.map(refactorElement => Bundle.Name(refactorElement))
      )((refactorEncoded: Option[Bundle.Name]) => refactorEncoded.map(refactorElement => (refactorElement).value))
      .mapOut[(TapirFile, FileName)]((refactorDecoded: (TapirFile, String)) =>
        (refactorDecoded._1, FileName(refactorDecoded._2))
      )((refactorEncoded: (TapirFile, FileName)) => (refactorEncoded._1, (refactorEncoded._2).value))

  lazy val verifyBundle: PublicEndpoint[TapirFile, Error, Bundle.VerifyResult, Any] =
    base.post.in("bundle" / "verify").in(fileBody).out(jsonBody[Bundle.VerifyResult])

  /** Not an MCP tool: its input is a file upload, which an MCP call cannot carry. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val importBundle: PublicEndpoint[(Apply, TapirFile), Error, Bundle.ImportResult, Any] =
    (base.post
      .in("bundle" / "import")
      .in(query[Boolean]("dryRun").default(false))
      .in(fileBody)
      .out(jsonBody[Bundle.ImportResult]))
      .mapIn[(Apply, TapirFile)]((refactorDecoded: (Boolean, TapirFile)) =>
        (Apply(refactorDecoded._1), refactorDecoded._2)
      )((refactorEncoded: (Apply, TapirFile)) => ((refactorEncoded._1).value, refactorEncoded._2))

  /** Kept on the server under its sha256 so `merge plan` and `merge import` can name it: their body is the resolution
    * set, and a request carries one body.
    */
  lazy val uploadBundle: PublicEndpoint[TapirFile, Error, Bundle.Upload, Any] =
    base.post.in("bundle" / "upload").in(fileBody).out(jsonBody[Bundle.Upload])

  lazy val mergePlan: PublicEndpoint[MergePlan.Request, Error, MergePlan, Any] =
    base.post.in("merge" / "plan").in(jsonBody[MergePlan.Request]).out(jsonBody[MergePlan])

  lazy val mergeImport: PublicEndpoint[MergeImportRequest, Error, Bundle.ImportResult, Any] =
    base.post.in("merge" / "import").in(jsonBody[MergeImportRequest]).out(jsonBody[Bundle.ImportResult])

  lazy val mergeApply: PublicEndpoint[MergeApplyRequest, Error, MergeApplyJob, Any] =
    base.post.in("merge" / "apply").in(jsonBody[MergeApplyRequest]).out(jsonBody[MergeApplyJob])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mergeApplyStatus: PublicEndpoint[Merge.Id, Error, MergeApplyJob, Any] =
    (base.get
      .in("merge" / "apply" / path[String]("job"))
      .out(jsonBody[MergeApplyJob]))
      .mapIn[Merge.Id]((refactorDecoded23: String) => Merge.Id(refactorDecoded23))((refactorEncoded23: Merge.Id) =>
        (refactorEncoded23).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mergeSession: PublicEndpoint[Merge.Id, Error, MergeSession, Any] =
    (base.get
      .in("merge" / "sessions" / path[String]("bundle"))
      .out(jsonBody[MergeSession]))
      .mapIn[Merge.Id]((refactorDecoded130: String) => Merge.Id(refactorDecoded130))((refactorEncoded130: Merge.Id) =>
        (refactorEncoded130).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putMergeSession: PublicEndpoint[(Merge.Id, MergeResolutions), Error, MergeSession, Any] =
    (base.put
      .in("merge" / "sessions" / path[String]("bundle"))
      .in(jsonBody[MergeResolutions])
      .out(jsonBody[MergeSession]))
      .mapIn[(Merge.Id, MergeResolutions)]((refactorDecoded131: (String, MergeResolutions)) =>
        (Merge.Id((refactorDecoded131)._1), (refactorDecoded131)._2)
      )((refactorEncoded131: (Merge.Id, MergeResolutions)) =>
        (((refactorEncoded131)._1).value, (refactorEncoded131)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val parseResolutions: PublicEndpoint[Merge.Id, Error, MergeResolutions, Any] =
    (base.post
      .in("merge" / "resolutions")
      .in(stringBody)
      .out(jsonBody[MergeResolutions]))
      .mapIn[Merge.Id]((refactorDecoded26: String) => Merge.Id(refactorDecoded26))((refactorEncoded26: Merge.Id) =>
        (refactorEncoded26).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val merges: PublicEndpoint[Option[Merge.Limit], Error, List[MergeInfo], Any] =
    (base.get
      .in("merges")
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[MergeInfo]]))
      .mapIn[Option[Merge.Limit]]((refactorDecoded133: Option[Int]) =>
        (refactorDecoded133).map(refactorElement113 => Merge.Limit(refactorElement113))
      )((refactorEncoded133: Option[Merge.Limit]) =>
        (refactorEncoded133).map(refactorElement114 => (refactorElement114).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mergeLedger
      : PublicEndpoint[(Option[Merge.Id], Option[Merge.Entity], Option[Merge.Limit]), Error, MergeLedger, Any] =
    (base.get
      .in("merges" / "ledger")
      .in(query[Option[String]]("uid"))
      .in(query[Option[String]]("merge"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[MergeLedger]))
      .mapIn[(Option[Merge.Id], Option[Merge.Entity], Option[Merge.Limit])](
        (refactorDecoded134: (Option[String], Option[String], Option[Int])) =>
          (
            ((refactorDecoded134)._1).map(refactorElement115 => Merge.Id(refactorElement115)),
            ((refactorDecoded134)._2).map(refactorElement116 => Merge.Entity(refactorElement116)),
            ((refactorDecoded134)._3).map(refactorElement117 => Merge.Limit(refactorElement117))
          )
      )((refactorEncoded134: (Option[Merge.Id], Option[Merge.Entity], Option[Merge.Limit])) =>
        (
          ((refactorEncoded134)._1).map(refactorElement118 => (refactorElement118).value),
          ((refactorEncoded134)._2).map(refactorElement119 => (refactorElement119).value),
          ((refactorEncoded134)._3).map(refactorElement120 => (refactorElement120).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mergeDetail: PublicEndpoint[Merge.Id, Error, MergeDetail, Any] =
    (base.get
      .in("merges" / path[String]("id"))
      .out(jsonBody[MergeDetail]))
      .mapIn[Merge.Id]((refactorDecoded29: String) => Merge.Id(refactorDecoded29))((refactorEncoded29: Merge.Id) =>
        (refactorEncoded29).value
      )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class ArticleQuery(
      filter: Filter = Filter(),
      kind: Option[ArticleQuery.Kind] = None,
      text: Option[ArticleQuery.Text] = None
  )

  object ArticleQuery:
    export Article.Kind.Wire as Kind
    type Text = Text.T
    object Text extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val articles: PublicEndpoint[ArticleQuery, Error, Article.List, Any] =
    base.get
      .in("articles")
      .in(filterQuery)
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("text"))
      .mapIn { case (filter, kind, text) =>
        ArticleQuery(filter, kind.map(ArticleQuery.Kind(_)), text.map(ArticleQuery.Text(_)))
      }(v => (v.filter, v.kind.map(_.value), v.text.map(_.value)))
      .out(jsonBody[Article.List])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val renameArticle: PublicEndpoint[(ReviewGoal.Slug, LabelRename), Error, LabelRenamed, Any] =
    (base.post
      .in("articles" / path[String]("slug") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed]))
      .mapIn[(ReviewGoal.Slug, LabelRename)]((refactorDecoded136: (String, LabelRename)) =>
        (ReviewGoal.Slug((refactorDecoded136)._1), (refactorDecoded136)._2)
      )((refactorEncoded136: (ReviewGoal.Slug, LabelRename)) =>
        (((refactorEncoded136)._1).value, (refactorEncoded136)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val getArticle: PublicEndpoint[(ReviewGoal.Slug, Article.FullText), Error, Article.View, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug"))
      .in(
        query[Boolean]("brief")
          .default(false)
          .description("leave out the formatting lint and the coverage (a hover card's read)")
      )
      .errorOut(problemOut)
      .out(jsonBody[Article.View]))
      .mapIn[(ReviewGoal.Slug, Article.FullText)]((refactorDecoded137: (String, Boolean)) =>
        (ReviewGoal.Slug((refactorDecoded137)._1), Article.FullText((refactorDecoded137)._2))
      )((refactorEncoded137: (ReviewGoal.Slug, Article.FullText)) =>
        (((refactorEncoded137)._1).value, ((refactorEncoded137)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putArticle: PublicEndpoint[(ReviewGoal.Slug, Article.Put, Overwrite), Error, Article.View, Any] =
    (endpoint.put
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[Article.Put])
      .in(strictQuery)
      .errorOut(problemOut)
      .out(jsonBody[Article.View]))
      .mapIn[(ReviewGoal.Slug, Article.Put, Overwrite)]((refactorDecoded138: (String, Article.Put, Boolean)) =>
        (ReviewGoal.Slug((refactorDecoded138)._1), (refactorDecoded138)._2, Overwrite((refactorDecoded138)._3))
      )((refactorEncoded138: (ReviewGoal.Slug, Article.Put, Overwrite)) =>
        (((refactorEncoded138)._1).value, (refactorEncoded138)._2, ((refactorEncoded138)._3).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val patchArticle: PublicEndpoint[(ReviewGoal.Slug, Article.Patch, Apply), Error, Article.View, Any] =
    (endpoint.patch
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[Article.Patch])
      .in(strictQuery)
      .errorOut(problemOut)
      .out(jsonBody[Article.View]))
      .mapIn[(ReviewGoal.Slug, Article.Patch, Apply)]((refactorDecoded33: (String, Article.Patch, Boolean)) =>
        (ReviewGoal.Slug((refactorDecoded33)._1), (refactorDecoded33)._2, Apply((refactorDecoded33)._3))
      )((refactorEncoded33: (ReviewGoal.Slug, Article.Patch, Apply)) =>
        (((refactorEncoded33)._1).value, (refactorEncoded33)._2, ((refactorEncoded33)._3).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val lintArticle: PublicEndpoint[ReviewGoal.Slug, Error, FormatReport, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "lint")
      .errorOut(problemOut)
      .out(jsonBody[FormatReport]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded140: String) => ReviewGoal.Slug(refactorDecoded140))(
        (refactorEncoded140: ReviewGoal.Slug) => (refactorEncoded140).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val fixArticleFormat: PublicEndpoint[ReviewGoal.Slug, Error, FormatReport, Any] =
    (endpoint.post
      .in("api" / "articles" / path[String]("slug") / "lint" / "fix")
      .errorOut(problemOut)
      .out(jsonBody[FormatReport]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded35: String) => ReviewGoal.Slug(refactorDecoded35))(
        (refactorEncoded35: ReviewGoal.Slug) => (refactorEncoded35).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val checkArticle: PublicEndpoint[
    (ReviewGoal.Slug, Article.Edits.ArticleCheckRequest),
    Error,
    Article.Edits.ArticleCheck,
    Any
  ] =
    (endpoint.post
      .in("api" / "articles" / path[String]("slug") / "check")
      .in(jsonBody[Article.Edits.ArticleCheckRequest])
      .errorOut(problemOut)
      .out(jsonBody[Article.Edits.ArticleCheck]))
      .mapIn[(ReviewGoal.Slug, Article.Edits.ArticleCheckRequest)](
        (refactorDecoded142: (String, Article.Edits.ArticleCheckRequest)) =>
          (ReviewGoal.Slug((refactorDecoded142)._1), (refactorDecoded142)._2)
      )((refactorEncoded142: (ReviewGoal.Slug, Article.Edits.ArticleCheckRequest)) =>
        (((refactorEncoded142)._1).value, (refactorEncoded142)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val articleCoverage: PublicEndpoint[ReviewGoal.Slug, Error, Coverage, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "coverage")
      .errorOut(problemOut)
      .out(jsonBody[Coverage]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded37: String) => ReviewGoal.Slug(refactorDecoded37))(
        (refactorEncoded37: ReviewGoal.Slug) => (refactorEncoded37).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val articleReview: PublicEndpoint[ReviewGoal.Slug, Error, Article.ReviewView, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "review")
      .errorOut(problemOut)
      .out(jsonBody[Article.ReviewView]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded38: String) => ReviewGoal.Slug(refactorDecoded38))(
        (refactorEncoded38: ReviewGoal.Slug) => (refactorEncoded38).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val deleteArticle: PublicEndpoint[ReviewGoal.Slug, Error, Article.Deleted, Any] =
    (endpoint.delete
      .in("api" / "articles" / path[String]("slug"))
      .errorOut(problemOut)
      .out(jsonBody[Article.Deleted]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded145: String) => ReviewGoal.Slug(refactorDecoded145))(
        (refactorEncoded145: ReviewGoal.Slug) => (refactorEncoded145).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val exportArticle: PublicEndpoint[ReviewGoal.Slug, Error, Article.Export, Any] =
    (endpoint.get
      .in("api" / "articles" / path[String]("slug") / "export")
      .errorOut(problemOut)
      .out(stringBody))
      .mapIn[ReviewGoal.Slug]((refactorDecoded146: String) => ReviewGoal.Slug(refactorDecoded146))(
        (refactorEncoded146: ReviewGoal.Slug) => (refactorEncoded146).value
      )
      .mapOut[Article.Export]((refactorDecoded147: String) => Article.Export(refactorDecoded147))(
        (refactorEncoded147: Article.Export) => (refactorEncoded147).value
      )

  lazy val importArticle: PublicEndpoint[Article.Import, Error, SeedResult, Any] =
    base.post.in("articles" / "import").in(jsonBody[Article.Import]).out(jsonBody[SeedResult])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val articleVersions: PublicEndpoint[ReviewGoal.Slug, Error, List[Article.Version], Any] =
    (base.get
      .in("articles" / path[String]("slug") / "versions")
      .out(jsonBody[List[Article.Version]]))
      .mapIn[ReviewGoal.Slug]((refactorDecoded148: String) => ReviewGoal.Slug(refactorDecoded148))(
        (refactorEncoded148: ReviewGoal.Slug) => (refactorEncoded148).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val articleVersion: PublicEndpoint[(ReviewGoal.Slug, Article.VersionId), Error, Article.VersionFull, Any] =
    (base.get
      .in("articles" / path[String]("slug") / "versions" / path[Long]("versionId"))
      .out(jsonBody[Article.VersionFull]))
      .mapIn[(ReviewGoal.Slug, Article.VersionId)]((refactorDecoded149: (String, Long)) =>
        (ReviewGoal.Slug((refactorDecoded149)._1), Article.VersionId((refactorDecoded149)._2))
      )((refactorEncoded149: (ReviewGoal.Slug, Article.VersionId)) =>
        (((refactorEncoded149)._1).value, ((refactorEncoded149)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val articleSlugs: PublicEndpoint[Unit, Error, List[ReviewGoal.Slug], Any] =
    (base.get
      .in("article-slugs")
      .out(jsonBody[List[String]]))
      .mapOut[List[ReviewGoal.Slug]]((refactorDecoded28: List[String]) =>
        (refactorDecoded28).map(refactorElement17 => ReviewGoal.Slug(refactorElement17))
      )((refactorEncoded28: List[ReviewGoal.Slug]) =>
        (refactorEncoded28).map(refactorElement18 => (refactorElement18).value)
      )

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
    createArea,
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
  ) ++ OpsApi.all ++ AgentsApi.all ++ ManagersApi.all ++ AutopilotApi.switches ++ AcquisitionWorkApi.all ++
    ManagersApi.steering ++ AutopilotApi.draining ++ ManagersApi.stalled ++ UiApi.all ++ EventsApi.all ++ AcquireApi.all ++ BasemapApi.all ++ TilesApi.all ++ ConfigEditApi.all ++ PersonalApi.all ++
    CollectionsApi.all ++ LogApi.all ++ ResearchApi.all

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val places: PublicEndpoint[
    (Option[Place.Kind.Wire], Option[AggregateQuery.AsOf], Option[PlacesGeoQuery.Limit]),
    Error,
    List[
      Place.Hit
    ],
    Any
  ] =
    (base.get
      .in("places")
      .in(query[Option[String]]("q"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[Place.Hit]]))
      .mapIn[(Option[Place.Kind.Wire], Option[AggregateQuery.AsOf], Option[PlacesGeoQuery.Limit])](
        (refactorDecoded150: (Option[String], Option[Long], Option[Int])) =>
          (
            ((refactorDecoded150)._1).map(refactorElement121 => Place.Kind.Wire(refactorElement121)),
            ((refactorDecoded150)._2).map(refactorElement122 => AggregateQuery.AsOf(refactorElement122)),
            ((refactorDecoded150)._3).map(refactorElement123 => PlacesGeoQuery.Limit(refactorElement123))
          )
      )((refactorEncoded150: (Option[Place.Kind.Wire], Option[AggregateQuery.AsOf], Option[PlacesGeoQuery.Limit])) =>
        (
          ((refactorEncoded150)._1).map(refactorElement124 => (refactorElement124).value),
          ((refactorEncoded150)._2).map(refactorElement125 => (refactorElement125).value),
          ((refactorEncoded150)._3).map(refactorElement126 => (refactorElement126).value)
        )
      )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PlacesGeoQuery(
      kind: Option[PlacesGeoQuery.Kind] = None,
      asOf: Option[PlacesGeoQuery.AsOf] = None,
      limit: Option[PlacesGeoQuery.Limit] = None,
      from: Option[PlacesGeoQuery.From] = None,
      to: Option[PlacesGeoQuery.To] = None,
      pick: Option[PlacesGeoQuery.Pick] = None
  )

  object PlacesGeoQuery:
    export Place.Kind.Wire as Kind
    export AggregateQuery.AsOf
    export Paging.Limit
    type From = From.T
    object From extends Newtype[Long, Pure]
    type To = To.T
    object To extends Newtype[Long, Pure]
    export GeoPick.Wire as Pick

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placesGeo: PublicEndpoint[PlacesGeoQuery, Error, PlacesGeo.Json, Any] =
    (base.get
      .in("places" / "geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("asOf"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[String]]("pick"))
          .map { case (kind, asOf, limit, from, to, pick) =>
            PlacesGeoQuery(
              kind.map(PlacesGeoQuery.Kind(_)),
              asOf.map(PlacesGeoQuery.AsOf(_)),
              limit.map(PlacesGeoQuery.Limit(_)),
              from.map(PlacesGeoQuery.From(_)),
              to.map(PlacesGeoQuery.To(_)),
              pick.map(PlacesGeoQuery.Pick(_))
            )
          }(v =>
            (
              v.kind.map(_.value),
              v.asOf.map(_.value),
              v.limit.map(_.value),
              v.from.map(_.value),
              v.to.map(_.value),
              v.pick.map(_.value)
            )
          )
      )
      .out(stringBody))
      .mapOut[PlacesGeo.Json]((refactorDecoded14: String) => PlacesGeo.Json(refactorDecoded14))(
        (refactorEncoded14: PlacesGeo.Json) => (refactorEncoded14).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placeChildren: PublicEndpoint[(Place.Id, Option[AggregateQuery.AsOf]), Error, List[Place.Summary], Any] =
    (base.get
      .in("places" / path[String]("id") / "children")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[Place.Summary]]))
      .mapIn[(Place.Id, Option[AggregateQuery.AsOf])]((refactorDecoded45: (String, Option[Long])) =>
        (
          Place.Id((refactorDecoded45)._1),
          ((refactorDecoded45)._2).map(refactorElement35 => AggregateQuery.AsOf(refactorElement35))
        )
      )((refactorEncoded45: (Place.Id, Option[AggregateQuery.AsOf])) =>
        (((refactorEncoded45)._1).value, ((refactorEncoded45)._2).map(refactorElement36 => (refactorElement36).value))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placeTree
      : PublicEndpoint[(Place.Id, Option[AggregateQuery.AsOf], Option[Place.Depth]), Error, List[Place.TreeRow], Any] =
    (base.get
      .in("places" / path[String]("id") / "tree")
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[List[Place.TreeRow]]))
      .mapIn[(Place.Id, Option[AggregateQuery.AsOf], Option[Place.Depth])](
        (refactorDecoded152: (String, Option[Long], Option[Int])) =>
          (
            Place.Id((refactorDecoded152)._1),
            ((refactorDecoded152)._2).map(refactorElement129 => AggregateQuery.AsOf(refactorElement129)),
            ((refactorDecoded152)._3).map(refactorElement130 => Place.Depth(refactorElement130))
          )
      )((refactorEncoded152: (Place.Id, Option[AggregateQuery.AsOf], Option[Place.Depth])) =>
        (
          ((refactorEncoded152)._1).value,
          ((refactorEncoded152)._2).map(refactorElement131 => (refactorElement131).value),
          ((refactorEncoded152)._3).map(refactorElement132 => (refactorElement132).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val place
      : PublicEndpoint[(Place.Id, Option[AggregateQuery.AsOf], Option[Place.Detail]), Error, PlaceDetail, Any] =
    (base.get
      .in("places" / path[String]("id"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Boolean]]("counts"))
      .out(jsonBody[PlaceDetail]))
      .mapIn[(Place.Id, Option[AggregateQuery.AsOf], Option[Place.Detail])](
        (refactorDecoded47: (String, Option[Long], Option[Boolean])) =>
          (
            Place.Id((refactorDecoded47)._1),
            ((refactorDecoded47)._2).map(refactorElement41 => AggregateQuery.AsOf(refactorElement41)),
            ((refactorDecoded47)._3).map(refactorElement42 => Place.Detail(refactorElement42))
          )
      )((refactorEncoded47: (Place.Id, Option[AggregateQuery.AsOf], Option[Place.Detail])) =>
        (
          ((refactorEncoded47)._1).value,
          ((refactorEncoded47)._2).map(refactorElement43 => (refactorElement43).value),
          ((refactorEncoded47)._3).map(refactorElement44 => (refactorElement44).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placeFootprints: PublicEndpoint[(Place.Id, Option[AggregateQuery.AsOf]), Error, List[Place.Footprint], Any] =
    (base.get
      .in("places" / path[String]("id") / "footprints")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[Place.Footprint]]))
      .mapIn[(Place.Id, Option[AggregateQuery.AsOf])]((refactorDecoded154: (String, Option[Long])) =>
        (
          Place.Id((refactorDecoded154)._1),
          ((refactorDecoded154)._2).map(refactorElement137 => AggregateQuery.AsOf(refactorElement137))
        )
      )((refactorEncoded154: (Place.Id, Option[AggregateQuery.AsOf])) =>
        (
          ((refactorEncoded154)._1).value,
          ((refactorEncoded154)._2).map(refactorElement138 => (refactorElement138).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placeLinks: PublicEndpoint[Place.Id, Error, List[Place.Link], Any] =
    (base.get
      .in("places" / path[String]("id") / "links")
      .out(jsonBody[List[Place.Link]]))
      .mapIn[Place.Id]((refactorDecoded49: String) => Place.Id(refactorDecoded49))((refactorEncoded49: Place.Id) =>
        (refactorEncoded49).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val periods: PublicEndpoint[
    (
        Option[CorpusCounts.Periods.Source],
        Option[CorpusCounts.Periods.Collection],
        Option[PlacesGeoQuery.From],
        Option[PlacesGeoQuery.To]
    ),
    Error,
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
      .out(jsonBody[List[HistoricalPeriod]]))
      .mapIn[
        (
            Option[CorpusCounts.Periods.Source],
            Option[CorpusCounts.Periods.Collection],
            Option[PlacesGeoQuery.From],
            Option[PlacesGeoQuery.To]
        )
      ]((refactorDecoded156: (Option[String], Option[String], Option[Long], Option[Long])) =>
        (
          ((refactorDecoded156)._1).map(refactorElement139 => CorpusCounts.Periods.Source(refactorElement139)),
          ((refactorDecoded156)._2).map(refactorElement140 => CorpusCounts.Periods.Collection(refactorElement140)),
          ((refactorDecoded156)._3).map(refactorElement141 => PlacesGeoQuery.From(refactorElement141)),
          ((refactorDecoded156)._4).map(refactorElement142 => PlacesGeoQuery.To(refactorElement142))
        )
      )(
        (refactorEncoded156: (
            Option[CorpusCounts.Periods.Source],
            Option[CorpusCounts.Periods.Collection],
            Option[PlacesGeoQuery.From],
            Option[PlacesGeoQuery.To]
        )) =>
          (
            ((refactorEncoded156)._1).map(refactorElement143 => (refactorElement143).value),
            ((refactorEncoded156)._2).map(refactorElement144 => (refactorElement144).value),
            ((refactorEncoded156)._3).map(refactorElement145 => (refactorElement145).value),
            ((refactorEncoded156)._4).map(refactorElement146 => (refactorElement146).value)
          )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val period: PublicEndpoint[Period.Id, Error, Period.Detail, Any] =
    (base.get
      .in("periods" / path[String]("id"))
      .out(jsonBody[Period.Detail]))
      .mapIn[Period.Id]((refactorDecoded157: String) => Period.Id(refactorDecoded157))(
        (refactorEncoded157: Period.Id) => (refactorEncoded157).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val seedPeriods: PublicEndpoint[Overwrite, Error, Period.ImportResult, Any] =
    (base.post
      .in("periods" / "seed")
      .in(query[Boolean]("replace").default(false))
      .out(jsonBody[Period.ImportResult]))
      .mapIn[Overwrite]((refactorDecoded158: Boolean) => Overwrite(refactorDecoded158))(
        (refactorEncoded158: Overwrite) => (refactorEncoded158).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val importPeriods: PublicEndpoint[
    (List[CorpusCounts.Periods.Source], Option[CorpusCounts.Periods.Collection], CorpusCounts.Periods.Name),
    Error,
    Period.ImportResult,
    Any
  ] =
    (base.post
      .in("periods" / "import")
      .in(query[List[String]]("collection"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[Period.ImportResult]))
      .mapIn[(List[CorpusCounts.Periods.Source], Option[CorpusCounts.Periods.Collection], CorpusCounts.Periods.Name)](
        (refactorDecoded53: (List[String], Option[String], String)) =>
          (
            ((refactorDecoded53)._1).map(refactorElement55 => CorpusCounts.Periods.Source(refactorElement55)),
            ((refactorDecoded53)._2).map(refactorElement56 => CorpusCounts.Periods.Collection(refactorElement56)),
            CorpusCounts.Periods.Name((refactorDecoded53)._3)
          )
      )(
        (refactorEncoded53: (
            List[CorpusCounts.Periods.Source],
            Option[CorpusCounts.Periods.Collection],
            CorpusCounts.Periods.Name
        )) =>
          (
            ((refactorEncoded53)._1).map(refactorElement57 => (refactorElement57).value),
            ((refactorEncoded53)._2).map(refactorElement58 => (refactorElement58).value),
            ((refactorEncoded53)._3).value
          )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val fetchPeriods: PublicEndpoint[
    (CorpusCounts.Periods.Source, List[CorpusCounts.Periods.Collection]),
    Error,
    Period.ImportResult,
    Any
  ] =
    (base.post
      .in("periods" / "fetch")
      .in(query[String]("source"))
      .in(query[List[String]]("collection"))
      .out(jsonBody[Period.ImportResult]))
      .mapIn[(CorpusCounts.Periods.Source, List[CorpusCounts.Periods.Collection])](
        (refactorDecoded160: (String, List[String])) =>
          (
            CorpusCounts.Periods.Source((refactorDecoded160)._1),
            ((refactorDecoded160)._2).map(refactorElement151 => CorpusCounts.Periods.Collection(refactorElement151))
          )
      )((refactorEncoded160: (CorpusCounts.Periods.Source, List[CorpusCounts.Periods.Collection])) =>
        (
          ((refactorEncoded160)._1).value,
          ((refactorEncoded160)._2).map(refactorElement152 => (refactorElement152).value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val init: PublicEndpoint[Overwrite, Error, JobInfo, Any] =
    (base.post
      .in("init")
      .in(query[Boolean]("fetch").default(false))
      .out(jsonBody[JobInfo]))
      .mapIn[Overwrite]((refactorDecoded161: Boolean) => Overwrite(refactorDecoded161))(
        (refactorEncoded161: Overwrite) => (refactorEncoded161).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val importPlaces: PublicEndpoint[
    (Option[ExternalPlaces.Source], Option[ExternalPlaces.Locator], ExternalPlaces.Format),
    Error,
    Place.ImportResult,
    Any
  ] =
    (base.post
      .in("places" / "import")
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[Place.ImportResult]))
      .mapIn[(Option[ExternalPlaces.Source], Option[ExternalPlaces.Locator], ExternalPlaces.Format)](
        (refactorDecoded162: (Option[String], Option[String], String)) =>
          (
            ((refactorDecoded162)._1).map(refactorElement153 => ExternalPlaces.Source(refactorElement153)),
            ((refactorDecoded162)._2).map(refactorElement154 => ExternalPlaces.Locator(refactorElement154)),
            ExternalPlaces.Format((refactorDecoded162)._3)
          )
      )((refactorEncoded162: (Option[ExternalPlaces.Source], Option[ExternalPlaces.Locator], ExternalPlaces.Format)) =>
        (
          ((refactorEncoded162)._1).map(refactorElement155 => (refactorElement155).value),
          ((refactorEncoded162)._2).map(refactorElement156 => (refactorElement156).value),
          ((refactorEncoded162)._3).value
        )
      )

  lazy val rebuildPlaces: PublicEndpoint[Unit, Error, Place.RebuildResult, Any] =
    base.post.in("places" / "rebuild").out(jsonBody[Place.RebuildResult])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val fetchExternalPlaces: PublicEndpoint[(ExternalPlaces.Source, Apply), Error, Place.ImportResult, Any] =
    (base.post
      .in("places" / "fetch")
      .in(query[String]("source"))
      .in(query[Boolean]("dryRun"))
      .out(jsonBody[Place.ImportResult]))
      .mapIn[(ExternalPlaces.Source, Apply)]((refactorDecoded57: (String, Boolean)) =>
        (ExternalPlaces.Source((refactorDecoded57)._1), Apply((refactorDecoded57)._2))
      )((refactorEncoded57: (ExternalPlaces.Source, Apply)) =>
        (((refactorEncoded57)._1).value, ((refactorEncoded57)._2).value)
      )

  /** Declared before `places/{id}` so `sources` is never read as an id. */
  lazy val placeSources: PublicEndpoint[Unit, Error, List[Gazetteer.SourceInfo], Any] =
    base.get.in("places" / "sources").out(jsonBody[List[Gazetteer.SourceInfo]])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val loadPlaces: PublicEndpoint[(ExternalPlaces.Source, Apply), Error, JobInfo, Any] =
    (base.post
      .in("places" / "load")
      .in(query[String]("source"))
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[JobInfo]))
      .mapIn[(ExternalPlaces.Source, Apply)]((refactorDecoded58: (String, Boolean)) =>
        (ExternalPlaces.Source((refactorDecoded58)._1), Apply((refactorDecoded58)._2))
      )((refactorEncoded58: (ExternalPlaces.Source, Apply)) =>
        (((refactorEncoded58)._1).value, ((refactorEncoded58)._2).value)
      )

  /** Declared before `places/{id}` so `names` is never read as an id. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placeNames: PublicEndpoint[Option[Place.Id], Error, List[ManualPlaceName], Any] =
    (base.get
      .in("places" / "names")
      .in(query[Option[String]]("place"))
      .out(jsonBody[List[ManualPlaceName]]))
      .mapIn[Option[Place.Id]]((refactorDecoded59: Option[String]) =>
        (refactorDecoded59).map(refactorElement65 => Place.Id(refactorElement65))
      )((refactorEncoded59: Option[Place.Id]) =>
        (refactorEncoded59).map(refactorElement66 => (refactorElement66).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val addPlaceName: PublicEndpoint[(Place.Id, Place.NameAdd), Error, Place.NameChanged, Any] =
    (base.post
      .in("places" / path[String]("id") / "names")
      .in(jsonBody[Place.NameAdd])
      .out(jsonBody[Place.NameChanged]))
      .mapIn[(Place.Id, Place.NameAdd)]((refactorDecoded60: (String, Place.NameAdd)) =>
        (Place.Id((refactorDecoded60)._1), (refactorDecoded60)._2)
      )((refactorEncoded60: (Place.Id, Place.NameAdd)) => (((refactorEncoded60)._1).value, (refactorEncoded60)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val removePlaceName: PublicEndpoint[(Place.Id, Place.NameRemove), Error, Place.NameChanged, Any] =
    (base.post
      .in("places" / path[String]("id") / "names" / "remove")
      .in(jsonBody[Place.NameRemove])
      .out(jsonBody[Place.NameChanged]))
      .mapIn[(Place.Id, Place.NameRemove)]((refactorDecoded167: (String, Place.NameRemove)) =>
        (Place.Id((refactorDecoded167)._1), (refactorDecoded167)._2)
      )((refactorEncoded167: (Place.Id, Place.NameRemove)) =>
        (((refactorEncoded167)._1).value, (refactorEncoded167)._2)
      )

  /** Declared before `places/{id}` so `overrides` is never read as an id. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val placeOverrides: PublicEndpoint[Option[Place.Id], Error, List[Place.Override], Any] =
    (base.get
      .in("places" / "overrides")
      .in(query[Option[String]]("place"))
      .out(jsonBody[List[Place.Override]]))
      .mapIn[Option[Place.Id]]((refactorDecoded168: Option[String]) =>
        (refactorDecoded168).map(refactorElement159 => Place.Id(refactorElement159))
      )((refactorEncoded168: Option[Place.Id]) =>
        (refactorEncoded168).map(refactorElement160 => (refactorElement160).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val setPlaceOverride: PublicEndpoint[(Place.Id, Place.OverrideSet), Error, Place.OverrideChanged, Any] =
    (base.post
      .in("places" / path[String]("id") / "override")
      .in(jsonBody[Place.OverrideSet])
      .out(jsonBody[Place.OverrideChanged]))
      .mapIn[(Place.Id, Place.OverrideSet)]((refactorDecoded169: (String, Place.OverrideSet)) =>
        (Place.Id((refactorDecoded169)._1), (refactorDecoded169)._2)
      )((refactorEncoded169: (Place.Id, Place.OverrideSet)) =>
        (((refactorEncoded169)._1).value, (refactorEncoded169)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mergePlace: PublicEndpoint[(Place.Id, Place.MergeIn), Error, Place.Gone, Any] =
    (base.post
      .in("places" / path[String]("id") / "merge")
      .in(jsonBody[Place.MergeIn])
      .out(jsonBody[Place.Gone]))
      .mapIn[(Place.Id, Place.MergeIn)]((refactorDecoded64: (String, Place.MergeIn)) =>
        (Place.Id((refactorDecoded64)._1), (refactorDecoded64)._2)
      )((refactorEncoded64: (Place.Id, Place.MergeIn)) => (((refactorEncoded64)._1).value, (refactorEncoded64)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val removePlace: PublicEndpoint[(Place.Id, Place.RemoveIn), Error, Place.Gone, Any] =
    (base.post
      .in("places" / path[String]("id") / "remove")
      .in(jsonBody[Place.RemoveIn])
      .out(jsonBody[Place.Gone]))
      .mapIn[(Place.Id, Place.RemoveIn)]((refactorDecoded171: (String, Place.RemoveIn)) =>
        (Place.Id((refactorDecoded171)._1), (refactorDecoded171)._2)
      )((refactorEncoded171: (Place.Id, Place.RemoveIn)) => (((refactorEncoded171)._1).value, (refactorEncoded171)._2))

  lazy val addPlace: PublicEndpoint[Place.Add, Error, Place.Summary, Any] =
    base.post.in("places").in(jsonBody[Place.Add]).out(jsonBody[Place.Summary])

  lazy val buildHierarchy: PublicEndpoint[Unit, Error, SpatialHierarchyResult, Any] =
    base.post.in("places" / "build-hierarchy").out(jsonBody[SpatialHierarchyResult])

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def read(
        t: (
            Filter,
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Option[Boolean],
            Option[String]
        )
    ): EventQuery = t match
      case (filter, text, kind, historicity, placeId, tier, context, area) =>
        EventQuery(
          filter,
          text.map(Text(_)),
          kind.map(Kind(_)),
          historicity.map(Historicity(_)),
          placeId.map(PlaceId(_)),
          tier.map(Tier(_)),
          context.map(Context(_)),
          area.map(Area(_))
        )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def write(v: EventQuery): (
        Filter,
        Option[String],
        Option[String],
        Option[String],
        Option[String],
        Option[String],
        Option[Boolean],
        Option[String]
    ) = (
      v.filter,
      v.text.map(_.value),
      v.kind.map(_.value),
      v.historicity.map(_.value),
      v.placeId.map(_.value),
      v.tier.map(_.value),
      v.context.map(_.value),
      v.area.map(_.value)
    )

    type Text = Text.T
    object Text extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export catalogue.Historicity.Wire as Historicity
    export Place.Id as PlaceId
    type Tier = Tier.T
    object Tier extends Newtype[String, Pure]
    type Context = Context.T
    object Context extends Newtype[Boolean, Pure]
    type Area = Area.T
    object Area extends Newtype[String, Pure]

  lazy val events: PublicEndpoint[EventQuery, Error, EventList, Any] =
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
      .mapIn(EventQuery.read)(EventQuery.write)
      .out(jsonBody[EventList])

  lazy val eventsGeo: PublicEndpoint[EventQuery, Error, EventGeo.List, Any] =
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
      .mapIn(EventQuery.read)(EventQuery.write)
      .out(jsonBody[EventGeo.List])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val renameEvent: PublicEndpoint[(Event.Uid, LabelRename), Error, LabelRenamed, Any] =
    (base.post
      .in("events" / path[String]("id") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed]))
      .mapIn[(Event.Uid, LabelRename)]((refactorDecoded66: (String, LabelRename)) =>
        (Event.Uid((refactorDecoded66)._1), (refactorDecoded66)._2)
      )((refactorEncoded66: (Event.Uid, LabelRename)) => (((refactorEncoded66)._1).value, (refactorEncoded66)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val getEvent: PublicEndpoint[Event.Uid, Error, EventDetail, Any] =
    (endpoint.get
      .in("api" / "events" / path[String]("id"))
      .errorOut(problemOut)
      .out(jsonBody[EventDetail]))
      .mapIn[Event.Uid]((refactorDecoded67: String) => Event.Uid(refactorDecoded67))((refactorEncoded67: Event.Uid) =>
        (refactorEncoded67).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putEvent: PublicEndpoint[(Event.Uid, EventPut), Error, EventDetail, Any] =
    (endpoint.put
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPut])
      .errorOut(problemOut)
      .out(jsonBody[EventDetail]))
      .mapIn[(Event.Uid, EventPut)]((refactorDecoded174: (String, EventPut)) =>
        (Event.Uid((refactorDecoded174)._1), (refactorDecoded174)._2)
      )((refactorEncoded174: (Event.Uid, EventPut)) => (((refactorEncoded174)._1).value, (refactorEncoded174)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val patchEvent: PublicEndpoint[(Event.Uid, EventPatch), Error, EventDetail, Any] =
    (endpoint.patch
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPatch])
      .errorOut(problemOut)
      .out(jsonBody[EventDetail]))
      .mapIn[(Event.Uid, EventPatch)]((refactorDecoded175: (String, EventPatch)) =>
        (Event.Uid((refactorDecoded175)._1), (refactorDecoded175)._2)
      )((refactorEncoded175: (Event.Uid, EventPatch)) => (((refactorEncoded175)._1).value, (refactorEncoded175)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val deleteEvent: PublicEndpoint[Event.Uid, Error, EventDeleted, Any] =
    (endpoint.delete
      .in("api" / "events" / path[String]("id"))
      .errorOut(problemOut)
      .out(jsonBody[EventDeleted]))
      .mapIn[Event.Uid]((refactorDecoded176: String) => Event.Uid(refactorDecoded176))(
        (refactorEncoded176: Event.Uid) => (refactorEncoded176).value
      )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class AttestationQuery(
      citekey: Option[Citekey] = None,
      targetType: Option[AttestationQuery.TargetType] = None,
      targetId: Option[AttestationQuery.TargetId] = None,
      limit: Option[AttestationQuery.Limit] = None,
      offset: Option[AttestationQuery.Offset] = None
  )

  object AttestationQuery:
    type TargetType = TargetType.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object TargetType extends Newtype[String, Pure]
    type TargetId = TargetId.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object TargetId extends Newtype[String, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type Offset = Offset.T
    object Offset extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val attestations: PublicEndpoint[AttestationQuery, Error, Attestation.List, Any] =
    base.get
      .in("attestations")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("targetType"))
      .in(query[Option[String]]("targetId"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapIn { case (citekey, targetType, targetId, limit, offset) =>
        AttestationQuery(
          citekey.map(Citekey(_)),
          targetType.map(AttestationQuery.TargetType(_)),
          targetId.map(AttestationQuery.TargetId(_)),
          limit.map(AttestationQuery.Limit(_)),
          offset.map(AttestationQuery.Offset(_))
        )
      }(v =>
        (
          v.citekey.map(_.value),
          v.targetType.map(_.value),
          v.targetId.map(_.value),
          v.limit.map(_.value),
          v.offset.map(_.value)
        )
      )
      .out(jsonBody[Attestation.List])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val getAttestation: PublicEndpoint[Attestation.Id, Error, Attestation, Any] =
    (endpoint.get
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(problemOut)
      .out(jsonBody[Attestation]))
      .mapIn[Attestation.Id]((refactorDecoded71: Long) => Attestation.Id(refactorDecoded71))(
        (refactorEncoded71: Attestation.Id) => (refactorEncoded71).value
      )

  lazy val createAttestation: PublicEndpoint[Attestation.Put, Error, Attestation, Any] =
    endpoint.post
      .in("api" / "attestations")
      .in(jsonBody[Attestation.Put])
      .errorOut(problemOut)
      .out(jsonBody[Attestation])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val patchAttestation: PublicEndpoint[(Attestation.Id, Attestation.Patch), Error, Attestation, Any] =
    (endpoint.patch
      .in("api" / "attestations" / path[Long]("id"))
      .in(jsonBody[Attestation.Patch])
      .errorOut(problemOut)
      .out(jsonBody[Attestation]))
      .mapIn[(Attestation.Id, Attestation.Patch)]((refactorDecoded72: (Long, Attestation.Patch)) =>
        (Attestation.Id((refactorDecoded72)._1), (refactorDecoded72)._2)
      )((refactorEncoded72: (Attestation.Id, Attestation.Patch)) =>
        (((refactorEncoded72)._1).value, (refactorEncoded72)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val deleteAttestation: PublicEndpoint[Attestation.Id, Error, Attestation.Deleted, Any] =
    (endpoint.delete
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(problemOut)
      .out(jsonBody[Attestation.Deleted]))
      .mapIn[Attestation.Id]((refactorDecoded179: Long) => Attestation.Id(refactorDecoded179))(
        (refactorEncoded179: Attestation.Id) => (refactorEncoded179).value
      )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class GoalQuery(
      status: Option[GoalQuery.Status] = None,
      limit: Option[GoalQuery.Limit] = None,
      offset: Option[GoalQuery.Offset] = None
  )

  object GoalQuery:
    export Goal.StatusValue as Status
    export Paging.Limit
    type Offset = Offset.T
    object Offset extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val goals: PublicEndpoint[GoalQuery, Error, Goal.List, Any] =
    base.get
      .in("goals")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapIn { case (status, limit, offset) =>
        GoalQuery(status.map(GoalQuery.Status(_)), limit.map(GoalQuery.Limit(_)), offset.map(GoalQuery.Offset(_)))
      }(v => (v.status.map(_.value), v.limit.map(_.value), v.offset.map(_.value)))
      .out(jsonBody[Goal.List])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val renameGoal: PublicEndpoint[(Goal.Uid, LabelRename), Error, LabelRenamed, Any] =
    (base.post
      .in("goals" / path[String]("slug") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed]))
      .mapIn[(Goal.Uid, LabelRename)]((refactorDecoded180: (String, LabelRename)) =>
        (Goal.Uid((refactorDecoded180)._1), (refactorDecoded180)._2)
      )((refactorEncoded180: (Goal.Uid, LabelRename)) => (((refactorEncoded180)._1).value, (refactorEncoded180)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val renamePlace: PublicEndpoint[(Place.Id, LabelRename), Error, LabelRenamed, Any] =
    (base.post
      .in("places" / path[String]("id") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed]))
      .mapIn[(Place.Id, LabelRename)]((refactorDecoded181: (String, LabelRename)) =>
        (Place.Id((refactorDecoded181)._1), (refactorDecoded181)._2)
      )((refactorEncoded181: (Place.Id, LabelRename)) => (((refactorEncoded181)._1).value, (refactorEncoded181)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val getGoal: PublicEndpoint[Goal.Uid, Error, Goal, Any] =
    (endpoint.get
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(problemOut)
      .out(jsonBody[Goal]))
      .mapIn[Goal.Uid]((refactorDecoded182: String) => Goal.Uid(refactorDecoded182))((refactorEncoded182: Goal.Uid) =>
        (refactorEncoded182).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putGoal: PublicEndpoint[(Goal.Uid, Goal.Put), Error, Goal, Any] =
    (endpoint.put
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[Goal.Put])
      .errorOut(problemOut)
      .out(jsonBody[Goal]))
      .mapIn[(Goal.Uid, Goal.Put)]((refactorDecoded183: (String, Goal.Put)) =>
        (Goal.Uid((refactorDecoded183)._1), (refactorDecoded183)._2)
      )((refactorEncoded183: (Goal.Uid, Goal.Put)) => (((refactorEncoded183)._1).value, (refactorEncoded183)._2))

  /** A new research area in one operation ([[Goal.AreaCreate]]): create-only, all or nothing. */
  lazy val createArea: PublicEndpoint[Goal.AreaCreate, Error, Goal.AreaCreated, Any] =
    endpoint.post
      .in("api" / "goals" / "areas")
      .in(jsonBody[Goal.AreaCreate])
      .errorOut(problemOut)
      .out(jsonBody[Goal.AreaCreated])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val patchGoal: PublicEndpoint[(Goal.Uid, Goal.Patch), Error, Goal, Any] =
    (endpoint.patch
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[Goal.Patch])
      .errorOut(problemOut)
      .out(jsonBody[Goal]))
      .mapIn[(Goal.Uid, Goal.Patch)]((refactorDecoded78: (String, Goal.Patch)) =>
        (Goal.Uid((refactorDecoded78)._1), (refactorDecoded78)._2)
      )((refactorEncoded78: (Goal.Uid, Goal.Patch)) => (((refactorEncoded78)._1).value, (refactorEncoded78)._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val deleteGoal: PublicEndpoint[Goal.Uid, Error, Goal.Deleted, Any] =
    (endpoint.delete
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(problemOut)
      .out(jsonBody[Goal.Deleted]))
      .mapIn[Goal.Uid]((refactorDecoded185: String) => Goal.Uid(refactorDecoded185))((refactorEncoded185: Goal.Uid) =>
        (refactorEncoded185).value
      )

  lazy val agentStateList: PublicEndpoint[Unit, Error, List[AgentStateEntry], Any] =
    base.get.in("agent-state").out(jsonBody[List[AgentStateEntry]])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val agentStateGet: PublicEndpoint[AgentState.Key, Error, AgentStateEntry, Any] =
    (endpoint.get
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(problemOut)
      .out(jsonBody[AgentStateEntry]))
      .mapIn[AgentState.Key]((refactorDecoded80: String) => AgentState.Key(refactorDecoded80))(
        (refactorEncoded80: AgentState.Key) => (refactorEncoded80).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val agentStateSet: PublicEndpoint[(AgentState.Key, AgentStatePut), Error, AgentStateEntry, Any] =
    (endpoint.put
      .in("api" / "agent-state" / path[String]("key"))
      .in(jsonBody[AgentStatePut])
      .errorOut(problemOut)
      .out(jsonBody[AgentStateEntry]))
      .mapIn[(AgentState.Key, AgentStatePut)]((refactorDecoded187: (String, AgentStatePut)) =>
        (AgentState.Key((refactorDecoded187)._1), (refactorDecoded187)._2)
      )((refactorEncoded187: (AgentState.Key, AgentStatePut)) =>
        (((refactorEncoded187)._1).value, (refactorEncoded187)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val agentStateDelete: PublicEndpoint[AgentState.Key, Error, AgentStateDeleted, Any] =
    (endpoint.delete
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(problemOut)
      .out(jsonBody[AgentStateDeleted]))
      .mapIn[AgentState.Key]((refactorDecoded82: String) => AgentState.Key(refactorDecoded82))(
        (refactorEncoded82: AgentState.Key) => (refactorEncoded82).value
      )

  /** Query parameters, not a multipart part list: tapir's netty server has no multipart request-body support
    * (`RawBodyType.MultipartBody` throws `UnsupportedOperationException`), so the document travels as the raw body.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val ingestParams: EndpointInput[Ingest.Params] =
    query[String]("citekey")
      .and(query[Option[String]]("filename"))
      .and(query[Boolean]("quality").default(false))
      .and(query[Boolean]("force").default(false))
      .and(query[Option[String]]("title"))
      .and(query[List[String]]("author"))
      .and(query[Option[Long]]("year"))
      .and(query[Option[String]]("doi"))
      .and(query[Option[String]]("provenance"))
      .and(
        query[Option[Int]]("acquireItem")
          .description("id of the acquisition item this upload fulfils; the OCR job ticks it off when it succeeds")
      )
      .and(creatorQuery)
      .map { case (citekey, filename, quality, force, title, authors, year, doi, provenance, acquireItem, by, via) =>
        Ingest.Params(
          Citekey(citekey),
          filename.map(Ingest.Params.Filename(_)),
          Ingest.Params.Quality(quality),
          Ingest.Params.Force(force),
          title.map(Title(_)),
          authors.map(AuthorName(_)),
          year.map(catalogue.Year(_)),
          doi.map(Doi(_)),
          provenance.map(Ingest.Params.Provenance(_)),
          acquireItem.map(Ingest.Params.AcquireItem(_)),
          by,
          via
        )
      }(v =>
        (
          v.citekey.value,
          v.filename.map(_.value),
          v.quality.value,
          v.force.value,
          v.title.map(_.value),
          v.authors.map(_.value),
          v.year.map(_.value),
          v.doi.map(_.value),
          v.provenance.map(_.value),
          v.acquireItem.map(_.value),
          v.createdBy,
          v.via
        )
      )

  lazy val creatorQuery: EndpointInput[(Option[automation.Creation.By], Option[automation.Creation.Via])] =
    query[Option[automation.Creation.Plugin]]("byPlugin")
      .and(query[Option[automation.Creation.Workflow]]("byWorkflow"))
      .and(query[Option[automation.Creation.Definition]]("byAgentDefinition"))
      .and(query[Option[automation.Creation.Provider]]("byProvider"))
      .and(query[Option[automation.Creation.Model]]("byModel"))
      .and(query[Option[automation.Creation.Via]]("via"))
      .map { case (plugin, workflow, definition, provider, model, via) =>
        val by = Option.when(List(plugin, workflow, definition, provider, model).exists(_.isDefined))(
          automation.Creation.By(plugin, workflow, definition, provider, model)
        )
        (by, via)
      } { case (by, via) =>
        (
          by.flatMap(_.plugin),
          by.flatMap(_.workflow),
          by.flatMap(_.agentDefinition),
          by.flatMap(_.provider),
          by.flatMap(_.model),
          via
        )
      }

  lazy val ingest: PublicEndpoint[(Ingest.Params, TapirFile), Error, Ingest.Accepted, Any] =
    endpoint.post
      .in("api" / "ingest")
      .in(ingestParams)
      .in(fileBody)
      .errorOut(problemOut)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Ingest.Accepted])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val reocr: PublicEndpoint[(Citekey, Overwrite), Error, Ingest.Accepted, Any] =
    (endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "reocr")
      .in(query[Boolean]("quality").default(false))
      .errorOut(problemOut)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Ingest.Accepted]))
      .mapIn[(Citekey, Overwrite)]((refactorDecoded189: (String, Boolean)) =>
        (Citekey((refactorDecoded189)._1), Overwrite((refactorDecoded189)._2))
      )((refactorEncoded189: (Citekey, Overwrite)) =>
        (((refactorEncoded189)._1).value, ((refactorEncoded189)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val original: PublicEndpoint[Citekey, Error, (TapirFile, FileName), Any] =
    (endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "original")
      .errorOut(problemOut)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition)))
      .mapIn[Citekey]((refactorDecoded: String) => Citekey(refactorDecoded))((refactorEncoded: Citekey) =>
        (refactorEncoded).value
      )
      .mapOut[(TapirFile, FileName)]((refactorDecoded: (TapirFile, String)) =>
        (refactorDecoded._1, FileName(refactorDecoded._2))
      )((refactorEncoded: (TapirFile, FileName)) => (refactorEncoded._1, (refactorEncoded._2).value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val job: PublicEndpoint[SpatialHierarchyResult.Job.Id, Error, JobInfo, Any] =
    (base.get
      .in("jobs" / path[String]("id"))
      .out(jsonBody[JobInfo]))
      .mapIn[SpatialHierarchyResult.Job.Id]((refactorDecoded192: String) =>
        SpatialHierarchyResult.Job.Id(refactorDecoded192)
      )((refactorEncoded192: SpatialHierarchyResult.Job.Id) => (refactorEncoded192).value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val jobControl: PublicEndpoint[(SpatialHierarchyResult.Job.Id, JobControl.Action), Error, JobInfo, Any] =
    (endpoint.post
      .in("api" / "jobs" / path[String]("id") / path[String]("action"))
      .errorOut(problemOut)
      .out(jsonBody[JobInfo]))
      .mapIn[(SpatialHierarchyResult.Job.Id, JobControl.Action)]((refactorDecoded: (String, String)) =>
        (SpatialHierarchyResult.Job.Id(refactorDecoded._1), JobControl.Action(refactorDecoded._2))
      )((refactorEncoded: (SpatialHierarchyResult.Job.Id, JobControl.Action)) =>
        ((refactorEncoded._1).value, (refactorEncoded._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val activity: PublicEndpoint[
    (
        Option[Activity.Kind],
        Option[Activity.Actor],
        Option[Activity.Subject],
        Option[Activity.Limit],
        Option[Activity.Cursor]
    ),
    Error,
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
      .out(jsonBody[Activity.Page]))
      .mapIn[
        (
            Option[Activity.Kind],
            Option[Activity.Actor],
            Option[Activity.Subject],
            Option[Activity.Limit],
            Option[Activity.Cursor]
        )
      ]((refactorDecoded193: (Option[String], Option[String], Option[String], Option[Int], Option[String])) =>
        (
          ((refactorDecoded193)._1).map(refactorElement161 => Activity.Kind(refactorElement161)),
          ((refactorDecoded193)._2).map(refactorElement162 => Activity.Actor(refactorElement162)),
          ((refactorDecoded193)._3).map(refactorElement163 => Activity.Subject(refactorElement163)),
          ((refactorDecoded193)._4).map(refactorElement164 => Activity.Limit(refactorElement164)),
          ((refactorDecoded193)._5).map(refactorElement165 => Activity.Cursor(refactorElement165))
        )
      )(
        (refactorEncoded193: (
            Option[Activity.Kind],
            Option[Activity.Actor],
            Option[Activity.Subject],
            Option[Activity.Limit],
            Option[Activity.Cursor]
        )) =>
          (
            ((refactorEncoded193)._1).map(refactorElement166 => (refactorElement166).value),
            ((refactorEncoded193)._2).map(refactorElement167 => (refactorElement167).value),
            ((refactorEncoded193)._3).map(refactorElement168 => (refactorElement168).value),
            ((refactorEncoded193)._4).map(refactorElement169 => (refactorElement169).value),
            ((refactorEncoded193)._5).map(refactorElement170 => (refactorElement170).value)
          )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val jobs: PublicEndpoint[
    (ListFilters.JobFilter, Option[Jobs.Limit], Option[Jobs.Status], Option[api.Jobs.Filter]),
    Error,
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
      .out(Paging.headers))
      .mapIn[(ListFilters.JobFilter, Option[Jobs.Limit], Option[Jobs.Status], Option[api.Jobs.Filter])](
        (refactorDecoded194: (ListFilters.JobFilter, Option[Int], Option[String], Option[String])) =>
          (
            (refactorDecoded194)._1,
            ((refactorDecoded194)._2).map(refactorElement171 => Jobs.Limit(refactorElement171)),
            ((refactorDecoded194)._3).map(refactorElement172 => Jobs.Status(refactorElement172)),
            ((refactorDecoded194)._4).map(refactorElement173 => api.Jobs.Filter(refactorElement173))
          )
      )(
        (refactorEncoded194: (
            ListFilters.JobFilter,
            Option[Jobs.Limit],
            Option[Jobs.Status],
            Option[api.Jobs.Filter]
        )) =>
          (
            (refactorEncoded194)._1,
            ((refactorEncoded194)._2).map(refactorElement174 => (refactorElement174).value),
            ((refactorEncoded194)._3).map(refactorElement175 => (refactorElement175).value),
            ((refactorEncoded194)._4).map(refactorElement176 => (refactorElement176).value)
          )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val assets: PublicEndpoint[Assets.Kind, Error, List[AssetInfo], Any] =
    (base.get
      .in("sources" / path[String]("citekey") / "assets")
      .out(jsonBody[List[AssetInfo]]))
      .mapIn[Assets.Kind]((refactorDecoded89: String) => Assets.Kind(refactorDecoded89))(
        (refactorEncoded89: Assets.Kind) => (refactorEncoded89).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putAssets: PublicEndpoint[(Assets.Owner, List[AssetInput]), Error, AssetsStored, Any] =
    (endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "assets")
      .in(jsonBody[List[AssetInput]])
      .errorOut(problemOut)
      .out(jsonBody[AssetsStored]))
      .mapIn[(Assets.Owner, List[AssetInput])]((refactorDecoded90: (String, List[AssetInput])) =>
        (Assets.Owner((refactorDecoded90)._1), (refactorDecoded90)._2)
      )((refactorEncoded90: (Assets.Owner, List[AssetInput])) =>
        (((refactorEncoded90)._1).value, (refactorEncoded90)._2)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val asset: PublicEndpoint[(Bundle.Asset.Uid, Bundle.Asset.Variant), Error, (FileBytes, ContentType), Any] =
    (endpoint.get
      .in("source" / path[String]("citekey") / "assets" / path[String]("name"))
      .errorOut(problemOut)
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.AssetCacheControl))))
      .mapIn[(Bundle.Asset.Uid, Bundle.Asset.Variant)]((refactorDecoded91: (String, String)) =>
        (Bundle.Asset.Uid((refactorDecoded91)._1), Bundle.Asset.Variant((refactorDecoded91)._2))
      )((refactorEncoded91: (Bundle.Asset.Uid, Bundle.Asset.Variant)) =>
        (((refactorEncoded91)._1).value, ((refactorEncoded91)._2).value)
      )
      .mapOut[(FileBytes, ContentType)]((refactorDecoded92: (Array[Byte], String)) =>
        (FileBytes((refactorDecoded92)._1), ContentType((refactorDecoded92)._2))
      )((refactorEncoded92: (FileBytes, ContentType)) =>
        (((refactorEncoded92)._1).value, ((refactorEncoded92)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val assetDetail: PublicEndpoint[(Bundle.Asset.Uid, Bundle.Asset.Variant), Error, AssetDetail, Any] =
    (base.get
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name") / "detail")
      .out(jsonBody[AssetDetail]))
      .mapIn[(Bundle.Asset.Uid, Bundle.Asset.Variant)]((refactorDecoded93: (String, String)) =>
        (Bundle.Asset.Uid((refactorDecoded93)._1), Bundle.Asset.Variant((refactorDecoded93)._2))
      )((refactorEncoded93: (Bundle.Asset.Uid, Bundle.Asset.Variant)) =>
        (((refactorEncoded93)._1).value, ((refactorEncoded93)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val patchAsset: PublicEndpoint[(Bundle.Asset.Uid, Bundle.Asset.Variant, AssetPatch), Error, AssetDetail, Any] =
    (base.patch
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name"))
      .in(jsonBody[AssetPatch])
      .out(jsonBody[AssetDetail]))
      .mapIn[(Bundle.Asset.Uid, Bundle.Asset.Variant, AssetPatch)]((refactorDecoded200: (String, String, AssetPatch)) =>
        (
          Bundle.Asset.Uid((refactorDecoded200)._1),
          Bundle.Asset.Variant((refactorDecoded200)._2),
          (refactorDecoded200)._3
        )
      )((refactorEncoded200: (Bundle.Asset.Uid, Bundle.Asset.Variant, AssetPatch)) =>
        (((refactorEncoded200)._1).value, ((refactorEncoded200)._2).value, (refactorEncoded200)._3)
      )

  final case class MediaGeoQuery(
      kind: Option[MediaGeoQuery.Kind] = None,
      from: Option[MediaGeoQuery.From] = None,
      to: Option[MediaGeoQuery.To] = None,
      limit: Option[MediaGeoQuery.Limit] = None,
      provider: Option[MediaGeoQuery.Provider] = None,
      license: Option[MediaGeoQuery.License] = None
  )

  object MediaGeoQuery:
    export MediaItem.Kind
    type From = From.T
    object From extends Newtype[Long, Pure]
    type To = To.T
    object To extends Newtype[Long, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    export MediaProvider.Id as Provider
    type License = License.T
    object License extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mediaGeo: PublicEndpoint[MediaGeoQuery, Error, List[MediaGeoHit], Any] =
    base.get
      .in("media-geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[String]]("provider"))
          .and(query[Option[String]]("license"))
          .map { case (kind, from, to, limit, provider, license) =>
            MediaGeoQuery(
              kind.map(MediaGeoQuery.Kind(_)),
              from.map(MediaGeoQuery.From(_)),
              to.map(MediaGeoQuery.To(_)),
              limit.map(MediaGeoQuery.Limit(_)),
              provider.map(MediaGeoQuery.Provider(_)),
              license.map(MediaGeoQuery.License(_))
            )
          }(v =>
            (
              v.kind.map(_.value),
              v.from.map(_.value),
              v.to.map(_.value),
              v.limit.map(_.value),
              v.provider.map(_.value),
              v.license.map(_.value)
            )
          )
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
    export MediaProvider.Id as Provider
    type License = License.T
    object License extends Newtype[String, Pure]
    export catalogue.Place.Id as Place
    export catalogue.Event.Id as Event
    type Q = Q.T
    object Q extends Newtype[String, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type Offset = Offset.T
    object Offset extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mediaList: PublicEndpoint[MediaQuery, Error, MediaList, Any] =
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
          .map { case (provider, license, place, event, q, limit, offset) =>
            MediaQuery(
              provider.map(MediaQuery.Provider(_)),
              license.map(MediaQuery.License(_)),
              place.map(MediaQuery.Place(_)),
              event.map(MediaQuery.Event(_)),
              q.map(MediaQuery.Q(_)),
              limit.map(MediaQuery.Limit(_)),
              offset.map(MediaQuery.Offset(_))
            )
          }(v =>
            (
              v.provider.map(_.value),
              v.license.map(_.value),
              v.place.map(_.value),
              v.event.map(_.value),
              v.q.map(_.value),
              v.limit.map(_.value),
              v.offset.map(_.value)
            )
          )
      )
      .out(jsonBody[MediaList])

  lazy val mediaFacets: PublicEndpoint[Unit, Error, MediaFacets, Any] =
    base.get.in("media" / "facets").out(jsonBody[MediaFacets])

  lazy val mediaProviders: PublicEndpoint[Unit, Error, List[MediaProvider.Info], Any] =
    base.get.in("media" / "providers").out(jsonBody[List[MediaProvider.Info]])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mediaShow: PublicEndpoint[Bundle.Asset.Uid, Error, MediaItem, Any] =
    (base.get
      .in("media" / path[String]("uid"))
      .out(jsonBody[MediaItem]))
      .mapIn[Bundle.Asset.Uid]((refactorDecoded95: String) => Bundle.Asset.Uid(refactorDecoded95))(
        (refactorEncoded95: Bundle.Asset.Uid) => (refactorEncoded95).value
      )

  lazy val mediaImport: PublicEndpoint[MediaImportRequest, Error, MediaImportReport, Any] =
    base.post.in("media" / "import").in(jsonBody[MediaImportRequest]).out(jsonBody[MediaImportReport])

  lazy val mediaImportJob: PublicEndpoint[MediaImportRequest, Error, JobInfo, Any] =
    base.post
      .in("media" / "import" / "job")
      .in(jsonBody[MediaImportRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val mediaCovers: PublicEndpoint[MediaCoversRequest, Error, MediaCoversReport, Any] =
    base.post.in("media" / "covers").in(jsonBody[MediaCoversRequest]).out(jsonBody[MediaCoversReport])

  lazy val mediaCoversJob: PublicEndpoint[MediaCoversRequest, Error, JobInfo, Any] =
    base.post
      .in("media" / "covers" / "job")
      .in(jsonBody[MediaCoversRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val putSourceCover: PublicEndpoint[(SourceCoverParams, FileBytes), Error, MediaItem, Any] =
    (base.put
      .in("media" / "sources" / path[String]("citekey") / "cover")
      .in(query[String]("provider").default(MediaProvider.Calibre))
      .in(query[String]("key"))
      .in(query[Option[String]]("mime"))
      .mapIn { case (citekey, provider, key, mime) =>
        SourceCoverParams(
          Citekey(citekey),
          MediaItem.Provider(provider),
          SourceCoverParams.Key(key),
          mime.map(Bundle.Asset.MimeText(_))
        )
      }(v => (v.citekey.value, v.provider.value, v.key.value, v.mime.map(_.value)))
      .in(byteArrayBody)
      .out(jsonBody[MediaItem]))
      .mapIn[(SourceCoverParams, FileBytes)]((refactorDecoded96: (SourceCoverParams, Array[Byte])) =>
        ((refactorDecoded96)._1, FileBytes((refactorDecoded96)._2))
      )((refactorEncoded96: (SourceCoverParams, FileBytes)) => ((refactorEncoded96)._1, ((refactorEncoded96)._2).value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val fillFigureLicences: PublicEndpoint[Apply, Error, FigureLicenceFill, Any] =
    (base.post
      .in("media" / "fill-licences")
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[FigureLicenceFill]))
      .mapIn[Apply]((refactorDecoded97: Boolean) => Apply(refactorDecoded97))((refactorEncoded97: Apply) =>
        (refactorEncoded97).value
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mediaThumb: PublicEndpoint[Bundle.Asset.Uid, Error, (FileBytes, ContentType), Any] =
    (endpoint.get
      .in("media" / path[String]("uid") / "thumb")
      .errorOut(problemOut)
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl))))
      .mapIn[Bundle.Asset.Uid]((refactorDecoded204: String) => Bundle.Asset.Uid(refactorDecoded204))(
        (refactorEncoded204: Bundle.Asset.Uid) => (refactorEncoded204).value
      )
      .mapOut[(FileBytes, ContentType)]((refactorDecoded205: (Array[Byte], String)) =>
        (FileBytes((refactorDecoded205)._1), ContentType((refactorDecoded205)._2))
      )((refactorEncoded205: (FileBytes, ContentType)) =>
        (((refactorEncoded205)._1).value, ((refactorEncoded205)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val mediaFile: PublicEndpoint[Bundle.Asset.Uid, Error, (FileBytes, ContentType), Any] =
    (endpoint.get
      .in("media" / path[String]("uid") / "file")
      .errorOut(problemOut)
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl))))
      .mapIn[Bundle.Asset.Uid]((refactorDecoded100: String) => Bundle.Asset.Uid(refactorDecoded100))(
        (refactorEncoded100: Bundle.Asset.Uid) => (refactorEncoded100).value
      )
      .mapOut[(FileBytes, ContentType)]((refactorDecoded101: (Array[Byte], String)) =>
        (FileBytes((refactorDecoded101)._1), ContentType((refactorDecoded101)._2))
      )((refactorEncoded101: (FileBytes, ContentType)) =>
        (((refactorEncoded101)._1).value, ((refactorEncoded101)._2).value)
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val assetLinks: PublicEndpoint[
    (Option[Bundle.Asset.Uid], Option[AssetLinks.Kind], Option[AssetLinks.TargetId], Option[AssetLinks.TargetKind]),
    Error,
    AssetLink.List,
    Any
  ] =
    (base.get
      .in("asset-links")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("asset"))
      .in(query[Option[String]]("target_type"))
      .in(query[Option[String]]("target_id"))
      .out(jsonBody[AssetLink.List]))
      .mapIn[
        (Option[Bundle.Asset.Uid], Option[AssetLinks.Kind], Option[AssetLinks.TargetId], Option[AssetLinks.TargetKind])
      ]((refactorDecoded208: (Option[String], Option[String], Option[String], Option[String])) =>
        (
          ((refactorDecoded208)._1).map(refactorElement177 => Bundle.Asset.Uid(refactorElement177)),
          ((refactorDecoded208)._2).map(refactorElement178 => AssetLinks.Kind(refactorElement178)),
          ((refactorDecoded208)._3).map(refactorElement179 => AssetLinks.TargetId(refactorElement179)),
          ((refactorDecoded208)._4).map(refactorElement180 => AssetLinks.TargetKind(refactorElement180))
        )
      )(
        (refactorEncoded208: (
            Option[Bundle.Asset.Uid],
            Option[AssetLinks.Kind],
            Option[AssetLinks.TargetId],
            Option[AssetLinks.TargetKind]
        )) =>
          (
            ((refactorEncoded208)._1).map(refactorElement181 => (refactorElement181).value),
            ((refactorEncoded208)._2).map(refactorElement182 => (refactorElement182).value),
            ((refactorEncoded208)._3).map(refactorElement183 => (refactorElement183).value),
            ((refactorEncoded208)._4).map(refactorElement184 => (refactorElement184).value)
          )
      )

  lazy val createAssetLink: PublicEndpoint[AssetLink.Put, Error, AssetLink, Any] =
    base.post.in("asset-links").in(jsonBody[AssetLink.Put]).out(jsonBody[AssetLink])

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val deleteAssetLink: PublicEndpoint[AssetLink.Id, Error, AssetLink.Deleted, Any] =
    (base.delete
      .in("asset-links" / path[Long]("id"))
      .out(jsonBody[AssetLink.Deleted]))
      .mapIn[AssetLink.Id]((refactorDecoded103: Long) => AssetLink.Id(refactorDecoded103))(
        (refactorEncoded103: AssetLink.Id) => (refactorEncoded103).value
      )

  /** Registered before the image endpoint so Tapir matches it first. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val iiifInfo: PublicEndpoint[(Bundle.Asset.Uid, Bundle.Asset.Variant), Error, Json, Any] =
    (endpoint.get
      .in("iiif" / path[String]("citekey") / path[String]("name") / "info.json")
      .errorOut(problemOut)
      .out(jsonBody[Json])
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*"))))
      .mapIn[(Bundle.Asset.Uid, Bundle.Asset.Variant)]((refactorDecoded104: (String, String)) =>
        (Bundle.Asset.Uid((refactorDecoded104)._1), Bundle.Asset.Variant((refactorDecoded104)._2))
      )((refactorEncoded104: (Bundle.Asset.Uid, Bundle.Asset.Variant)) =>
        (((refactorEncoded104)._1).value, ((refactorEncoded104)._2).value)
      )

  /** IIIF Image API Level 1: region `full|x,y,w,h`, size `max|^max|w,|,h|w,h|!w,h`, rotation `0` only, quality
    * `default`, format `jpg|png`.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val iiifImage: PublicEndpoint[
    (Bundle.Asset.Uid, Iiif.RegionText, Iiif.SizeText, Iiif.Rotation, Iiif.Quality, Iiif.Format),
    Error,
    (FileBytes, ContentType),
    Any
  ] =
    (endpoint.get
      .in(
        "iiif" / path[String]("citekey") / path[String]("name") /
          path[String]("region") / path[String]("size") / path[String]("rotation") /
          path[String]("filename")
      )
      .errorOut(problemOut)
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, "public, max-age=31536000, immutable"))))
      .mapIn[(Bundle.Asset.Uid, Iiif.RegionText, Iiif.SizeText, Iiif.Rotation, Iiif.Quality, Iiif.Format)](
        (refactorDecoded211: (String, String, String, String, String, String)) =>
          (
            Bundle.Asset.Uid((refactorDecoded211)._1),
            Iiif.RegionText((refactorDecoded211)._2),
            Iiif.SizeText((refactorDecoded211)._3),
            Iiif.Rotation((refactorDecoded211)._4),
            Iiif.Quality((refactorDecoded211)._5),
            Iiif.Format((refactorDecoded211)._6)
          )
      )(
        (refactorEncoded211: (
            Bundle.Asset.Uid,
            Iiif.RegionText,
            Iiif.SizeText,
            Iiif.Rotation,
            Iiif.Quality,
            Iiif.Format
        )) =>
          (
            ((refactorEncoded211)._1).value,
            ((refactorEncoded211)._2).value,
            ((refactorEncoded211)._3).value,
            ((refactorEncoded211)._4).value,
            ((refactorEncoded211)._5).value,
            ((refactorEncoded211)._6).value
          )
      )
      .mapOut[(FileBytes, ContentType)]((refactorDecoded212: (Array[Byte], String)) =>
        (FileBytes((refactorDecoded212)._1), ContentType((refactorDecoded212)._2))
      )((refactorEncoded212: (FileBytes, ContentType)) =>
        (((refactorEncoded212)._1).value, ((refactorEncoded212)._2).value)
      )

  /** A failed request: its problems, the first deciding the status ([[status]]), and the code a client places the
    * refusal by (the citekey, the field of a patch, a collection's usage). The body is
    * `{"problems": [...], "code": ...}`.
    */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class Error(problems: NonEmptyVector[Problem], code: Option[Error.Code])
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object Error:

    /** A request that failed with `problems`. */
    def apply(problems: Problems): Error = Error(problems.toNonEmpty, None)

    /** A request that failed with `problems`, with the code a client places the refusal by. */
    def apply(problems: Problems, code: Option[Code]): Error = Error(problems.toNonEmpty, code)

    type Code = Code.T
    object Code extends Newtype[String, Pure]

    /** The code of a refused citekey. */
    val InvalidCitekey: Code = Code("invalid_citekey")

    /** The field of a source a refused patch is about, as the code the refusal carries. */
    enum Field(val code: Code):
      case Period extends Field(Code("invalid_period"))
      case Status extends Field(Code("invalid_status"))

    extension (e: Error)
      /** The server answered "not found" (an unknown citekey, id or slug). */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def isNotFound: Boolean =
        NonEmptyVector.toVector(e.problems).exists(_.isNotFound) || e.code.contains(Code(Write.CodeNotFound))

      /** The server refused the citekey of the request. */
      def isInvalidCitekey: Boolean = e.code.contains(InvalidCitekey)

      /** The field of the source the server refused a patch for, when the refusal names one. */
      def field: Option[Field] = e.code.flatMap(c => Field.values.find(_.code == c))

      /** The status of the answer: the first problem's, by its group ([[Answer.status]]). TEMPORARY (G60 step 5, goes
        * with the codes in step 7): a site that still gives a code answering other than 400 keeps that code's status
        * ([[CodeStatus]]), as the six hand-written error outputs answered it.
        */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def status: StatusCode = e.code.flatMap(c => CodeStatus.get(s"$c")).getOrElse(Answer.status(e.problems: Problems))

    /** TEMPORARY (G60 step 5): the codes the six error outputs answered other than 400 by, and their statuses; and a
      * refused settings change (`settings_refused`), whose problems are the proposed settings' (`Config.Setting`, which
      * answers 503 for a server whose own settings are broken), answered 400 as the request's fault.
      */
    private val CodeStatus: Map[String, StatusCode] = Map(
      Write.CodeNotFound -> StatusCode.NotFound,
      Write.CodePrecondition -> StatusCode.PreconditionFailed,
      Backup.CodeMaintenance -> StatusCode.ServiceUnavailable,
      AgentsApi.CodeConflict -> StatusCode.Conflict,
      ConfigEdit.CodeChanged -> StatusCode.Conflict,
      ConfigEdit.CodeRefused -> StatusCode.BadRequest,
      ConfigEdit.CodeUnavailable -> StatusCode.ServiceUnavailable,
      BasemapApi.Busy -> StatusCode.Conflict
    )

  /** The error output of every endpoint: the status from the problems ([[Error.status]]), the body the [[Error]]. */
  lazy val problemOut: EndpointOutput[Error] =
    statusCode
      .and(jsonBody[Error])
      .map[Error]((answered: (StatusCode, Error)) => answered._2)((e: Error) => (e.status, e))

  object Index:
    type Current = Current.T
    object Current extends Newtype[String, Pure]
    type PathTemplate = PathTemplate.T
    object PathTemplate extends Newtype[String, Pure]
    type Readme = Readme.T
    object Readme extends Newtype[String, Pure]

    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]

    val Begin = "<!-- BEGIN API INDEX (generated by `just api-docs` — do not edit by hand) -->"
    val End = "<!-- END API INDEX -->"

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def path(e: AnyEndpoint): PathTemplate = PathTemplate(e.showPathTemplate(showQueryParam = None))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def method(e: AnyEndpoint): HttpMethod.Wire = HttpMethod.Wire(e.method.map(_.method).getOrElse("GET"))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(endpoints: List[AnyEndpoint]): Markdown =
      val order = List("GET", "POST", "PUT", "PATCH", "DELETE")
      val byPath =
        endpoints.groupBy(path).view.mapValues(_.map(method).distinct.sortBy(m => order.indexOf((m).value))).toMap
      val rows = byPath.keys.toList.sorted.map(p => s"| ${byPath(p).map(m => s"`$m`").mkString(" · ")} | `$p` |")
      Markdown(
        (s"${Plural.count(Plural.Count((endpoints.size).toLong), Plural.Noun.Singular("endpoint"))} on ${Plural.count(Plural.Count((byPath.size).toLong), Plural.Noun.Singular("path"))}:" ::
          "" :: "| methods | path |" :: "|---|---|" :: rows).mkString("\n")
      )

    val Page = "docs/user-guide/reference/rest-api.md"

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def splice(readme: Readme, index: Markdown): Either[Problem, Readme] =
      val (b, e) = ((readme).value.indexOf(Begin), (readme).value.indexOf(End))
      if b < 0 || e < b then Left(Problem.Invalid.Missing(Problem.Field(s"$Begin"), Vector(Problem.Field(s"$Page"))))
      else
        Right(
          Readme(
            (readme).value.substring(0, b + Begin.length) + "\n" + (index).value + "\n" + (readme).value.substring(e)
          )
        )

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def current(readme: Readme): Option[Current] =
      val (b, e) = ((readme).value.indexOf(Begin), (readme).value.indexOf(End))
      Option.when(b >= 0 && e > b)(Current((readme).value.substring(b + Begin.length, e).trim))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @main def generateApiIndex(repo: String): Unit =
    val p = java.nio.file.Path.of(repo).resolve(Index.Page)
    Index.splice(Index.Readme(java.nio.file.Files.readString(p)), Index.markdown(all)) match
      case Left(e)  => throw IllegalStateException((e).sentence)
      case Right(s) => val _ = java.nio.file.Files.writeString(p, (s).value)
