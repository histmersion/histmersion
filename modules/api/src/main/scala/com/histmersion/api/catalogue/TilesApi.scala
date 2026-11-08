package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{HeaderNames, StatusCode}
import com.histmersion.api.Api
import com.histmersion.api.library.Filter
import com.histmersion.api.given

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
      placeId: Option[com.histmersion.api.catalogue.Place.Id] = None,
      siteType: Option[TileQuery.SiteType] = None,
      provider: Option[TileQuery.Provider] = None,
      license: Option[TileQuery.License] = None,
      undated: Option[TileQuery.Undated] = None,
      ifNoneMatch: Option[TileQuery.IfNoneMatch] = None
  ):
    def withUndated: Boolean = (undated.contains(TileQuery.Undated(true)))

  object TileQuery:

    type Layer = com.histmersion.api.catalogue.TileLayer.Wire
    val Layer: _root_.com.histmersion.api.catalogue.TileLayer.Wire.type = _root_.com.histmersion.api.catalogue.TileLayer.Wire
    type Z = com.histmersion.api.basemap.Basemap.Zoom
    val Z: _root_.com.histmersion.api.basemap.Basemap.Zoom.type = _root_.com.histmersion.api.basemap.Basemap.Zoom
    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tier = Tier.T
    object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Historicity = com.histmersion.api.catalogue.Historicity.Wire
    val Historicity: _root_.com.histmersion.api.catalogue.Historicity.Wire.type = _root_.com.histmersion.api.catalogue.Historicity.Wire
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = com.histmersion.api.Api.Search.Text
    val Text: _root_.com.histmersion.api.Api.Search.Text.type = _root_.com.histmersion.api.Api.Search.Text
    type SiteType = SiteType.T
    object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type License = License.T
    object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Undated = Undated.T
    object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IfNoneMatch = IfNoneMatch.T
    object IfNoneMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class TileAnswer(
      bytes: Api.FileBytes,
      contentType: TileAnswer.ContentType,
      etag: TileAnswer.Etag,
      cacheControl: TileAnswer.CacheControl,
      status: StatusCode
  )

  object TileAnswer:
    type ContentType = com.histmersion.api.Api.ContentType
    val ContentType: _root_.com.histmersion.api.Api.ContentType.type = _root_.com.histmersion.api.Api.ContentType
    type Etag = Etag.T
    object Etag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CacheControl = CacheControl.T
    object CacheControl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ContentType = "application/vnd.mapbox-vector-tile"

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
      .mapIn { case (layer, z, x, y, filter, kind, tier, historicity, context, text, placeId, siteType, provider, license, undated, ifNoneMatch) => TileQuery(TileQuery.Layer(layer), TileQuery.Z(z), TileQuery.X(x), TileQuery.Y(y), filter, kind.map(TileQuery.Kind(_)), tier.map(TileQuery.Tier(_)), historicity.map(TileQuery.Historicity(_)), context.map(TileQuery.Context(_)), text.map(TileQuery.Text(_)), placeId.map(com.histmersion.api.catalogue.Place.Id(_)), siteType.map(TileQuery.SiteType(_)), provider.map(TileQuery.Provider(_)), license.map(TileQuery.License(_)), undated.map(TileQuery.Undated(_)), ifNoneMatch.map(TileQuery.IfNoneMatch(_))) }(v => (v.layer.value, v.z.value, v.x.value, v.y.value, v.filter, v.kind.map(_.value), v.tier.map(_.value), v.historicity.map(_.value), v.context.map(_.value), v.text.map(_.value), v.placeId.map(_.value), v.siteType.map(_.value), v.provider.map(_.value), v.license.map(_.value), v.undated.map(_.value), v.ifNoneMatch.map(_.value)))
      .errorOut(statusCode(StatusCode.BadRequest))
      .errorOut(jsonBody[Api.Error])
      .out(
        byteArrayBody
          .and(header[String](HeaderNames.ContentType))
          .and(header[String](HeaderNames.Etag))
          .and(header[String](HeaderNames.CacheControl))
          .and(statusCode)
          .map { case (bytes, contentType, etag, cacheControl, status) => TileAnswer(Api.FileBytes(bytes), TileAnswer.ContentType(contentType), TileAnswer.Etag(etag), TileAnswer.CacheControl(cacheControl), status) }(v => (v.bytes.value, v.contentType.value, v.etag.value, v.cacheControl.value, v.status))
      )

  lazy val all: List[AnyEndpoint] = List(tile)
