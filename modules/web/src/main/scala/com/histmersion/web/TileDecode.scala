package com.histmersion.web

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventScale, EventTier}
import com.histmersion.api.media.{MediaGeoHit, MediaThumb}

/** The pure half of the tiled layers: a tile feature as the app's own point records. */
object TileDecode:
  type LongK = String
  val LongK: String.type = String

  private def str(p: js.Dictionary[js.Any], k: String): Option[String] =
    p.get(k).filter(v => v != null && !js.isUndefined(v)).map(_.toString)
  private def num(p: js.Dictionary[js.Any], k: String): Option[Double] =
    p.get(k).filter(v => js.typeOf(v) == "number").map(_.asInstanceOf[Double])
  private def long(p: js.Dictionary[js.Any], k: com.histmersion.web.TileDecode.LongK): Option[Long] = num(p, k).map(_.toLong)

  /** Is it a cell of several items (it carries their count)? */
  def isCell(f: TilePointFeature): Boolean = num(f.props, P.Count).exists(_ > 1)

  /** A cell of `kind`: its count, dates' range and box (none of them missing: its own point). */
  def cell(f: TilePointFeature, kind: DotKind): TileCellPoint =
    val p = f.props
    val box = (for w <- num(p, P.West); s <- num(p, P.South); e <- num(p, P.East); n <- num(p, P.North)
    yield GeoBounds(s, w, n, e)).getOrElse(GeoBounds(f.at.lat, f.at.lon, f.at.lat, f.at.lon))
    TileCellPoint(
      str(p, P.Key).getOrElse(s"${f.at.lat}|${f.at.lon}"),
      kind,
      f.at.lat,
      f.at.lon,
      num(p, P.Count).fold(1)(_.toInt),
      num(p, P.MidLo),
      num(p, P.MidHi),
      box,
      num(p, P.Undated).fold(0)(_.toInt)
    )

  def site(f: TilePointFeature): Option[SitePoint] =
    val p = f.props
    str(p, P.Id).map { id =>
      val period = for a <- num(p, P.PeriodStart); b <- num(p, P.PeriodEnd) yield (a, b)
      SitePoint(
        Some(id),
        str(p, P.Label).getOrElse(""),
        str(p, P.Kind).getOrElse(com.histmersion.api.catalogue.Place.Kind.Site),
        str(p, P.SiteType),
        str(p, P.Basis),
        f.at.lat,
        f.at.lon,
        period,
        undated = num(p, P.Undated).exists(_ > 0)
      )
    }

  def event(f: TilePointFeature): Option[EventGeo] =
    val p = f.props
    for id <- str(p, P.Id)
    yield EventGeo(
      com.histmersion.api.catalogue.Event.Id(id),
      str(p, P.Label).getOrElse(id),
      str(p, P.Kind).getOrElse(""),
      str(p, P.Scale).getOrElse(EventScale.Local),
      str(p, P.Historicity).getOrElse(""),
      TimeSpan(long(p, P.NotBefore), long(p, P.BeginBy), long(p, P.EndFrom), long(p, P.NotAfter)),
      com.histmersion.api.catalogue.Latitude(f.at.lat),
      com.histmersion.api.catalogue.Longitude(f.at.lon),
      str(p, P.Anchor).getOrElse("event"),
      (str(p, P.PlaceId)).map(com.histmersion.api.catalogue.Place.Id.apply),
      str(p, P.PlaceLabel),
      str(p, P.ParentId),
      None,
      str(p, P.Tier).getOrElse(EventTier.Attested),
      str(p, P.Size),
      str(p, P.Precision).getOrElse(EventPrecision.Precise),
      str(p, P.Dating)
    )

  def media(f: TilePointFeature): Option[MediaGeoHit] =
    val p = f.props
    for uid <- str(p, P.Uid)
    yield MediaGeoHit(
      str(p, P.Citekey),
      str(p, P.AssetName).getOrElse(uid),
      str(p, P.Kind),
      f.at.lat,
      f.at.lon,
      long(p, P.PeriodStart),
      long(p, P.PeriodEnd),
      str(p, P.Caption),
      uid,
      str(p, P.Provider),
      str(p, P.License),
      str(p, P.Credit),
      str(p, P.ThumbUrl),
      fileUrl = Some(MediaThumb.filePath(uid)),
      sourceUrl = str(p, P.SourceUrl),
      licenseUrl = str(p, P.LicenseUrl),
      author = str(p, P.Author)
    )
