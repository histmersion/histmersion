package com.histmersion.util.basemap

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*
import io.circe.Json
import io.circe.parser.parse
import com.histmersion.api.basemap.Basemap

/** The physical names: Natural Earth's classes and names, where a polygon's name goes, the zooms, the region,
  * simplifying and clipping rivers, and the tiles they are written as. Pure.
  */
class BasemapLabelsSuite extends munit.FunSuite:

  private val Lon = BasemapLabels.Label.Lon
  private val Lat = BasemapLabels.Label.Lat
  private val X = BasemapLabels.WorldX
  private val Y = BasemapLabels.WorldY

  private def feature(props: String, geometry: String): Json =
    parse(s"""{"type":"Feature","properties":$props,"geometry":$geometry}""").fold(e => fail(e.toString), identity)

  test("Natural Earth's classes: the physical ones kept, never a reservoir, a canal or a settlement"):
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("Range/mtn")), Some("range"))
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("Desert")), Some("desert"))
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("Pen/cape")), Some("peninsula"))
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("Geoarea")), Some("region"))
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("Island group")), Some("island_group"))
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("mountain")), Some("peak"))
    assertEquals(BasemapLabels.kindOf(com.histmersion.util.basemap.BasemapLabels.FeatureClass("Alkaline Lake")), Some("lake"))
    List("Reservoir", "Canal", "Lake Centerline", "pole", "Populated place", "Admin-0 country", "Road")
      .map(BasemapLabels.FeatureClass(_))
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
    val square = Vector((Lon(0.0), Lat(0.0)), (Lon(4.0), Lat(0.0)), (Lon(4.0), Lat(4.0)), (Lon(0.0), Lat(4.0)))
    assertEquals(BasemapLabels.labelPoint(Vector(square)), Some((2.0, 2.0)))
    // a C open to the east: its centre of area lies in the opening
    val c = Vector(
      (Lon(0.0), Lat(0.0)),
      (Lon(10.0), Lat(0.0)),
      (Lon(10.0), Lat(2.0)),
      (Lon(2.0), Lat(2.0)),
      (Lon(2.0), Lat(8.0)),
      (Lon(10.0), Lat(8.0)),
      (Lon(10.0), Lat(10.0)),
      (Lon(0.0), Lat(10.0))
    )
    val p = BasemapLabels.labelPoint(Vector(c)).get
    assert(BasemapLabels.inside(c, p), p.toString)
    // the largest part wins
    val tiny = Vector((Lon(50.0), Lat(50.0)), (Lon(50.1), Lat(50.0)), (Lon(50.1), Lat(50.1)))
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
    assert(BasemapLabels.parse(com.histmersion.util.basemap.BasemapLabels.FileName("x"), Json.obj()).isLeft)

  test("the region: a point inside (with a margin), a river that crosses it"):
    val b = BBox.parse(com.histmersion.api.BBox.Text("20,35,30,42")).get
    val pt = BasemapLabels.Label(
      BasemapLabels.Label.Kind("peak"),
      List((BasemapLabels.Language("name"), BasemapLabels.Name("A"))),
      Lon(25),
      Lat(38),
      BasemapLabels.Label.MinZoom(6)
    )
    assert(BasemapLabels.within(pt, b))
    assert(BasemapLabels.within(pt.modify(_.lon).setTo(Lon(30.5)), b), "within the margin")
    assert(!BasemapLabels.within(pt.modify(_.lon).setTo(Lon(40)), b))
    val river = pt
      .modify(_.kind)
      .setTo(BasemapLabels.Label.Kind("river"))
      .modify(_.lon)
      .setTo(Lon(50))
      .modify(_.line)
      .setTo(Vector(Vector((Lon(50.0), Lat(50.0)), (Lon(29.0), Lat(41.0)))))
    assert(BasemapLabels.within(river, b))

  test("rivers: simplified to about a pixel, clipped to each tile they cross, relative to its corner"):
    val l = Vector((X(0.0), Y(0.0)), (X(1.0), Y(0.01)), (X(2.0), Y(0.0)), (X(3.0), Y(5.0)), (X(4.0), Y(0.0)))
    assertEquals(BasemapLabels.simplify(l, BasemapLabels.Tolerance(0.5)), Vector((0.0, 0.0), (2.0, 0.0), (3.0, 5.0), (4.0, 0.0)))
    val across = Vector((X(100.0), Y(100.0)), (X(8000.0), Y(100.0)), (X(8000.0), Y(12000.0)))
    assertEquals(BasemapLabels.clip(across, com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(0), com.histmersion.util.basemap.BasemapLabels.Buffer(64)), Vector(Vector((100, 100), (8000, 100))))
    assertEquals(BasemapLabels.clip(across, com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(0), com.histmersion.util.basemap.BasemapLabels.Buffer(64)), Vector(Vector((-3996, 100), (3904, 100), (3904, 12000))))
    assertEquals(BasemapLabels.clip(across, com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(2), com.histmersion.util.basemap.BasemapLabels.Buffer(64)), Vector(Vector((3904, -8092), (3904, 3808))))
    assertEquals(BasemapLabels.clip(across, com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(2), com.histmersion.util.basemap.BasemapLabels.Buffer(64)), Vector.empty)

  test("geometry commands: a point, a line in two parts (deltas carried across parts)"):
    assertEquals(Mvt.pointGeometry(com.histmersion.util.basemap.Mvt.PointX(5), com.histmersion.util.basemap.Mvt.PointY(-3)), Vector(9, 10, 5))
    assertEquals(
      Mvt.lineGeometry(List(List((com.histmersion.util.basemap.Mvt.PointX(1), com.histmersion.util.basemap.Mvt.PointY(1)), (com.histmersion.util.basemap.Mvt.PointX(3), com.histmersion.util.basemap.Mvt.PointY(1))), List((com.histmersion.util.basemap.Mvt.PointX(3), com.histmersion.util.basemap.Mvt.PointY(4)), (com.histmersion.util.basemap.Mvt.PointX(3), com.histmersion.util.basemap.Mvt.PointY(6)), (com.histmersion.util.basemap.Mvt.PointX(0), com.histmersion.util.basemap.Mvt.PointY(6))), List((com.histmersion.util.basemap.Mvt.PointX(9), com.histmersion.util.basemap.Mvt.PointY(9))))),
      Vector(9, 2, 2, 10, 4, 0, 9, 0, 6, 18, 0, 4, 5, 0)
    )
    assertEquals(Mvt.zigzag(com.histmersion.util.basemap.Mvt.SignedNumber(-1)), 1L)
    assertEquals(Mvt.zigzag(com.histmersion.util.basemap.Mvt.SignedNumber(2)), 4L)

  test(
    "tiles: a name from its own zoom (one data zoom early: tile z serves zoom z + 1), all in the deepest, not past its max"
  ):
    val olympus = BasemapLabels.Label(
      com.histmersion.util.basemap.BasemapLabels.Label.Kind("peak"),
      List(
        BasemapLabels.Language("name") -> BasemapLabels.Name("Όλυμπος"),
        BasemapLabels.Language("name:en") -> BasemapLabels.Name("Olympus")
      ),
      Lon(22.35),
      Lat(40.08),
      com.histmersion.util.basemap.BasemapLabels.Label.MinZoom(7),
      elevation = Some(BasemapLabels.Label.Elevation(2917))
    )
    val world = BasemapLabels.Label(com.histmersion.util.basemap.BasemapLabels.Label.Kind("continent"), List(BasemapLabels.Language("name") -> BasemapLabels.Name("Europe")), com.histmersion.util.basemap.BasemapLabels.Label.Lon(15), com.histmersion.util.basemap.BasemapLabels.Label.Lat(50), com.histmersion.util.basemap.BasemapLabels.Label.MinZoom(1), maxZoom = Some(com.histmersion.util.basemap.BasemapLabels.Label.MaxZoom(3)))
    val axios = BasemapLabels.Label(
      com.histmersion.util.basemap.BasemapLabels.Label.Kind("river"),
      List(BasemapLabels.Language("name") -> BasemapLabels.Name("Axios")),
      com.histmersion.util.basemap.BasemapLabels.Label.Lon(22),
      com.histmersion.util.basemap.BasemapLabels.Label.Lat(41),
      com.histmersion.util.basemap.BasemapLabels.Label.MinZoom(5),
      line = Vector(Vector((Lon(21.0), Lat(42.0)), (Lon(22.6), Lat(40.6))))
    )
    assert(
      !BasemapLabels.inTile(olympus, com.histmersion.util.basemap.PmTiles.Zoom(5), com.histmersion.util.basemap.PmTiles.Zoom(8)) && BasemapLabels.inTile(olympus, com.histmersion.util.basemap.PmTiles.Zoom(6), com.histmersion.util.basemap.PmTiles.Zoom(8)) && BasemapLabels.inTile(
        olympus.modify(_.minZoom).setTo(BasemapLabels.Label.MinZoom(12)),
        com.histmersion.util.basemap.PmTiles.Zoom(8),
        com.histmersion.util.basemap.PmTiles.Zoom(8)
      )
    )
    assert(BasemapLabels.inTile(world, com.histmersion.util.basemap.PmTiles.Zoom(2), com.histmersion.util.basemap.PmTiles.Zoom(8)) && !BasemapLabels.inTile(world, com.histmersion.util.basemap.PmTiles.Zoom(3), com.histmersion.util.basemap.PmTiles.Zoom(8)))
    val tiles = BasemapLabels.tiles(List(olympus, world, axios), com.histmersion.util.basemap.PmTiles.Zoom(8))
    assertEquals(tiles.map(_._1), tiles.map(_._1).sortBy[Long](_.value), "in tile-id order, as the archive is written")
    def at(z: PmTiles.Zoom, lon: Longitude, lat: Latitude) =
      val id = PmTiles.tileId(
        z,
        PmTiles.TileColumn(Basemap.tileX(lon, Basemap.Zoom(z.value)).value),
        PmTiles.TileRow(Basemap.tileY(lat, Basemap.Zoom(z.value)).value)
      )
      tiles.find(_._1 == id).map(t => PmTiles.gunzip(PmTiles.CompressedBytes(t._2.value)).fold(e => fail(e.value), identity))
    assertEquals(at(PmTiles.Zoom(6), Longitude(22.35), Latitude(40.08)).map(Mvt.kinds(_, com.histmersion.util.basemap.Mvt.LayerName("physical"))), Some(Right(List("peak"))))
    assertEquals(at(PmTiles.Zoom(5), Longitude(22.35), Latitude(40.08)).map(Mvt.kinds(_, com.histmersion.util.basemap.Mvt.LayerName("physical"))), Some(Right(Nil)), "the river's tile, no peak yet")
    assertEquals(at(PmTiles.Zoom(4), Longitude(22.35), Latitude(40.08)).map(Mvt.kinds(_, com.histmersion.util.basemap.Mvt.LayerName("rivers"))), Some(Right(List("river"))))
    assertEquals(at(PmTiles.Zoom(2), Longitude(15), Latitude(50)).map(Mvt.kinds(_, com.histmersion.util.basemap.Mvt.LayerName("physical"))), Some(Right(List("continent"))))
    assertEquals(at(PmTiles.Zoom(8), Longitude(15), Latitude(50)), None, "the continent is gone deeper")
    val t8 = at(PmTiles.Zoom(8), Longitude(22.35), Latitude(40.08)).get
    assertEquals(Mvt.layerNames(t8), Right(List("physical", "rivers")), "the Axios ends in this tile")
    val text = String(t8.value, "UTF-8")
    assert(text.contains("Olympus") && text.contains("min_zoom") && text.contains("elevation"), text)
