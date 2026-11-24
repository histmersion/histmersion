package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.TimeSpan
import com.histmersion.web.map.{GeoBounds, LatLon, TilePointFeature}

import scala.scalajs.js
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Filter
import com.histmersion.api.media.MediaGeoHit
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.media.MediaItem
import com.histmersion.api.Years
import com.histmersion.api.Api
import com.histmersion.lint.{LintRule, tolerated}

/** The tiled layers' pure half: a tile feature read back as the app's records (an item, a cell), the playback buckets,
  * the tile URL template, and what a cell counts for in a bubble.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class TiledLayersSuite extends munit.FunSuite:

  private def feature(lat: LatLon.Lat, lon: LatLon.Lon, props: (String, js.Any)*): TilePointFeature =
    TilePointFeature(LatLon(lat, lon), js.Dictionary(props*))

  test("a site feature is the site the places feed gave: its type, basis and dates"):
    val f = feature(
      LatLon.Lat(37.6),
      LatLon.Lon(22.7),
      "k" -> "pleiades_1",
      "z" -> 9,
      "id" -> "pleiades_1",
      "label" -> "Mycenae",
      "kind" -> "site",
      "siteType" -> "fortification",
      "basis" -> "dated",
      "periodStart" -> -1600,
      "periodEnd" -> -1100
    )
    assertEquals(
      TileDecode.site(f),
      Some(
        SitePoint(
          Some(SitePoint.Id("pleiades_1")),
          SitePoint.Label("Mycenae"),
          SitePoint.Kind("site"),
          Some(SitePoint.SiteType("fortification")),
          Some(SitePoint.Basis("dated")),
          LatLon.Lat(37.6),
          LatLon.Lon(22.7),
          Some((PlaybackAreas.FractionalYear(-1600.0), PlaybackAreas.FractionalYear(-1100.0)))
        )
      )
    )
    assert(!TileDecode.isCell(f))
    // an open end: no period on the ramp, as the feed's
    assertEquals(
      TileDecode
        .site(feature(LatLon.Lat(1), LatLon.Lon(2), "id" -> "x", "label" -> "X", "periodStart" -> -10))
        .flatMap(_.period),
      None
    )

  test("an event feature carries everything its marker, label and card read"):
    val f = feature(
      LatLon.Lat(40.0),
      LatLon.Lon(26.2),
      "id" -> "ev1",
      "label" -> "Fall of Troy",
      "kind" -> "destruction",
      "scale" -> "local",
      "historicity" -> "legendary",
      "notBefore" -> -1190,
      "notAfter" -> -1180,
      "anchor" -> "place",
      "placeId" -> "p1",
      "placeLabel" -> "Troy",
      "tier" -> "catalogue",
      "precision" -> "approximate",
      "dating" -> "c. 1185 BCE"
    )
    val e = TileDecode.event(f).get
    assertEquals(
      (e.id, e.label, e.kind, e.tier, e.precision),
      (Event.Id("ev1"), "Fall of Troy", "destruction", "catalogue", "approximate")
    )
    assertEquals(e.timeSpan, TimeSpan(Some(Year(-1190L)), None, None, Some(Year(-1180L))))
    assertEquals(
      (e.lat, e.lon, e.placeLabel, e.dating, e.outline),
      (Latitude(40.0), Longitude(26.2), Some("Troy"), Some("c. 1185 BCE"), None)
    )

  test("a picture feature is the media layer's hit"):
    val f = feature(
      LatLon.Lat(41.9),
      LatLon.Lon(12.5),
      "uid" -> "u1",
      "assetName" -> "Forum",
      "kind" -> "photo",
      "periodStart" -> -50,
      "periodEnd" -> 300,
      "provider" -> "met",
      "license" -> "CC0-1.0",
      "credit" -> "The Met",
      "thumbUrl" -> "/api/media/u1/thumb",
      "sourceUrl" -> "https://www.metmuseum.org/art/collection/search/1",
      "licenseUrl" -> "https://creativecommons.org/publicdomain/zero/1.0/",
      "author" -> "Unknown maker"
    )
    // its full size is this server's file of the uid (not carried per picture); the provider's page and the licence's
    assertEquals(
      TileDecode.media(f),
      Some(
        MediaGeoHit(
          None,
          Bundle.Asset.FileName("Forum"),
          Some(MediaItem.Kind("photo")),
          Latitude(41.9),
          Longitude(12.5),
          Some(Year(-50L)),
          Some(Year(300L)),
          None,
          MediaItem.Uid("u1"),
          Some(MediaItem.Provider("met")),
          Some(MediaItem.License("CC0-1.0")),
          Some(MediaItem.Credit("The Met")),
          Some(MediaItem.ThumbUrl("/api/media/u1/thumb")),
          fileUrl = Some(MediaItem.FileUrl("/media/u1/file")),
          sourceUrl = Some(MediaItem.SourceUrl("https://www.metmuseum.org/art/collection/search/1")),
          licenseUrl = Some(MediaItem.LicenseUrl("https://creativecommons.org/publicdomain/zero/1.0/")),
          author = Some(MediaItem.Author("Unknown maker"))
        )
      )
    )

  test("a cell: its count, dates' range and box; it counts for all of them in a bubble and is not listed"):
    val f = feature(
      LatLon.Lat(10),
      LatLon.Lon(20),
      "k" -> "c4/1/2",
      "n" -> 37,
      "lo" -> -1500.5,
      "hi" -> -200,
      "w" -> 19,
      "s" -> 9,
      "e" -> 21,
      "nn" -> 11
    )
    assert(TileDecode.isCell(f))
    val c = TileDecode.cell(f, DotKind.Site)
    assertEquals(
      c,
      TileCellPoint(
        TileCellPoint.Key("c4/1/2"),
        DotKind.Site,
        TileCellPoint.Lat(10),
        TileCellPoint.Lon(20),
        TileCellPoint.N(37),
        Some(TileCellPoint.MidLo(-1500.5)),
        Some(TileCellPoint.MidHi(-200.0)),
        GeoBounds(GeoBounds.South(9), GeoBounds.West(19), GeoBounds.North(11), GeoBounds.East(21))
      )
    )
    val m = CellKind(DotKind.Site).member(c)
    assertEquals((m.kind, m.n, m.mids, m.aggregate), (DotKind.Site, 37, List(-1500.5, -200.0), true))
    val held = DotCluster.Contents(List(m, Member(DotKind.Event, Member.N(1), Nil, Certainty.NoSay)))
    assertEquals(held.total, 38)
    assert(held.counted)
    assertEquals(
      held.cells,
      List(GeoBounds(GeoBounds.South(9), GeoBounds.West(19), GeoBounds.North(11), GeoBounds.East(21)))
    )
    assert(!DotCluster.Contents(List(Member(DotKind.Event, Member.N(1), Nil, Certainty.NoSay))).counted)

  test("playback buckets: a round number of years a bucket, the window widened to whole ones"):
    assertEquals(PlaybackBuckets.years(PlaybackBuckets.YearsPerSecond(166), PlaybackBuckets.Seconds(1.0)), 200L)
    assertEquals(PlaybackBuckets.years(PlaybackBuckets.YearsPerSecond(30), PlaybackBuckets.Seconds(1.0)), 50L)
    assertEquals(PlaybackBuckets.years(PlaybackBuckets.YearsPerSecond(0), PlaybackBuckets.Seconds(1.0)), 1L)
    assertEquals(PlaybackBuckets.years(PlaybackBuckets.YearsPerSecond(1000), PlaybackBuckets.Seconds(1.0)), 1000L)
    assertEquals(
      PlaybackBuckets.widen((Years.Astronomical(-1650L), Years.Astronomical(-1550L)), PlaybackBuckets.BucketYears(200)),
      (-1800L, -1401L)
    )
    assertEquals(
      PlaybackBuckets.widen((Years.Astronomical(10L), Years.Astronomical(10L)), PlaybackBuckets.BucketYears(100)),
      (0L, 99L)
    )
    assertEquals(
      PlaybackBuckets.widen((Years.Astronomical(-1L), Years.Astronomical(0L)), PlaybackBuckets.BucketYears(100)),
      (-100L, 99L)
    )

  test("a tile URL template: the placeholder tile becomes {z}/{x}/{y}"):
    val uri = s"/api/tiles/sites/${TilePlaceholder.Z}/${TilePlaceholder.X}/${TilePlaceholder.Y}?from=-10&to=20"
    assertEquals(TilePlaceholder.fill(uri), "/api/tiles/sites/{z}/{x}/{y}?from=-10&to=20")

  test("the events tiles' query: the filter's span set, its paging and sort left as every tile shares them"):
    val f = Filter(tags = List(Filter.Tags("bronze")), limit = Filter.Limit(500), offset = Filter.Offset(40))
    val q = EventMarkers.query(f, (Year(-1200L), Year(-1100L)), Some(Api.EventQuery.Kind("battle")), None)
    assertEquals(q.layer, "events")
    assertEquals(q.filter, Filter(tags = List(Filter.Tags("bronze")), period = Some(Period(Year(-1200), Year(-1100)))))
    assertEquals((q.kind, q.tier, q.context), (Some("battle"), None, Some(true)))

  test("a read at the same URL and zoom keeps what was shown near the view and is in no tile held now"):
    val fresh = List("a" -> 1, "b" -> 2)
    val was = List("b" -> 0, "c" -> 3, "far" -> 4)
    // "b" is read again (the new copy wins), "c" near the view stays, "far" goes
    assertEquals(TiledPoints.merge(fresh, was, _._1, _._1 != "far"), List("a" -> 1, "b" -> 2, "c" -> 3))
    assertEquals(TiledPoints.merge(Nil, was, _._1, _ => true), was)
    assertEquals(TiledPoints.merge(fresh, Nil, _._1, _ => true), fresh)
