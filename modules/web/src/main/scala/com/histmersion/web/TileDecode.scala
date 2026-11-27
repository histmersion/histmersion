package com.histmersion
package web

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventScale, EventTier}
import com.histmersion.api.media.{MediaGeoHit, MediaItem, MediaThumb}
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.library.Citekey
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

/** The pure half of the tiled layers: a tile feature as the app's own point records. */
object TileDecode:
  private def str(p: js.Dictionary[js.Any], k: String): Option[String] =
    p.get(k).filter(v => v != null && !js.isUndefined(v)).map(_.toString)
  private def num(p: js.Dictionary[js.Any], k: String): Option[Double] =
    p.get(k).filter(v => js.typeOf(v) == "number").map(_.asInstanceOf[Double])
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def long(p: js.Dictionary[js.Any], k: String): Option[Year] =
    num(p, k).map(d => Year(d.toLong))

  /** Is it a cell of several items (it carries their count)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def isCell(f: TilePointFeature): Boolean = num(f.props, P.Count).exists(_ > 1)

  /** A cell of `kind`: its count, dates' range and box (none of them missing: its own point). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cell(f: TilePointFeature, kind: DotKind): TileCellPoint =
    val p = f.props
    val box = (for w <- num(p, P.West); s <- num(p, P.South); e <- num(p, P.East); n <- num(p, P.North)
    yield GeoBounds(GeoBounds.South(s), GeoBounds.West(w), GeoBounds.North(n), GeoBounds.East(e))).getOrElse(
      GeoBounds(
        GeoBounds.South(f.at.lat.value),
        GeoBounds.West(f.at.lon.value),
        GeoBounds.North(f.at.lat.value),
        GeoBounds.East(f.at.lon.value)
      )
    )
    TileCellPoint(
      TileCellPoint.Key(str(p, P.Key).getOrElse(s"${f.at.lat}|${f.at.lon}")),
      kind,
      TileCellPoint.Lat(f.at.lat.value),
      TileCellPoint.Lon(f.at.lon.value),
      TileCellPoint.N(num(p, P.Count).fold(1)(_.toInt)),
      num(p, P.MidLo).map(TileCellPoint.MidLo(_)),
      num(p, P.MidHi).map(TileCellPoint.MidHi(_)),
      box,
      TileCellPoint.Undated(num(p, P.Undated).fold(0)(_.toInt))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def site(f: TilePointFeature): Option[SitePoint] =
    val p = f.props
    str(p, P.Id).map { id =>
      val period = for a <- num(p, P.PeriodStart); b <- num(p, P.PeriodEnd)
      yield (PlaybackAreas.FractionalYear(a), PlaybackAreas.FractionalYear(b))
      SitePoint(
        Some(SitePoint.Id(id)),
        SitePoint.Label(str(p, P.Label).getOrElse("")),
        SitePoint.Kind(str(p, P.Kind).getOrElse(Place.Kind.Site)),
        str(p, P.SiteType).map(SitePoint.SiteType(_)),
        str(p, P.Basis).map(SitePoint.Basis(_)),
        f.at.lat,
        f.at.lon,
        period,
        undated = SitePoint.Undated(num(p, P.Undated).exists(_ > 0))
      )
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def event(f: TilePointFeature): Option[EventGeo] =
    val p = f.props
    for id <- str(p, P.Id)
    yield EventGeo(
      Event.Id(id),
      EventGeo.Label(str(p, P.Label).getOrElse(id)),
      EventGeo.Kind(str(p, P.Kind).getOrElse("")),
      EventGeo.Scale(str(p, P.Scale).getOrElse(EventScale.Local)),
      EventGeo.Historicity(str(p, P.Historicity).getOrElse("")),
      TimeSpan(long(p, P.NotBefore), long(p, P.BeginBy), long(p, P.EndFrom), long(p, P.NotAfter)),
      Latitude(f.at.lat.value),
      Longitude(f.at.lon.value),
      EventGeo.Anchor(str(p, P.Anchor).getOrElse("event")),
      (str(p, P.PlaceId)).map(Place.Id.apply),
      str(p, P.PlaceLabel).map(EventGeo.PlaceLabel(_)),
      str(p, P.ParentId).map(EventGeo.ParentId(_)),
      None,
      EventGeo.Tier(str(p, P.Tier).getOrElse(EventTier.Attested)),
      str(p, P.Size).map(EventGeo.Size(_)),
      EventGeo.Precision(str(p, P.Precision).getOrElse(EventPrecision.Precise)),
      str(p, P.Dating).map(EventGeo.Dating(_))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def media(f: TilePointFeature): Option[MediaGeoHit] =
    val p = f.props
    for uid <- str(p, P.Uid)
    yield MediaGeoHit(
      str(p, P.Citekey).map(Citekey(_)),
      Bundle.Asset.FileName(str(p, P.AssetName).getOrElse(uid)),
      str(p, P.Kind).map(MediaItem.Kind(_)),
      Latitude(f.at.lat.value),
      Longitude(f.at.lon.value),
      long(p, P.PeriodStart),
      long(p, P.PeriodEnd),
      str(p, P.Caption).map(MediaItem.Caption(_)),
      MediaItem.Uid(uid),
      str(p, P.Provider).map(MediaItem.Provider(_)),
      str(p, P.License).map(MediaItem.License(_)),
      str(p, P.Credit).map(MediaItem.Credit(_)),
      str(p, P.ThumbUrl).map(MediaItem.ThumbUrl(_)),
      fileUrl = Some(
        MediaItem.FileUrl(MediaThumb.filePath(Bundle.Asset.Uid(uid)).value)
      ),
      sourceUrl = str(p, P.SourceUrl).map(MediaItem.SourceUrl(_)),
      licenseUrl = str(p, P.LicenseUrl).map(MediaItem.LicenseUrl(_)),
      author = str(p, P.Author).map(MediaItem.Author(_))
    )
