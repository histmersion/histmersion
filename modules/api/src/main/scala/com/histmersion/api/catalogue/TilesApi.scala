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
    def withUndated: Boolean = (undated.contains(true))

  object TileQuery:

    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tier = Tier.T
    object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
      bytes: Array[TileAnswer.Bytes],
      contentType: TileAnswer.ContentType,
      etag: TileAnswer.Etag,
      cacheControl: TileAnswer.CacheControl,
      status: StatusCode
  )

  object TileAnswer:
    object ContentType:
      type Wire = Wire.T
      object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
      .mapInTo[TileQuery]
      .errorOut(statusCode(StatusCode.BadRequest))
      .errorOut(jsonBody[Api.Error])
      .out(
        byteArrayBody
          .and(header[String](HeaderNames.ContentType))
          .and(header[String](HeaderNames.Etag))
          .and(header[String](HeaderNames.CacheControl))
          .and(statusCode)
          .mapTo[TileAnswer]
      )

  lazy val all: List[AnyEndpoint] = List(tile)
