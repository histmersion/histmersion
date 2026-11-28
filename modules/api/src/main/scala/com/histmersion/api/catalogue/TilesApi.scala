package com.histmersion
package api.catalogue
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.{HeaderNames, StatusCode}
import com.histmersion.api.Api
import com.histmersion.api.library.Filter
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@atBoundary(
  BoundaryKind.Http,
  "declares the Tapir endpoints of the tiles routes with their request and response bodies"
)
object TilesApi:

  final case class TileQuery(
      layer: TileQuery.Layer,
      z: TileQuery.Z,
      x: TileQuery.X,
      y: TileQuery.Y,
      filter: Filter = Filter(),
      kind: Option[TileQuery.Kind] = None,
      tier: Option[TileQuery.Tier] = None,
      historicity: Option[TileQuery.Historicity] = None,
      context: Option[TileQuery.Context] = None,
      text: Option[TileQuery.Text] = None,
      placeId: Option[Place.Id] = None,
      siteType: Option[TileQuery.SiteType] = None,
      provider: Option[TileQuery.Provider] = None,
      license: Option[TileQuery.License] = None,
      undated: Option[TileQuery.Undated] = None,
      ifNoneMatch: Option[TileQuery.IfNoneMatch] = None
  ):
    def withUndated: Boolean = (undated.contains(TileQuery.Undated.Undated))

  object TileQuery:

    export TileLayer.Wire as Layer
    export com.histmersion.api.basemap.Basemap.Zoom as Z
    type X = X.T
    object X extends Newtype[Long, Pure]
    type Y = Y.T
    object Y extends Newtype[Long, Pure]
    export Api.EventQuery.Kind
    export Api.EventQuery.Tier
    export com.histmersion.api.catalogue.Historicity.Wire as Historicity
    enum Context derives CanEqual:
      case Context, Alone
    object Context extends TwoStates[Context]:
      extension (context: Context) def context: Boolean = context == Context
    export Api.Search.Text
    type SiteType = SiteType.T
    object SiteType extends Newtype[String, Pure]
    type Provider = Provider.T
    object Provider extends Newtype[String, Pure]
    type License = License.T
    object License extends Newtype[String, Pure]
    enum Undated derives CanEqual:
      case Undated, Dated
    object Undated extends TwoStates[Undated]:
      extension (undated: Undated) def undated: Boolean = undated == Undated
    type IfNoneMatch = IfNoneMatch.T
    object IfNoneMatch extends Newtype[String, Pure]

  final case class TileAnswer(
      bytes: Api.FileBytes,
      contentType: TileAnswer.ContentType,
      etag: TileAnswer.Etag,
      cacheControl: TileAnswer.CacheControl,
      status: StatusCode
  )

  object TileAnswer:
    export Api.ContentType
    type Etag = Etag.T
    object Etag extends Newtype[String, Pure]
    type CacheControl = CacheControl.T
    object CacheControl extends Newtype[String, Pure]

  val ContentType = "application/vnd.mapbox-vector-tile"

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(
    LintRule.GetWrites,
    "pending S1 follow-up 5 (a GET or HEAD route writes nothing): GET: Database Frag.update"
  )
  lazy val tile: PublicEndpoint[TileQuery, Api.Error, TileAnswer, Any] =
    endpoint.get
      .in("api" / "tiles" / path[String]("layer") / path[Int]("z") / path[Long]("x") / path[Long]("y"))
      .in(Api.filterQuery)
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("siteType"))
      .in(query[Option[String]]("provider"))
      .in(query[Option[String]]("license"))
      .in(query[Option[Boolean]]("undated"))
      .in(header[Option[String]](HeaderNames.IfNoneMatch))
      .mapIn {
        case (
              layer,
              z,
              x,
              y,
              filter,
              kind,
              tier,
              historicity,
              context,
              text,
              placeId,
              siteType,
              provider,
              license,
              undated,
              ifNoneMatch
            ) =>
          TileQuery(
            TileQuery.Layer(layer),
            TileQuery.Z(z),
            TileQuery.X(x),
            TileQuery.Y(y),
            filter,
            kind.map(TileQuery.Kind(_)),
            tier.map(TileQuery.Tier(_)),
            historicity.map(TileQuery.Historicity(_)),
            context.map((if _ then TileQuery.Context.Context else TileQuery.Context.Alone)),
            text.map(TileQuery.Text(_)),
            placeId.map(Place.Id(_)),
            siteType.map(TileQuery.SiteType(_)),
            provider.map(TileQuery.Provider(_)),
            license.map(TileQuery.License(_)),
            undated.map((if _ then TileQuery.Undated.Undated else TileQuery.Undated.Dated)),
            ifNoneMatch.map(TileQuery.IfNoneMatch(_))
          )
      }(v =>
        (
          v.layer.value,
          v.z.value,
          v.x.value,
          v.y.value,
          v.filter,
          v.kind.map(_.value),
          v.tier.map(_.value),
          v.historicity.map(_.value),
          v.context.map(_.context),
          v.text.map(_.value),
          v.placeId.map(_.value),
          v.siteType.map(_.value),
          v.provider.map(_.value),
          v.license.map(_.value),
          v.undated.map(_.undated),
          v.ifNoneMatch.map(_.value)
        )
      )
      .errorOut(Api.problemOut)
      .out(
        byteArrayBody
          .and(header[String](HeaderNames.ContentType))
          .and(header[String](HeaderNames.Etag))
          .and(header[String](HeaderNames.CacheControl))
          .and(statusCode)
          .map { case (bytes, contentType, etag, cacheControl, status) =>
            TileAnswer(
              Api.FileBytes(bytes),
              TileAnswer.ContentType(contentType),
              TileAnswer.Etag(etag),
              TileAnswer.CacheControl(cacheControl),
              status
            )
          }(v => (v.bytes.value, v.contentType.value, v.etag.value, v.cacheControl.value, v.status))
      )

  lazy val all: List[AnyEndpoint] = List(tile)
