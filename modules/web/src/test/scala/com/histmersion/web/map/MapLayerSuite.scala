package com.histmersion.web.map

import com.histmersion.api.*

import scala.scalajs.js

/** The map layer's own logic on the fake engine ([[FakeSurface]]): clustering, what is drawn in view,
  * the projection's round trip, geometry bounds, and the software-renderer test. */
class MapLayerSuite extends munit.FunSuite:

  test("clustering: a point joins the nearest group whose first point is within the radius, else starts one"):
    val g = Clustering.group(Array(0.0, 10.0, 100.0, 105.0, 300.0), Array(0.0, 0.0, 0.0, 0.0, 0.0), 20)
    assertEquals(g.toList, List(0, 0, 1, 1, 2))
    // measured from a group's FIRST point (markercluster's rule): a chain does not keep growing
    assertEquals(Clustering.group(Array(0.0, 15.0, 30.0), Array(0.0, 0.0, 0.0), 20).toList, List(0, 0, 1))
    assertEquals(Clustering.group(Array(1.0, 1.0), Array(1.0, 1.0), 0).toList, List(0, 1), "radius 0: never")

  test("groups: each bubble stands at the mean of its points; whole zooms; Mercator world pixels"):
    val pts = List("a" -> LatLon(37.5, 32.0), "b" -> LatLon(37.52, 32.0), "c" -> LatLon(10, -40))
    val gs = Clustering.groups(pts, 7, 50)
    assertEquals(gs.map(_._1), Vector(Vector("a", "b"), Vector("c")))
    assertEqualsDouble(gs.head._2.lat, 37.51, 1e-9)
    // zoomed in far enough, the two stand apart
    assertEquals(Clustering.groups(pts, 14, 50).map(_._1.size), Vector(1, 1, 1))
    assertEquals((Clustering.level(6.49), Clustering.level(6.5)), (6, 7))
    val (x, y) = Clustering.world(0, 0, 0)
    assertEquals((x, y), (128.0, 128.0))

  test("what is drawn: the groups in view and a margin; a bubble's id is its first point and size"):
    val s = FakeSurface()
    s.setView(LatLon(37.5, 32), 7)
    val gs = Clustering.groups(List("a" -> LatLon(37.5, 32.0), "b" -> LatLon(37.51, 32.0), "far" -> LatLon(-30, -100)), 7, 50)
    val (singles, bubbles) = Clustering.drawn(gs, s)
    assertEquals((singles, bubbles), (Set.empty[String], Map("a|2" -> Vector("a", "b"))))
    s.setView(LatLon(-30, -100), 7)
    assertEquals(Clustering.drawn(gs, s)._1, Set("far"))

  test("the fake engine projects and unprojects consistently; a pan moves the centre"):
    val s = FakeSurface()
    s.setView(LatLon(38, 24), 8)
    val p = s.project(LatLon(38.2, 24.3))
    val back = s.unproject(p)
    assertEqualsDouble(back.lat, 38.2, 1e-9)
    assertEqualsDouble(back.lon, 24.3, 1e-9)
    assertEquals(s.project(s.center), ScreenPt(500, 300))
    var moved = 0
    s.on(MapEventKind.MoveEnd)(_ => moved += 1)
    s.panBy(100, 0)
    assert(s.center.lon > 24 && moved == 1)

  test("a geometry's bounds, any nesting; none for an empty one"):
    val poly = js.JSON.parse("""{"type":"Polygon","coordinates":[[[30,36],[34,36],[34,39],[30,39],[30,36]]]}""").asInstanceOf[js.Dynamic]
    assertEquals(GeoBounds.ofGeometry(poly), Some(GeoBounds(36, 30, 39, 34)))
    val pt = js.JSON.parse("""{"type":"Point","coordinates":[22.75,37.73]}""").asInstanceOf[js.Dynamic]
    assertEquals(GeoBounds.ofGeometry(pt), Some(GeoBounds(37.73, 22.75, 37.73, 22.75)))
    assertEquals(GeoBounds.ofGeometry(js.JSON.parse("""{"type":"MultiPolygon","coordinates":[]}""").asInstanceOf[js.Dynamic]), None)
    assert(GeoBounds(36, 30, 39, 34).extend(GeoBounds(10, 40, 11, 41)) == GeoBounds(10, 30, 39, 41))

  test("a software renderer is named by its WebGL renderer string"):
    for r <- List("ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (Subzero) (0x0000C0DE)), SwiftShader driver)",
      "llvmpipe (LLVM 15.0.7, 256 bits)", "Microsoft Basic Render Driver", "Mesa OffScreen") do
      assert(Capabilities.isSoftware(r), r)
    for r <- List("ANGLE (Apple, ANGLE Metal Renderer: Apple M4 Pro, Unspecified Version)", "Mali-G78", "NVIDIA GeForce RTX 3060") do
      assert(!Capabilities.isSoftware(r), r)
    // Firefox names the GPU in RENDERER; Chromium and Safari answer a generic name there
    assert(!Capabilities.genericRenderer("Apple M1, or similar"))
    for r <- List("WebKit WebGL", "", "Mozilla") do assert(Capabilities.genericRenderer(r), r)

  test("the fake engine's shapes: a restyle keeps the set, a new member sends it again"):
    val s = FakeSurface()
    val set = s.shapes("polities", ShapeStyle())
    set.set(List(ShapeFeature("a", js.Dynamic.literal(), "red"), ShapeFeature("b", js.Dynamic.literal(), "blue")))
    set.set(List(ShapeFeature("a", js.Dynamic.literal(), "green"), ShapeFeature("b", js.Dynamic.literal(), "blue")))
    assertEquals(s.sets("polities").rebuilt, 1)
    assertEquals(s.sets("polities").drawn("a").colour, "green")
    set.set(List(ShapeFeature("c", js.Dynamic.literal())))
    assertEquals((s.sets("polities").rebuilt, set.ids), (2, Seq("c")))
