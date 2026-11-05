package com.histmersion.web

import com.histmersion.api.TimeSpan
import com.histmersion.web.map.{GeoBounds, LatLon, TilePointFeature}

import scala.scalajs.js
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Filter
import com.histmersion.api.media.MediaGeoHit

/** The tiled layers' pure half: a tile feature read back as the app's records (an item, a cell), the playback buckets,
  * the tile URL template, and what a cell counts for in a bubble.
  */
class TiledLayersSuite extends munit.FunSuite:

  private def feature(lat: Double, lon: Double, props: (String, js.Any)*): TilePointFeature =
    TilePointFeature(LatLon(lat, lon), js.Dictionary(props*))

  test("a site feature is the site the places feed gave: its type, basis and dates"):
    val f = feature(
      37.6,
      22.7,
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
          Some("pleiades_1"),
          "Mycenae",
          "site",
          Some("fortification"),
          Some("dated"),
          37.6,
          22.7,
          Some((-1600.0, -1100.0))
        )
      )
    )
    assert(!TileDecode.isCell(f))
    // an open end: no period on the ramp, as the feed's
    assertEquals(
      TileDecode.site(feature(1, 2, "id" -> "x", "label" -> "X", "periodStart" -> -10)).flatMap(_.period),
      None
    )

  test("an event feature carries everything its marker, label and card read"):
    val f = feature(
      40.0,
      26.2,
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
      (com.histmersion.api.catalogue.Event.Id("ev1"), "Fall of Troy", "destruction", "catalogue", "approximate")
    )
    assertEquals(e.timeSpan, TimeSpan(Some(-1190L), None, None, Some(-1180L)))
    assertEquals(
      (e.lat, e.lon, e.placeLabel, e.dating, e.outline),
      (com.histmersion.api.catalogue.Latitude(40.0), com.histmersion.api.catalogue.Longitude(26.2), Some("Troy"), Some("c. 1185 BCE"), None)
    )

  test("a picture feature is the media layer's hit"):
    val f = feature(
      41.9,
      12.5,
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
          "Forum",
          Some("photo"),
          41.9,
          12.5,
          Some(-50L),
          Some(300L),
          None,
          "u1",
          Some("met"),
          Some("CC0-1.0"),
          Some("The Met"),
          Some("/api/media/u1/thumb"),
          fileUrl = Some("/media/u1/file"),
          sourceUrl = Some("https://www.metmuseum.org/art/collection/search/1"),
          licenseUrl = Some("https://creativecommons.org/publicdomain/zero/1.0/"),
          author = Some("Unknown maker")
        )
      )
    )

  test("a cell: its count, dates' range and box; it counts for all of them in a bubble and is not listed"):
    val f = feature(
      10,
      20,
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
      TileCellPoint("c4/1/2", DotKind.Site, 10, 20, 37, Some(-1500.5), Some(-200.0), GeoBounds(9, 19, 11, 21))
    )
    val m = CellKind(DotKind.Site).member(c)
    assertEquals((m.kind, m.n, m.mids, m.aggregate), (DotKind.Site, 37, List(-1500.5, -200.0), true))
    val held = DotCluster.Contents(List(m, Member(DotKind.Event, 1, Nil, Certainty.NoSay)))
    assertEquals(held.total, 38)
    assert(held.counted)
    assertEquals(held.cells, List(GeoBounds(9, 19, 11, 21)))
    assert(!DotCluster.Contents(List(Member(DotKind.Event, 1, Nil, Certainty.NoSay))).counted)

  test("playback buckets: a round number of years a bucket, the window widened to whole ones"):
    assertEquals(PlaybackBuckets.years(166, 1.0), 200L)
    assertEquals(PlaybackBuckets.years(30, 1.0), 50L)
    assertEquals(PlaybackBuckets.years(0, 1.0), 1L)
    assertEquals(PlaybackBuckets.years(1000, 1.0), 1000L)
    assertEquals(PlaybackBuckets.widen((-1650L, -1550L), 200), (-1800L, -1401L))
    assertEquals(PlaybackBuckets.widen((10L, 10L), 100), (0L, 99L))
    assertEquals(PlaybackBuckets.widen((-1L, 0L), 100), (-100L, 99L))

  test("a tile URL template: the placeholder tile becomes {z}/{x}/{y}"):
    val uri = s"/api/tiles/sites/${TilePlaceholder.Z}/${TilePlaceholder.X}/${TilePlaceholder.Y}?from=-10&to=20"
    assertEquals(TilePlaceholder.fill(uri), "/api/tiles/sites/{z}/{x}/{y}?from=-10&to=20")

  test("the events tiles' query: the filter's span set, its paging and sort left as every tile shares them"):
    val f = Filter(tags = List("bronze"), limit = 500, offset = 40)
    val q = EventMarkers.query(f, (-1200L, -1100L), Some("battle"), None)
    assertEquals(q.layer, "events")
    assertEquals(q.filter, Filter(tags = List("bronze"), period = Some(Period(-1200, -1100))))
    assertEquals((q.kind, q.tier, q.context), (Some("battle"), None, Some(true)))

  test("a read at the same URL and zoom keeps what was shown near the view and is in no tile held now"):
    val fresh = List("a" -> 1, "b" -> 2)
    val was = List("b" -> 0, "c" -> 3, "far" -> 4)
    // "b" is read again (the new copy wins), "c" near the view stays, "far" goes
    assertEquals(TiledPoints.merge(fresh, was, _._1, _._1 != "far"), List("a" -> 1, "b" -> 2, "c" -> 3))
    assertEquals(TiledPoints.merge(Nil, was, _._1, _ => true), was)
    assertEquals(TiledPoints.merge(fresh, Nil, _._1, _ => true), fresh)
