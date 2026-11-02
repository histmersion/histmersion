package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{HeaderNames, StatusCode}
import com.histmersion.api.{Api, ApiError}
import com.histmersion.api.library.Filter
import com.histmersion.api.given

object TilesApi:

  final case class TileQuery(
      layer: String,
      z: Int,
      x: Long,
      y: Long,
      filter: Filter = Filter(),
      kind: Option[String] = None,
      tier: Option[String] = None,
      historicity: Option[String] = None,
      context: Option[Boolean] = None,
      text: Option[String] = None,
      placeId: Option[String] = None,
      siteType: Option[String] = None,
      provider: Option[String] = None,
      license: Option[String] = None,
      undated: Option[Boolean] = None,
      ifNoneMatch: Option[String] = None
  ):
    def withUndated: Boolean = undated.contains(true)

  final case class TileAnswer(
      bytes: Array[Byte],
      contentType: String,
      etag: String,
      cacheControl: String,
      status: StatusCode
  )

  val ContentType = "application/vnd.mapbox-vector-tile"

  lazy val tile: PublicEndpoint[TileQuery, ApiError, TileAnswer, Any] =
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
      .errorOut(jsonBody[ApiError])
      .out(
        byteArrayBody
          .and(header[String](HeaderNames.ContentType))
          .and(header[String](HeaderNames.Etag))
          .and(header[String](HeaderNames.CacheControl))
          .and(statusCode)
          .mapTo[TileAnswer]
      )

  lazy val all: List[AnyEndpoint] = List(tile)
