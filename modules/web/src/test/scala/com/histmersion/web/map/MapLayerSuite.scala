package com.histmersion
package web.map

import scala.scalajs.js

/** The map layer's own logic on the fake engine ([[FakeSurface]]): clustering, what is drawn in view, the projection's
  * round trip, geometry bounds, and the software-renderer test.
  */
class MapLayerSuite extends munit.FunSuite:

  test("clustering: a point joins the nearest group whose first point is within the radius, else starts one"):
    val g = Clustering.group(
      Array(0.0, 10.0, 100.0, 105.0, 300.0),
      Array(0.0, 0.0, 0.0, 0.0, 0.0),
      Clustering.GroupRadius(20)
    )
    assertEquals(g.toList, List(0, 0, 1, 1, 2))
    // measured from a group's FIRST point (markercluster's rule): a chain does not keep growing
    assertEquals(
      Clustering.group(Array(0.0, 15.0, 30.0), Array(0.0, 0.0, 0.0), Clustering.GroupRadius(20)).toList,
      List(0, 0, 1)
    )
    assertEquals(
      Clustering.group(Array(1.0, 1.0), Array(1.0, 1.0), Clustering.GroupRadius(0)).toList,
      List(0, 1),
      "radius 0: never"
    )

  test("groups: each bubble stands at the mean of its points; whole zooms; Mercator world pixels"):
    val pts = List(
      Clustering.GroupKey("a") -> LatLon(LatLon.Lat(37.5), LatLon.Lon(32.0)),
      Clustering.GroupKey("b") -> LatLon(LatLon.Lat(37.52), LatLon.Lon(32.0)),
      Clustering.GroupKey("c") -> LatLon(LatLon.Lat(10), LatLon.Lon(-40))
    )
    val gs = Clustering.groups(pts, Clustering.ZoomLevel(7), Clustering.GroupRadius(50))
    assertEquals(
      gs.map(_._1),
      Vector(Vector(Clustering.GroupKey("a"), Clustering.GroupKey("b")), Vector(Clustering.GroupKey("c")))
    )
    assertEqualsDouble(gs.head._2.lat.value, 37.51, 1e-9)
    // zoomed in far enough, the two stand apart
    assertEquals(
      Clustering.groups(pts, Clustering.ZoomLevel(14), Clustering.GroupRadius(50)).map(_._1.size),
      Vector(1, 1, 1)
    )
    assertEquals(
      (Clustering.level(MapSurface.Zoom(6.49)), Clustering.level(MapSurface.Zoom(6.5))),
      (Clustering.ZoomLevel(6), Clustering.ZoomLevel(7))
    )
    val (x, y) = Clustering.world(LatLon.Lat(0), LatLon.Lon(0), 0)
    assertEquals((x, y), (128.0, 128.0))

  test("what is drawn: the groups in view and a margin; a bubble's id is its first point and size"):
    val s = FakeSurface()
    s.setView(LatLon(LatLon.Lat(37.5), LatLon.Lon(32)), MapSurface.Zoom(7))
    val gs =
      Clustering.groups(
        List(
          Clustering.GroupKey("a") -> LatLon(LatLon.Lat(37.5), LatLon.Lon(32.0)),
          Clustering.GroupKey("b") -> LatLon(LatLon.Lat(37.51), LatLon.Lon(32.0)),
          Clustering.GroupKey("far") -> LatLon(LatLon.Lat(-30), LatLon.Lon(-100))
        ),
        Clustering.ZoomLevel(7),
        Clustering.GroupRadius(50)
      )
    val (singles, bubbles) = Clustering.drawn(gs, s)
    assertEquals(
      (singles, bubbles),
      (
        Set.empty[Clustering.GroupKey],
        Map(Clustering.GroupLabel("a|2") -> Vector(Clustering.GroupKey("a"), Clustering.GroupKey("b")))
      )
    )
    s.setView(LatLon(LatLon.Lat(-30), LatLon.Lon(-100)), MapSurface.Zoom(7))
    assertEquals(Clustering.drawn(gs, s)._1, Set(Clustering.GroupKey("far")))

  test("the fake engine projects and unprojects consistently; a pan moves the centre"):
    val s = FakeSurface()
    s.setView(LatLon(LatLon.Lat(38), LatLon.Lon(24)), MapSurface.Zoom(8))
    val p = s.project(LatLon(LatLon.Lat(38.2), LatLon.Lon(24.3)))
    val back = s.unproject(p)
    assertEqualsDouble(back.lat.value, 38.2, 1e-9)
    assertEqualsDouble(back.lon.value, 24.3, 1e-9)
    assertEquals(s.project(s.center), ScreenPt(ScreenPt.X(500), ScreenPt.Y(300)))
    var moved = 0
    s.on(MapEventKind.MoveEnd)(_ => moved += 1)
    s.panBy(MapSurface.PanByDx(100), MapSurface.PanByDy(0))
    assert(s.center.lon.value > 24 && moved == 1)

  test("a geometry's bounds, any nesting; none for an empty one"):
    val poly = js.JSON
      .parse("""{"type":"Polygon","coordinates":[[[30,36],[34,36],[34,39],[30,39],[30,36]]]}""")
      .asInstanceOf[js.Dynamic]
    assertEquals(
      GeoBounds.ofGeometry(poly),
      Some(GeoBounds(GeoBounds.South(36), GeoBounds.West(30), GeoBounds.North(39), GeoBounds.East(34)))
    )
    val pt = js.JSON.parse("""{"type":"Point","coordinates":[22.75,37.73]}""").asInstanceOf[js.Dynamic]
    assertEquals(
      GeoBounds.ofGeometry(pt),
      Some(GeoBounds(GeoBounds.South(37.73), GeoBounds.West(22.75), GeoBounds.North(37.73), GeoBounds.East(22.75)))
    )
    assertEquals(
      GeoBounds.ofGeometry(js.JSON.parse("""{"type":"MultiPolygon","coordinates":[]}""").asInstanceOf[js.Dynamic]),
      None
    )
    assert(
      GeoBounds(GeoBounds.South(36), GeoBounds.West(30), GeoBounds.North(39), GeoBounds.East(34))
        .extend(GeoBounds(GeoBounds.South(10), GeoBounds.West(40), GeoBounds.North(11), GeoBounds.East(41))) ==
        GeoBounds(GeoBounds.South(10), GeoBounds.West(30), GeoBounds.North(39), GeoBounds.East(41))
    )

  test("a software renderer is named by its WebGL renderer string"):
    for r <- List(
        "ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (Subzero) (0x0000C0DE)), SwiftShader driver)",
        "llvmpipe (LLVM 15.0.7, 256 bits)",
        "Microsoft Basic Render Driver",
        "Mesa OffScreen"
      )
    do assert(Capabilities.isSoftware(Capabilities.Renderer(r)), r)
    for r <- List(
        "ANGLE (Apple, ANGLE Metal Renderer: Apple M4 Pro, Unspecified Version)",
        "Mali-G78",
        "NVIDIA GeForce RTX 3060"
      )
    do assert(!Capabilities.isSoftware(Capabilities.Renderer(r)), r)
    // Firefox names the GPU in RENDERER; Chromium and Safari answer a generic name there
    assert(!Capabilities.genericRenderer(Capabilities.Renderer("Apple M1, or similar")))
    for r <- List("WebKit WebGL", "", "Mozilla") do assert(Capabilities.genericRenderer(Capabilities.Renderer(r)), r)

  test("the fake engine's shapes: a restyle keeps the set, a new member sends it again"):
    val s = FakeSurface()
    val set = s.shapes(MapSurface.ShapesId("polities"), ShapeStyle())
    set.set(
      List(
        ShapeFeature(ShapeFeature.Id("a"), js.Dynamic.literal(), ShapeFeature.Colour("red")),
        ShapeFeature(ShapeFeature.Id("b"), js.Dynamic.literal(), ShapeFeature.Colour("blue"))
      )
    )
    set.set(
      List(
        ShapeFeature(ShapeFeature.Id("a"), js.Dynamic.literal(), ShapeFeature.Colour("green")),
        ShapeFeature(ShapeFeature.Id("b"), js.Dynamic.literal(), ShapeFeature.Colour("blue"))
      )
    )
    assertEquals(s.sets("polities").rebuilt, 1)
    assertEquals(s.sets("polities").drawn("a").colour, ShapeFeature.Colour("green"))
    set.set(List(ShapeFeature(ShapeFeature.Id("c"), js.Dynamic.literal())))
    assertEquals((s.sets("polities").rebuilt, set.ids), (2, Seq(ShapeFeature.Id("c"))))
