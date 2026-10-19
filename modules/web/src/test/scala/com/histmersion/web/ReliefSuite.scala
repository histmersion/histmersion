package com.histmersion.web

import com.histmersion.core.TintStop

import scala.scalajs.js
import scala.scalajs.js.typedarray.{Float32Array, Int16Array, Uint8ClampedArray}

/** The relief's arithmetic ([[Relief]]), the contours ([[Contours]]) and the names' boxes
  * ([[TerrainLabels]]) on known synthetic grids. Pure. */
class ReliefSuite extends munit.FunSuite:

  private def grid(w: Int, h: Int)(f: (Int, Int) => Double): Float32Array =
    val g = new Float32Array(w * h)
    for i <- 0 until h; j <- 0 until w do g(i * w + j) = f(j, i).toFloat
    g

  test("terrarium decoding to whole metres; the tile a map tile reads and the part of it"):
    val px = new Uint8ClampedArray(12)
    List(128, 0, 0, 255, 128, 100, 128, 255, 127, 255, 0, 255).zipWithIndex.foreach((v, i) => px(i) = v.toShort)
    val out = new Int16Array(3)
    Relief.decode(px, 3, out)
    assertEquals(out.toList.map(_.toInt), List(0, 101, -1))
    assertEquals(Relief.source(9, 288, 197, 12), Relief.Source(8, 288, 197, 0, 0, 512))
    // deeper than the data: a quarter, an eighth … of the deepest tile, at its place in it
    assertEquals(Relief.source(14, 4611, 3166, 12), Relief.Source(12, 2305, 1583, 256, 0, 256))
    assertEquals(Relief.source(16, 18447, 12667, 12), Relief.Source(12, 2305, 1583, 7 * 64.0, 3 * 64.0, 64))
    assertEquals(Relief.source(20, 0, 0, 8).span, 512.0 / (1 << 11))
    assertEquals(Relief.Source(12, 0, 0, 448, 0, 64).window(2), (446, -2, 69))
    assertEquals(Relief.neighboursNeeded(446, -2, 69), List((0, -1), (1, -1), (1, 0)))
    assertEquals(Relief.neighboursNeeded(-1, -1, 515).toSet, Set((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)))

  test("the neighbourhood reads across a tile edge from the neighbour, and holds the edge where there is none"):
    val c = new Int16Array(512 * 512); c(0) = 5; c(511) = 7
    val east = new Int16Array(512 * 512); east(0) = 9
    val ns = new js.Array[Int16Array](9)
    ns(5) = east   // (dx 1, dy 0)
    val nb = Relief.Neighbourhood(c, ns)
    assertEquals(nb.at(0, 0), 5.0)
    assertEquals(nb.at(512, 0), 9.0, "the east neighbour's first pixel")
    assertEquals(nb.at(-1, 0), 5.0, "no west neighbour: the edge is held")
    assertEquals(nb.at(511, -3), 7.0)

  test("bicubic resampling: a plane stays a plane (no blocks), the data's own pixels are kept at their own scale"):
    val n = 16; val pad = 2; val sw = n + 2 * pad + 1
    val src = grid(sw, sw)((x, y) => 3.0 * x + 2.0 * y)
    val r = 64
    val out = new Float32Array((r + 2) * (r + 2)); val tmp = new Float32Array(sw * (r + 2))
    Relief.resample(src, sw, pad.toDouble, pad.toDouble, n, r, out, tmp)
    // output pixel u samples pad + (u + ½)·n/r − ½ of the window: a plane is reproduced exactly
    for (u, v) <- List((0, 0), (10, 20), (33, 47), (63, 63)) do
      val sx = pad + (u + 0.5) * n / r - 0.5; val sy = pad + (v + 0.5) * n / r - 0.5
      assertEqualsDouble(out((v + 1) * (r + 2) + u + 1).toDouble, 3 * sx + 2 * sy, 1e-3)
    // 1:1 — every sample is the data's own value (weights 0, 1, 0, 0)
    val same = new Float32Array((n + 2) * (n + 2))
    Relief.resample(src, sw, pad.toDouble, pad.toDouble, n, n, same, tmp)
    assertEqualsDouble(same(5 * (n + 2) + 7).toDouble, src((pad + 4) * sw + pad + 6).toDouble, 1e-4)
    // smooth: neighbouring output pixels of a steep step differ by far less than the step
    val step = grid(sw, sw)((x, _) => if x < sw / 2 then 0 else 100)
    Relief.resample(step, sw, pad.toDouble, pad.toDouble, n, r, out, tmp)
    val row = (1 to r).map(u => out(10 * (r + 2) + u).toDouble)
    assert(row.zip(row.tail).map((a, b) => math.abs(b - a)).max < 40, row.toString)

  test("the shading: flat ground is transparent, a slope facing the light is lit, one facing away shadowed; several lights shade every side"):
    val w = 3
    val one = Relief.lights(315, 45, 0)
    assertEqualsDouble(one.flat, math.sin(math.toRadians(45)), 1e-9)
    val flat = Relief.illumination(0, 0, 0, 0, 0, 0, 0, 0, 30, 1, one)
    assertEqualsDouble(flat, one.flat, 1e-9)
    // rising to the south-east: faces north-west, the light → lit; rising to the north-west: in shadow
    val lit  = Relief.illumination(0, 10, 20, 10, 30, 20, 30, 40, 30, 1, one)
    val dark = Relief.illumination(40, 30, 20, 30, 10, 20, 10, 0, 30, 1, one)
    assert(dark < flat && flat < lit, (dark, flat, lit).toString)
    // the slope turned away from the main light: black under one light, modelled (lighter) under several
    val many = Relief.lights(315, 45, 1)
    assertEqualsDouble(many.w.sum, 1.0, 1e-9)
    val away = (a: Relief.Lights) => Relief.illumination(40, 30, 20, 30, 10, 20, 10, 0, 30, 1, a) / a.flat
    assert(away(many) > away(one), (away(many), away(one)).toString)
    val g = grid(w + 2, w + 2)((x, y) => if x + y > 4 then 50 else 0)
    val out = new Uint8ClampedArray(w * w * 4)
    Relief.shade(grid(w + 2, w + 2)((_, _) => 0), w, 30, 1, one, (0, 0, 0), 200, (255, 255, 255), 100, out)
    assert((0 until w * w).forall(p => out(p * 4 + 3) == 0), "flat: transparent")
    Relief.shade(g, w, 30, 1, one, (0, 0, 0), 200, (255, 255, 255), 100, out)
    assert((0 until w * w).exists(p => out(p * 4 + 3) > 0))

  test("the hypsometric tint: linear between stops, the ends held, the opacity on every entry"):
    val stops = Relief.stops(List(TintStop(0, "#000000"), TintStop(100, "#ffffff")), Relief.parseColour)
    val lut = Relief.lut(stops, 0.5, step = 10)
    assertEquals(lut.size, 11)
    assertEquals(lut.index(50), 5)
    assertEquals((lut.rgba(5 * 4).toInt, lut.rgba(5 * 4 + 3).toInt), (128, 128))
    assertEquals(lut.index(-100), 0)
    assertEquals(lut.index(1e6), 10)
    val g = grid(3, 3)((_, _) => 100)
    val out = new Uint8ClampedArray(4)
    Relief.tint(g, 1, Some(lut), out)
    assertEquals(out.toList.map(_.toInt), List(255, 255, 255, 128))
    assertEquals(Relief.parseColour("rgba(1, 2, 3, 0.5)"), Some((1, 2, 3, 0.5)))
    assertEquals(Relief.parseColour("#a1b2c3"), Some((161, 178, 195, 1.0)))
    assertEquals(Relief.parseColour("#fff"), Some((255, 255, 255, 1.0)))
    assertEquals(Relief.parseColour("wheat"), None)

  test("contours of a cone: closed rings at every level above sea level, index lines apart, joined into polylines"):
    val n = 101
    // a cone 500 m high at the centre, falling 10 m a cell
    val g = grid(n, n)((x, y) => math.max(0, 500 - 10 * math.hypot(x - 50, y - 50)))
    assertEquals(Contours.levels(-20, 450, 100), List(100.0, 200.0, 300.0, 400.0))
    assert(Contours.isIndex(500, 100, 5) && !Contours.isIndex(300, 100, 5) && Contours.isIndex(1000, 50, 5))
    val t = Contours.trace(g, n, n, 100, 2)
    // 100…400 m: 100 and 300 ordinary, 200 and 400 index lines; sea level never a contour
    assert(t.lines.length > 0 && t.index.length > 0)
    assertEquals(t.indexLevels.toSet, Set(200.0, 400.0))
    // every point of a level lies at that level's radius from the top
    def radius(l: Double) = (500 - l) / 10
    val ok = (0 until t.index.length by 2).forall { k =>
      val l = t.indexLevels(k / 4)
      math.abs(math.hypot(t.index(k) - 50, t.index(k + 1) - 50) - radius(l)) < 0.3
    }
    assert(ok, "on the circle")
    val lines = Contours.polylines(t)
    assertEquals(lines.map(_._1).sorted, List(200.0, 400.0), "one line a level: the segments joined")
    val ring200 = lines.find(_._1 == 200).get._2
    assertEqualsDouble(Contours.length(ring200), 2 * math.Pi * radius(200), 2.0)
    assertEqualsDouble(ring200(0), ring200(ring200.length - 2), 1e-9)   // closed: it ends where it began
    // a label on it: half way along, upright, away from the edges
    val at = Contours.labelAt(ring200, 20, 10, n, 5)
    assert(at.exists((x, y, a) => math.abs(math.hypot(x - 50, y - 50) - 30) < 0.5 && math.abs(a) <= math.Pi / 2), at.toString)
    assertEquals(Contours.labelAt(ring200, 500, 10, n, 5), None, "too short for the label")

  test("a flat or sea grid has no contours; a saddle is resolved by its centre"):
    assertEquals(Contours.trace(grid(5, 5)((_, _) => 0), 5, 5, 10, 5).lines.length, 0)
    assertEquals(Contours.trace(grid(5, 5)((_, _) => 37), 5, 5, 10, 5).lines.length, 0)
    val saddle = new Float32Array(js.Array[Float](10, 0, 0, 10))
    val t = Contours.trace(saddle, 2, 2, 6, 0)
    assertEquals(t.lines.length, 8, "two segments")

  test("names: wrapped at spaces and hyphens; the box holds every line, the halo and the margin"):
    assertEquals(TerrainLabels.wrap("Gulf of Corinth", 8), List("Gulf of", "Corinth"))
    assertEquals(TerrainLabels.wrap("Ionian Sea", 15), List("Ionian Sea"))
    assertEquals(TerrainLabels.wrap("Peloponnese", 5), List("Peloponnese"), "a word is never cut")
    assertEquals(TerrainLabels.wrap("Agios-Nikolaos Bay", 9), List("Agios-", "Nikolaos", "Bay"))
    val (hw, hh, bl) = TerrainLabels.block(List(80, 40), ascent = 10, descent = 3, lineGap = 14, margin = 4)
    assertEquals((hw, hh), (44.0, (10 + 3 + 14) / 2.0 + 4))
    // the first line's ascent reaches the box's inner top, the last descent its inner bottom
    assertEqualsDouble(bl.head - 10, -(hh - 4), 1e-9)
    assertEqualsDouble(bl.last + 3, hh - 4, 1e-9)
    assertEquals(bl(1) - bl(0), 14.0, "baselines a line height apart, not the longest line's ink height")
    assertEquals(TerrainLabels.transform("aegean sea", Some("capitalize")), "Aegean Sea")
    assert(TerrainLabels.isCut(0, 5, 0, 9, 0, 0, 10, 10) && !TerrainLabels.isCut(1, 5, 1, 9, 0, 0, 10, 10))
    assert(!TerrainLabels.isCut(0, 5, 3, 9, 0, 0, 10, 10), "a diagonal is a coast")
