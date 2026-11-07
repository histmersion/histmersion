package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.web.MapRegions.*
import com.histmersion.api.basemap.Basemap

/** The About window's map regions, as data: what may be picked, the fetch a choice asks for, the sizes. */
class MapRegionsSuite extends munit.FunSuite:

  private val status = Basemap.Status(
    available = com.histmersion.api.basemap.Basemap.Status.Available(true),
    dir = com.histmersion.api.basemap.Basemap.Status.Dir("/d"),
    bbox = com.histmersion.api.basemap.Basemap.Status.Bbox("-12,20,90,62"),
    layers = Nil,
    fetchCommand = com.histmersion.api.basemap.Basemap.Status.FetchCommand(""),
    regions = List(Basemap.Region(com.histmersion.api.basemap.Basemap.Region.Name("argolid"), com.histmersion.api.basemap.Basemap.Region.Bbox("22.5,37.4,23.2,37.9")))
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
    val cs = choices(status.modify(_.bbox).setTo("world"))
    assertEquals(
      cs.map(c => (c.id, c.bbox)),
      List(("configured", Some("world")), ("argolid", Some("22.5,37.4,23.2,37.9")), (OwnBox, None))
    )
    assertEquals(cs.head.label, "the configured region (the whole world)")

  test("the fetch: the map for a box replaces the region; the relief of a named region or of a box of one's own"):
    assertEquals(
      request(com.histmersion.api.catalogue.MapArea.Bbox(" 20,30,40,50 "), Content.Map, None, com.histmersion.web.MapRegions.ZoomText(""), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)),
      Right(Basemap.FetchQuery(bbox = Some(com.histmersion.api.BBox.Text("20,30,40,50")), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)))
    )
    assertEquals(
      request(com.histmersion.api.catalogue.MapArea.Bbox("world"), Content.Map, None, com.histmersion.web.MapRegions.ZoomText("6"), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(false)),
      Right(Basemap.FetchQuery(bbox = Some(com.histmersion.api.BBox.Text("world")), maxZoom = Some(com.histmersion.api.basemap.Basemap.Focus.MaxZoom(6))))
    )
    assertEquals(
      request(com.histmersion.api.catalogue.MapArea.Bbox("22.5,37.4,23.2,37.9"), Content.Relief, Some(com.histmersion.api.basemap.Basemap.Focus.Name("argolid")), com.histmersion.web.MapRegions.ZoomText(""), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)),
      Right(
        Basemap.FetchQuery(
          layer = com.histmersion.api.basemap.Basemap.FetchQuery.Layer("terrain"),
          bbox = Some(com.histmersion.api.BBox.Text("22.5,37.4,23.2,37.9")),
          dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true),
          focus = Some("argolid")
        )
      )
    )
    assertEquals(
      request(com.histmersion.api.catalogue.MapArea.Bbox("1,2,3,4"), Content.Relief, None, com.histmersion.web.MapRegions.ZoomText("11"), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(false)).map(q => (q.focus, q.terrainMaxZoom)),
      Right((Some("bbox-1-2-3-4"), Some(11)))
    )

  test("refused before asking: a box that is not one, a zoom that is not a number in range"):
    assert(request(com.histmersion.api.catalogue.MapArea.Bbox("20,30,10,50"), Content.Map, None, com.histmersion.web.MapRegions.ZoomText(""), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)).left.exists(_.contains("west must be < east")))
    assert(request(com.histmersion.api.catalogue.MapArea.Bbox("nowhere"), Content.Map, None, com.histmersion.web.MapRegions.ZoomText(""), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)).isLeft)
    assert(request(com.histmersion.api.catalogue.MapArea.Bbox("1,2,3,4"), Content.Map, None, com.histmersion.web.MapRegions.ZoomText("deep"), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)).left.exists(_.contains("0 to")))
    assert(request(com.histmersion.api.catalogue.MapArea.Bbox("1,2,3,4"), Content.Map, None, com.histmersion.web.MapRegions.ZoomText("99"), dryRun = com.histmersion.api.basemap.Basemap.FetchQuery.DryRun(true)).isLeft)

  test("a dry run's answer, layer by layer, each with its sizes by zoom"):
    val answer =
      "vector (20261019.pmtiles, zoom 0–2): 6 tiles, would download 1 MB in 2 requests (+1 for the directories); " +
        "by zoom — zoom 0: 1 tile, 40 kB · zoom 1: 1 tile, 90 kB · zoom 2: 4 tiles, 900 kB; bathymetry (zoom 0–7): would download 12 tiles, about 1 MB"
    assertEquals(
      sizes(answer),
      List(
        LayerSizes(
          "vector (20261019.pmtiles, zoom 0–2): 6 tiles, would download 1 MB in 2 requests (+1 for the directories)",
          List(com.histmersion.web.MapRegions.LayerSizes.Zooms("zoom 0: 1 tile, 40 kB"), com.histmersion.web.MapRegions.LayerSizes.Zooms("zoom 1: 1 tile, 90 kB"), com.histmersion.web.MapRegions.LayerSizes.Zooms("zoom 2: 4 tiles, 900 kB"))
        ),
        LayerSizes(com.histmersion.web.MapRegions.LayerSizes.Line("bathymetry (zoom 0–7): would download 12 tiles, about 1 MB"), Nil)
      )
    )
    assertEquals(sizes(com.histmersion.web.MapRegions.SizesText("")), Nil)
