package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.web.MapRegions.*
import com.histmersion.api.basemap.Basemap
import com.histmersion.api.catalogue.MapArea

/** The About window's map regions, as data: what may be picked, the fetch a choice asks for, the sizes. */
class MapRegionsSuite extends munit.FunSuite:

  private val status = Basemap.Status(
    available = Basemap.Status.Available(true),
    dir = Basemap.Status.Dir("/d"),
    bbox = Basemap.Status.Bbox("-12,20,90,62"),
    layers = Nil,
    fetchCommand = Basemap.Status.FetchCommand(""),
    regions = List(Basemap.Region(Basemap.Region.Name("argolid"), Basemap.Region.Bbox("22.5,37.4,23.2,37.9")))
  )

  test("the regions: the configured one, the named ones, the world, a box of one's own"):
    assertEquals(
      choices(status).map(c => (c.id, c.bbox)),
      List[(String, Option[String])](
        ("configured", Some("-12,20,90,62")),
        ("argolid", Some("22.5,37.4,23.2,37.9")),
        ("world", Some("world")),
        (OwnBox, None)
      )
    )

  test("the default configured region is the whole world, listed once"):
    val cs = choices(status.modify(_.bbox).setTo(Basemap.Status.Bbox("world")))
    assertEquals(
      cs.map(c => (c.id, c.bbox)),
      List[(String, Option[String])](("configured", Some("world")), ("argolid", Some("22.5,37.4,23.2,37.9")), (OwnBox, None))
    )
    assertEquals(cs.head.label, "the configured region (the whole world)")

  test("the fetch: the map for a box replaces the region; the relief of a named region or of a box of one's own"):
    assertEquals(
      request(MapArea.Bbox(" 20,30,40,50 "), Content.Map, None, MapRegions.ZoomText(""), dryRun = Basemap.FetchQuery.DryRun(true)),
      Right(Basemap.FetchQuery(bbox = Some(Basemap.FetchQuery.Bbox("20,30,40,50")), dryRun = Basemap.FetchQuery.DryRun(true)))
    )
    assertEquals(
      request(MapArea.Bbox("world"), Content.Map, None, MapRegions.ZoomText("6"), dryRun = Basemap.FetchQuery.DryRun(false)),
      Right(Basemap.FetchQuery(bbox = Some(Basemap.FetchQuery.Bbox("world")), maxZoom = Some(Basemap.Focus.MaxZoom(6))))
    )
    assertEquals(
      request(MapArea.Bbox("22.5,37.4,23.2,37.9"), Content.Relief, Some(Basemap.Focus.Name("argolid")), MapRegions.ZoomText(""), dryRun = Basemap.FetchQuery.DryRun(true)),
      Right(
        Basemap.FetchQuery(
          layer = Basemap.FetchQuery.Layer("terrain"),
          bbox = Some(Basemap.FetchQuery.Bbox("22.5,37.4,23.2,37.9")),
          dryRun = Basemap.FetchQuery.DryRun(true),
          focus = Some(Basemap.FetchQuery.Focus("argolid"))
        )
      )
    )
    assertEquals(
      request(MapArea.Bbox("1,2,3,4"), Content.Relief, None, MapRegions.ZoomText("11"), dryRun = Basemap.FetchQuery.DryRun(false)).map(q => (q.focus, q.terrainMaxZoom)),
      Right((Some("bbox-1-2-3-4"), Some(11)))
    )

  test("refused before asking: a box that is not one, a zoom that is not a number in range"):
    assert(request(MapArea.Bbox("20,30,10,50"), Content.Map, None, MapRegions.ZoomText(""), dryRun = Basemap.FetchQuery.DryRun(true)).left.exists(_.value.contains("west must be < east")))
    assert(request(MapArea.Bbox("nowhere"), Content.Map, None, MapRegions.ZoomText(""), dryRun = Basemap.FetchQuery.DryRun(true)).isLeft)
    assert(request(MapArea.Bbox("1,2,3,4"), Content.Map, None, MapRegions.ZoomText("deep"), dryRun = Basemap.FetchQuery.DryRun(true)).left.exists(_.value.contains("0 to")))
    assert(request(MapArea.Bbox("1,2,3,4"), Content.Map, None, MapRegions.ZoomText("99"), dryRun = Basemap.FetchQuery.DryRun(true)).isLeft)

  test("a dry run's answer, layer by layer, each with its sizes by zoom"):
    val answer = SizesText(
      "vector (20261019.pmtiles, zoom 0–2): 6 tiles, would download 1 MB in 2 requests (+1 for the directories); " +
        "by zoom — zoom 0: 1 tile, 40 kB · zoom 1: 1 tile, 90 kB · zoom 2: 4 tiles, 900 kB; bathymetry (zoom 0–7): would download 12 tiles, about 1 MB"
    )
    assertEquals(
      sizes(answer),
      List(
        LayerSizes(
          LayerSizes.Line("vector (20261019.pmtiles, zoom 0–2): 6 tiles, would download 1 MB in 2 requests (+1 for the directories)"),
          List(MapRegions.LayerSizes.Zooms("zoom 0: 1 tile, 40 kB"), MapRegions.LayerSizes.Zooms("zoom 1: 1 tile, 90 kB"), MapRegions.LayerSizes.Zooms("zoom 2: 4 tiles, 900 kB"))
        ),
        LayerSizes(MapRegions.LayerSizes.Line("bathymetry (zoom 0–7): would download 12 tiles, about 1 MB"), Nil)
      )
    )
    assertEquals(sizes(MapRegions.SizesText("")), Nil)
