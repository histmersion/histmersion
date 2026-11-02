package com.histmersion.util.basemap

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*
import io.circe.Json
import io.circe.parser.parse
import com.histmersion.api.basemap.Basemap

/** The physical names: Natural Earth's classes and names, where a polygon's name goes, the zooms, the region,
  * simplifying and clipping rivers, and the tiles they are written as. Pure.
  */
class BasemapLabelsSuite extends munit.FunSuite:

  private def feature(props: String, geometry: String): Json =
    parse(s"""{"type":"Feature","properties":$props,"geometry":$geometry}""").fold(e => fail(e.toString), identity)

  test("Natural Earth's classes: the physical ones kept, never a reservoir, a canal or a settlement"):
    assertEquals(BasemapLabels.kindOf("Range/mtn"), Some("range"))
    assertEquals(BasemapLabels.kindOf("Desert"), Some("desert"))
    assertEquals(BasemapLabels.kindOf("Pen/cape"), Some("peninsula"))
    assertEquals(BasemapLabels.kindOf("Geoarea"), Some("region"))
    assertEquals(BasemapLabels.kindOf("Island group"), Some("island_group"))
    assertEquals(BasemapLabels.kindOf("mountain"), Some("peak"))
    assertEquals(BasemapLabels.kindOf("Alkaline Lake"), Some("lake"))
    List("Reservoir", "Canal", "Lake Centerline", "pole", "Populated place", "Admin-0 country", "Road")
      .foreach(c => assertEquals(BasemapLabels.kindOf(c), None, c))

  test("names: Natural Earth's own and each language, upper-case keys too, Chinese as Protomaps names it"):
    val p = parse(
      """{"NAME":"Taurus Mts.","NAME_EN":"Taurus Mountains","NAME_TR":"Toros Dağları","NAME_ZH":"托罗斯山脉","NAME_ZHT":"托羅斯山脈","NAMEALT":null,"name_xyzw":"no"}"""
    ).toOption.flatMap(_.asObject).get
    assertEquals(
      BasemapLabels.names(p),
      List(
        "name" -> "Taurus Mts.",
        "name:en" -> "Taurus Mountains",
        "name:tr" -> "Toros Dağları",
        "name:zh-Hans" -> "托罗斯山脉",
        "name:zh-Hant" -> "托羅斯山脈"
      )
    )

  test("the zoom a name starts at: min_label, else min_zoom, else scalerank; rounded"):
    def z(s: String) = BasemapLabels.minZoomOf(parse(s).toOption.flatMap(_.asObject).get)
    assertEquals(z("""{"MIN_LABEL": 6.7, "min_zoom": 2}"""), 7)
    assertEquals(z("""{"min_label": 3.2}"""), 3)
    assertEquals(z("""{"min_zoom": 5}"""), 5)
    assertEquals(z("""{"scalerank": 3}"""), 4)
    assertEquals(z("""{}"""), 6)

  test("a polygon's name sits inside it — a crescent's centroid does not, the widest stretch does"):
    val square = Vector((0.0, 0.0), (4.0, 0.0), (4.0, 4.0), (0.0, 4.0))
    assertEquals(BasemapLabels.labelPoint(Vector(square)), Some((2.0, 2.0)))
    // a C open to the east: its centre of area lies in the opening
    val c = Vector((0.0, 0.0), (10.0, 0.0), (10.0, 2.0), (2.0, 2.0), (2.0, 8.0), (10.0, 8.0), (10.0, 10.0), (0.0, 10.0))
    val p = BasemapLabels.labelPoint(Vector(c)).get
    assert(BasemapLabels.inside(c, p), p.toString)
    // the largest part wins
    val tiny = Vector((50.0, 50.0), (50.1, 50.0), (50.1, 50.1))
    assertEquals(BasemapLabels.labelPoint(Vector(tiny, square)), Some((2.0, 2.0)))
    assertEquals(BasemapLabels.labelPoint(Vector.empty), None)

  test("features: points, polygons and rivers as labels; an unnamed or unkept feature is none"):
    val range = BasemapLabels.label(
      feature(
        """{"FEATURECLA":"Range/mtn","NAME":"Pindus","NAME_EN":"Pindus","MIN_LABEL":5,"MAX_LABEL":10}""",
        """{"type":"MultiPolygon","coordinates":[[[[20,39],[22,39],[22,41],[20,41],[20,39]]]]}"""
      )
    )
    assertEquals(
      range.map(l => (l.kind, l.lon, l.lat, l.minZoom, l.maxZoom, l.line)),
      Some(("range", 21.0, 40.0, 5, Some(10), Vector.empty))
    )
    val peak = BasemapLabels.label(
      feature(
        """{"featurecla":"mountain","name":"Olympus","elevation":2917,"min_zoom":7}""",
        """{"type":"Point","coordinates":[22.35,40.08]}"""
      )
    )
    assertEquals(peak.map(l => (l.kind, l.elevation, l.minZoom)), Some(("peak", Some(2917L), 7)))
    val river = BasemapLabels.label(
      feature(
        """{"featurecla":"River","name":"Axios","min_label":6}""",
        """{"type":"MultiLineString","coordinates":[[[21,42],[22,41],[22.6,40.6]],[[20,40],[20.5,40.5]]]}"""
      )
    )
    assertEquals(river.map(l => (l.kind, l.line.size, l.line.head.size)), Some(("river", 2, 3)))
    assertEquals(
      BasemapLabels.label(
        feature("""{"featurecla":"Reservoir","name":"X"}""", """{"type":"Point","coordinates":[0,0]}""")
      ),
      None
    )
    assertEquals(
      BasemapLabels.label(feature("""{"featurecla":"Desert"}""", """{"type":"Point","coordinates":[0,0]}""")),
      None
    )
    assert(BasemapLabels.parse("x", Json.obj()).isLeft)

  test("the region: a point inside (with a margin), a river that crosses it"):
    val b = BBox.parse("20,35,30,42").get
    val pt = BasemapLabels.Label("peak", List("name" -> "A"), 25, 38, 6)
    assert(BasemapLabels.within(pt, b))
    assert(BasemapLabels.within(pt.copy(lon = 30.5), b), "within the margin")
    assert(!BasemapLabels.within(pt.copy(lon = 40), b))
    val river = pt.copy(kind = "river", lon = 50, line = Vector(Vector((50.0, 50.0), (29.0, 41.0))))
    assert(BasemapLabels.within(river, b))

  test("rivers: simplified to about a pixel, clipped to each tile they cross, relative to its corner"):
    val l = Vector((0.0, 0.0), (1.0, 0.01), (2.0, 0.0), (3.0, 5.0), (4.0, 0.0))
    assertEquals(BasemapLabels.simplify(l, 0.5), Vector((0.0, 0.0), (2.0, 0.0), (3.0, 5.0), (4.0, 0.0)))
    val across = Vector((100.0, 100.0), (8000.0, 100.0), (8000.0, 12000.0))
    assertEquals(BasemapLabels.clip(across, 0, 0, 64), Vector(Vector((100, 100), (8000, 100))))
    assertEquals(BasemapLabels.clip(across, 1, 0, 64), Vector(Vector((-3996, 100), (3904, 100), (3904, 12000))))
    assertEquals(BasemapLabels.clip(across, 1, 2, 64), Vector(Vector((3904, -8092), (3904, 3808))))
    assertEquals(BasemapLabels.clip(across, 0, 2, 64), Vector.empty)

  test("geometry commands: a point, a line in two parts (deltas carried across parts)"):
    assertEquals(Mvt.pointGeometry(5, -3), Vector(9, 10, 5))
    assertEquals(
      Mvt.lineGeometry(List(List((1, 1), (3, 1)), List((3, 4), (3, 6), (0, 6)), List((9, 9)))),
      Vector(9, 2, 2, 10, 4, 0, 9, 0, 6, 18, 0, 4, 5, 0)
    )
    assertEquals(Mvt.zigzag(-1), 1L)
    assertEquals(Mvt.zigzag(2), 4L)

  test(
    "tiles: a name from its own zoom (one data zoom early: tile z serves zoom z + 1), all in the deepest, not past its max"
  ):
    val olympus = BasemapLabels.Label(
      "peak",
      List("name" -> "Όλυμπος", "name:en" -> "Olympus"),
      22.35,
      40.08,
      7,
      elevation = Some(2917)
    )
    val world = BasemapLabels.Label("continent", List("name" -> "Europe"), 15, 50, 1, maxZoom = Some(3))
    val axios = BasemapLabels.Label(
      "river",
      List("name" -> "Axios"),
      22,
      41,
      5,
      line = Vector(Vector((21.0, 42.0), (22.6, 40.6)))
    )
    assert(
      !BasemapLabels.inTile(olympus, 5, 8) && BasemapLabels.inTile(olympus, 6, 8) && BasemapLabels.inTile(
        olympus.copy(minZoom = 12),
        8,
        8
      )
    )
    assert(BasemapLabels.inTile(world, 2, 8) && !BasemapLabels.inTile(world, 3, 8))
    val tiles = BasemapLabels.tiles(List(olympus, world, axios), 8)
    assertEquals(tiles.map(_._1), tiles.map(_._1).sorted, "in tile-id order, as the archive is written")
    def at(z: Int, lon: Double, lat: Double) =
      val id = PmTiles.tileId(z, Basemap.tileX(lon, z), Basemap.tileY(lat, z))
      tiles.find(_._1 == id).map(t => PmTiles.gunzip(t._2).fold(e => fail(e), identity))
    assertEquals(at(6, 22.35, 40.08).map(Mvt.kinds(_, "physical")), Some(Right(List("peak"))))
    assertEquals(at(5, 22.35, 40.08).map(Mvt.kinds(_, "physical")), Some(Right(Nil)), "the river's tile, no peak yet")
    assertEquals(at(4, 22.35, 40.08).map(Mvt.kinds(_, "rivers")), Some(Right(List("river"))))
    assertEquals(at(2, 15, 50).map(Mvt.kinds(_, "physical")), Some(Right(List("continent"))))
    assertEquals(at(8, 15, 50), None, "the continent is gone deeper")
    val t8 = at(8, 22.35, 40.08).get
    assertEquals(Mvt.layerNames(t8), Right(List("physical", "rivers")), "the Axios ends in this tile")
    val text = String(t8, "UTF-8")
    assert(text.contains("Olympus") && text.contains("min_zoom") && text.contains("elevation"), text)
