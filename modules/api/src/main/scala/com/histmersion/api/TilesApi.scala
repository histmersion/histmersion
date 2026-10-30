package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{HeaderNames, StatusCode}

/** The tile endpoint's definition (appended to [[Api.all]]). REST only, like `/api/places/geo` and `/api/events/geo`: a
  * feed for the web map, of no use to an agent or a terminal — `events`, `places` and `media` (MCP and CLI) answer the
  * same selections as lists.
  */
object TilesApi:

  /** One tile's request: the layer and the tile, the shared [[Filter]] (its `from`/`to` the span — a RANGE: every item
    * whose dates OVERLAP it), and what each layer is narrowed by: the events' `kind`, `tier`, `historicity`, `context`,
    * `text` and `placeId` ([[Api.EventQuery]]); the sites' `siteType`; the pictures' `kind`, `provider` and `license`
    * ([[Api.MediaGeoQuery]]). `undated` (sites and events): also every item with no known date, whatever the span —
    * each marked ([[TileProps.Undated]]). `ifNoneMatch` is the browser's revalidation.
    */
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
    /** Are the items with no known date asked for? */
    def withUndated: Boolean = undated.contains(true)

  /** A tile's answer: its bytes (none when the browser's copy is current), its type, its ETag (the data's version: a
    * write to what the layer reads changes it), its caching rule and status (200, 304).
    */
  final case class TileAnswer(
      bytes: Array[Byte],
      contentType: String,
      etag: String,
      cacheControl: String,
      status: StatusCode
  )

  /** The type of a Mapbox vector tile. */
  val ContentType = "application/vnd.mapbox-vector-tile"

  /** `GET /api/tiles/{layer}/{z}/{x}/{y}?<filter>&kind=&tier=&historicity=&context=&text=&placeId=&siteType=&provider=&license=&undated=`
    * — one vector tile of `sites`, `events` or `media` (one layer of that name) for the span `from`..`to`. 400 for an
    * unknown layer or a tile outside the grid.
    */
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
