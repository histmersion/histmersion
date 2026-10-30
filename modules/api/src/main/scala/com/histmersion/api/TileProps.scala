package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*

/** The property names of a tile's features — one list the server writes and the page reads. A feature is ONE item (its
  * [[Key]] the item's key, and the item's own fields) or a CELL of several (its [[Key]] the cell's, [[Count]] how many,
  * [[MidLo]]/[[MidHi]] the least and greatest middle of their dated spans, [[West]]…[[North]] their bounding box).
  */
object TileProps:
  /** The zoom of the tile a feature came in (every feature carries it): the page reads the tiles of its view's zoom
    * alone while the map still holds a coarser tile it drew in the meantime.
    */
  val Zoom = "z"
  val Key = "k"
  val Count = "n"
  val MidLo = "lo"
  val MidHi = "hi"
  val West = "w"
  val South = "s"
  val East = "e"
  val North = "nn"

  /** How many of a cell's items have no known date; on an item, 1 when it has none. Only in the tiles of a layer asked
    * for with `undated=true`, and only when not zero.
    */
  val Undated = "u"
  // the fields of one item: the names of its wire record's fields ([[SiteTile]], [[EventGeo]], [[MediaGeoHit]])
  val Id = "id"
  val Label = "label"
  val Kind = "kind"
  val SiteType = "siteType"
  val Basis = "basis"
  val PeriodStart = "periodStart"
  val PeriodEnd = "periodEnd"
  val Scale = "scale"
  val Historicity = "historicity"
  val NotBefore = "notBefore"
  val BeginBy = "beginBy"
  val EndFrom = "endFrom"
  val NotAfter = "notAfter"
  val Anchor = "anchor"
  val PlaceId = "placeId"
  val PlaceLabel = "placeLabel"
  val ParentId = "parentId"
  val Tier = "tier"
  val Size = "size"
  val Precision = "precision"
  val Dating = "dating"
  val Citekey = "citekey"
  val AssetName = "assetName"
  val Caption = "caption"
  val Uid = "uid"
  val Provider = "provider"
  val License = "license"
  val Credit = "credit"
  val ThumbUrl = "thumbUrl"
  val SourceUrl = "sourceUrl"
  val LicenseUrl = "licenseUrl"
  val Author = "author"
