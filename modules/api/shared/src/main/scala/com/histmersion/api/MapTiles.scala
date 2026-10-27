package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.{HeaderNames, StatusCode}

/** The map's point layers as Mapbox vector tiles (`GET /api/tiles/{layer}/{z}/{x}/{y}`): every site,
  * event and picture of the time span, cut by the tile grid MapLibre asks for (Web Mercator, 512 px a
  * tile), never capped. A tile holding more items than the server's `ui.map-tiles.max-points` is
  * GROUPED: its items are counted by square cells of the grid, and a cell of several items is one
  * feature with their count, the range of their dates and their bounding box — so the counts of a
  * view always add up to every item in it. Below that, and at the deepest tile the server cuts, every
  * item is a feature of its own. */
enum TileLayer:
  case Sites, Events, Media

  def wire: String = this match
    case Sites  => "sites"
    case Events => "events"
    case Media  => "media"

object TileLayer:
  def parse(s: String): Option[TileLayer] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

/** The property names of a tile's features — one list the server writes and the page reads. A feature
  * is ONE item (its [[Key]] the item's key, and the item's own fields) or a CELL of several (its
  * [[Key]] the cell's, [[Count]] how many, [[MidLo]]/[[MidHi]] the least and greatest middle of their
  * dated spans, [[West]]…[[North]] their bounding box). */
object TileProps:
  /** The zoom of the tile a feature came in (every feature carries it): the page reads the tiles of its
    * view's zoom alone while the map still holds a coarser tile it drew in the meantime. */
  val Zoom  = "z"
  val Key   = "k"
  val Count = "n"
  val MidLo = "lo"
  val MidHi = "hi"
  val West  = "w"
  val South = "s"
  val East  = "e"
  val North = "nn"
  // the fields of one item: the names of its wire record's fields ([[SiteTile]], [[EventGeo]], [[MediaGeoHit]])
  val Id          = "id"
  val Label       = "label"
  val Kind        = "kind"
  val SiteType    = "siteType"
  val Basis       = "basis"
  val PeriodStart = "periodStart"
  val PeriodEnd   = "periodEnd"
  val Scale       = "scale"
  val Historicity = "historicity"
  val NotBefore   = "notBefore"
  val BeginBy     = "beginBy"
  val EndFrom     = "endFrom"
  val NotAfter    = "notAfter"
  val Anchor      = "anchor"
  val PlaceId     = "placeId"
  val PlaceLabel  = "placeLabel"
  val ParentId    = "parentId"
  val Tier        = "tier"
  val Size        = "size"
  val Precision   = "precision"
  val Dating      = "dating"
  val Citekey     = "citekey"
  val AssetName   = "assetName"
  val Caption     = "caption"
  val Uid         = "uid"
  val Provider    = "provider"
  val License     = "license"
  val Credit      = "credit"
  val ThumbUrl    = "thumbUrl"

/** One site of the sites tiles: what the places feed's site features carried (`/api/places/geo`). */
final case class SiteTile(id: String, label: String, kind: String, siteType: Option[String], basis: String,
    periodStart: Option[Long], periodEnd: Option[Long])

/** The tile endpoint's definition (appended to [[Api.all]]). REST only, like `/api/places/geo` and
  * `/api/events/geo`: a feed for the web map, of no use to an agent or a terminal — `events`, `places`
  * and `media` (MCP and CLI) answer the same selections as lists. */
object TilesApi:

  /** One tile's request: the layer and the tile, the shared [[Filter]] (its `from`/`to` the span — a
    * RANGE: every item whose dates OVERLAP it), and what each layer is narrowed by: the events' `kind`,
    * `tier`, `historicity`, `context`, `text` and `placeId` ([[Api.EventQuery]]); the sites' `siteType`;
    * the pictures' `kind`, `provider` and `license` ([[Api.MediaGeoQuery]]). `ifNoneMatch` is the
    * browser's revalidation. */
  final case class TileQuery(layer: String, z: Int, x: Long, y: Long, filter: Filter = Filter(),
      kind: Option[String] = None, tier: Option[String] = None, historicity: Option[String] = None,
      context: Option[Boolean] = None, text: Option[String] = None, placeId: Option[String] = None,
      siteType: Option[String] = None, provider: Option[String] = None, license: Option[String] = None,
      ifNoneMatch: Option[String] = None)

  /** A tile's answer: its bytes (none when the browser's copy is current), its type, its ETag (the
    * data's version: a write to what the layer reads changes it), its caching rule and status (200,
    * 304). */
  final case class TileAnswer(bytes: Array[Byte], contentType: String, etag: String, cacheControl: String,
      status: StatusCode)

  /** The type of a Mapbox vector tile. */
  val ContentType = "application/vnd.mapbox-vector-tile"

  /** `GET /api/tiles/{layer}/{z}/{x}/{y}?<filter>&kind=&tier=&historicity=&context=&text=&placeId=&siteType=&provider=&license=`
    * — one vector tile of `sites`, `events` or `media` (one layer of that name) for the span `from`..`to`.
    * 400 for an unknown layer or a tile outside the grid. */
  lazy val tile: PublicEndpoint[TileQuery, ApiError, TileAnswer, Any] =
    endpoint.get.in("api" / "tiles" / path[String]("layer") / path[Int]("z") / path[Long]("x") / path[Long]("y"))
      .in(Api.filterQuery)
      .in(query[Option[String]]("kind")).in(query[Option[String]]("tier")).in(query[Option[String]]("historicity"))
      .in(query[Option[Boolean]]("context")).in(query[Option[String]]("text")).in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("siteType")).in(query[Option[String]]("provider")).in(query[Option[String]]("license"))
      .in(header[Option[String]](HeaderNames.IfNoneMatch))
      .mapInTo[TileQuery]
      .errorOut(statusCode(StatusCode.BadRequest)).errorOut(jsonBody[ApiError])
      .out(byteArrayBody.and(header[String](HeaderNames.ContentType)).and(header[String](HeaderNames.Etag))
        .and(header[String](HeaderNames.CacheControl)).and(statusCode).mapTo[TileAnswer])

  lazy val all: List[AnyEndpoint] = List(tile)
