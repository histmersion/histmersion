package com.histmersion.web

import com.histmersion.web.MapRegions.*
import com.histmersion.api.basemap.Basemap

/** The About window's map regions, as data: what may be picked, the fetch a choice asks for, the sizes. */
class MapRegionsSuite extends munit.FunSuite:

  private val status = Basemap.Status(
    available = true,
    dir = "/d",
    bbox = "-12,20,90,62",
    layers = Nil,
    fetchCommand = "",
    regions = List(Basemap.Region("argolid", "22.5,37.4,23.2,37.9"))
  )

  test("the regions: the configured one, the named ones, the world, a box of one's own"):
    assertEquals(
      choices(status).map(c => (c.id, c.bbox)),
      List(
        ("configured", Some("-12,20,90,62")),
        ("argolid", Some("22.5,37.4,23.2,37.9")),
        ("world", Some("world")),
        (OwnBox, None)
      )
    )

  test("the default configured region is the whole world, listed once"):
    val cs = choices(status.copy(bbox = "world"))
    assertEquals(
      cs.map(c => (c.id, c.bbox)),
      List(("configured", Some("world")), ("argolid", Some("22.5,37.4,23.2,37.9")), (OwnBox, None))
    )
    assertEquals(cs.head.label, "the configured region (the whole world)")

  test("the fetch: the map for a box replaces the region; the relief of a named region or of a box of one's own"):
    assertEquals(
      request(" 20,30,40,50 ", Content.Map, None, "", dryRun = true),
      Right(Basemap.FetchQuery(bbox = Some("20,30,40,50"), dryRun = true))
    )
    assertEquals(
      request("world", Content.Map, None, "6", dryRun = false),
      Right(Basemap.FetchQuery(bbox = Some("world"), maxZoom = Some(6)))
    )
    assertEquals(
      request("22.5,37.4,23.2,37.9", Content.Relief, Some("argolid"), "", dryRun = true),
      Right(
        Basemap.FetchQuery(
          layer = "terrain",
          bbox = Some("22.5,37.4,23.2,37.9"),
          dryRun = true,
          focus = Some("argolid")
        )
      )
    )
    assertEquals(
      request("1,2,3,4", Content.Relief, None, "11", dryRun = false).map(q => (q.focus, q.terrainMaxZoom)),
      Right((Some("bbox-1-2-3-4"), Some(11)))
    )

  test("refused before asking: a box that is not one, a zoom that is not a number in range"):
    assert(request("20,30,10,50", Content.Map, None, "", dryRun = true).left.exists(_.contains("west must be < east")))
    assert(request("nowhere", Content.Map, None, "", dryRun = true).isLeft)
    assert(request("1,2,3,4", Content.Map, None, "deep", dryRun = true).left.exists(_.contains("0 to")))
    assert(request("1,2,3,4", Content.Map, None, "99", dryRun = true).isLeft)

  test("a dry run's answer, layer by layer, each with its sizes by zoom"):
    val answer =
      "vector (20261019.pmtiles, zoom 0–2): 6 tiles, would download 1 MB in 2 requests (+1 for the directories); " +
        "by zoom — zoom 0: 1 tile, 40 kB · zoom 1: 1 tile, 90 kB · zoom 2: 4 tiles, 900 kB; bathymetry (zoom 0–7): would download 12 tiles, about 1 MB"
    assertEquals(
      sizes(answer),
      List(
        LayerSizes(
          "vector (20261019.pmtiles, zoom 0–2): 6 tiles, would download 1 MB in 2 requests (+1 for the directories)",
          List("zoom 0: 1 tile, 40 kB", "zoom 1: 1 tile, 90 kB", "zoom 2: 4 tiles, 900 kB")
        ),
        LayerSizes("bathymetry (zoom 0–7): would download 12 tiles, about 1 MB", Nil)
      )
    )
    assertEquals(sizes(""), Nil)
