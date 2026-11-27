package com.histmersion
package util.basemap

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.ByteCount
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{Latitude, Longitude}

import java.nio.{ByteBuffer, ByteOrder}
import com.histmersion.lint.{tolerated, LintRule}

/** The GeoTIFF reader over grids written the way GEBCO writes its own (little-endian, one band of uncompressed signed
  * 16-bit rows in strips, a tie point and a pixel scale), and the ones it refuses.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class GeoTiffSuite extends munit.FunSuite:
  import GeoTiffSuite.*

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def parsed(b: Array[Byte]) =
    GeoTiffGrid.parse(
      RangeHttp.ResponseBytes(b.take(64)),
      (o, l) => Right(RangeHttp.ResponseBytes(b.slice(o.value.toInt, o.value.toInt + l.value.toInt)))
    )

  /** A 4 × 4 grid at 0°E 10°N, one degree a pixel, with the given TIFF fields. */
  private def small(
      compression: Int = 1,
      sampleFormat: Int = 2,
      tiled: Boolean = false,
      georeferenced: Boolean = true
  ): Array[Byte] =
    tiff(
      PixelCount(4),
      PixelCount(4),
      Degrees(0),
      Degrees(10),
      Degrees.PerPixel(1),
      compression = compression,
      sampleFormat = sampleFormat,
      tiled = tiled,
      georeferenced = georeferenced
    )

  test("a GEBCO-shaped grid: where it lies, its rows, its no-data value (the strip offsets read past the head)"):
    val g =
      parsed(
        tiff(
          width = PixelCount(8),
          height = PixelCount(6),
          west = Degrees(10),
          north = Degrees(50),
          step = Degrees.PerPixel(0.5),
          value = (r, c) => (-100 * r - c).toShort
        )
      )
        .fold(e => fail(e.sentence), identity)
    assertEquals((g.west.value, g.north.value, g.stepX.value, g.width.value, g.height.value), (10.0, 50.0, 0.5, 8, 6))
    assertEquals((g.east, g.south), (14.0, 47.0))
    assertEquals(g.noData, Some(-32768.toShort))
    assertEquals(g.rowOffsets.map(_.value).take(2), Vector(DataAt.toLong, DataAt + 16L))
    assertEquals(g.pixelAt(Degrees(10.75), Degrees(49.25)), (1.5, 1.5))

  test("pixel-is-point grids are shifted half a pixel to their area"):
    val g = parsed(
      tiff(
        PixelCount(8),
        PixelCount(6),
        west = Degrees(10),
        north = Degrees(50),
        step = Degrees.PerPixel(0.5),
        point = true
      )
    ).fold(e => fail(e.sentence), identity)
    assertEquals((g.west.value, g.north.value), (9.75, 50.25))

  test("the window of a box, half-open on the east and south; none for a box that only touches the grid"):
    val g = parsed(
      tiff(PixelCount(8), PixelCount(6), west = Degrees(10), north = Degrees(50), step = Degrees.PerPixel(0.5))
    ).fold(e => fail(e.sentence), identity)
    assertEquals(
      g.window(BBox(Longitude(10.6), Latitude(48.1), Longitude(11.4), Latitude(49.9))),
      Some(GridWindow(GridWindow.First(0), GridWindow.Last(3), GridWindow.FirstCol(1), GridWindow.LastCol(2)))
    )
    assertEquals(
      g.window(BBox(Longitude(0), Latitude(0), Longitude(100), Latitude(80))),
      Some(GridWindow(GridWindow.First(0), GridWindow.Last(5), GridWindow.FirstCol(0), GridWindow.LastCol(7)))
    )
    assertEquals(g.window(BBox(Longitude(14), Latitude(40), Longitude(20), Latitude(45))), None)
    assertEquals(g.window(BBox(Longitude(0), Latitude(50), Longitude(20), Latitude(60))), None)

  test("row runs: whole rows that follow each other share a request, at most the size asked"):
    val g = parsed(
      tiff(PixelCount(8), PixelCount(6), west = Degrees(10), north = Degrees(50), step = Degrees.PerPixel(0.5))
    ).fold(e => fail(e.sentence), identity)
    val w = GridWindow(GridWindow.First(1), GridWindow.Last(5), GridWindow.FirstCol(2), GridWindow.LastCol(3))
    assertEquals(
      g.runs(w, ByteCount.applyUnsafe(1L << 20)).map(r => (r.firstRow, r.rows, r.offset.value, r.length)),
      List((1, 5, DataAt + 16L, 80L))
    )
    assertEquals(g.runs(w, ByteCount(32)).map(r => (r.firstRow, r.rows)), List((1, 2), (3, 2), (5, 1)))
    assertEquals(g.runs(w, ByteCount(1)).map(_.rows), List(1, 1, 1, 1, 1), "a request holds a row at least")

  test("refused: big-endian, BigTIFF, compressed, unsigned, tiled, or no georeference"):
    def why(b: Array[Byte]) = parsed(b).left.getOrElse(fail("read")).sentence
    val ok = small()
    assert(why("MM".getBytes ++ ok.drop(2)).contains("little-endian"))
    assert(why(ok.updated(2, 43.toByte)).contains("BigTIFF"))
    assert(why(small(compression = 8)).contains("compression 8"))
    assert(why(small(sampleFormat = 1)).contains("sample format 1"))
    assert(why(small(tiled = true)).contains("tiled"))
    assert(why(small(georeferenced = false)).contains("not a GeoTIFF"))

object GeoTiffSuite:
  /** Where the samples begin in [[tiff]]'s files. */
  val DataAt = 1024

  /** A little-endian GeoTIFF of `width` × `height` signed 16-bit samples, one row a strip: the directory first, the
    * samples from [[DataAt]], the directory's arrays after them.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tiff(
      width: PixelCount,
      height: PixelCount,
      west: Degrees,
      north: Degrees,
      step: Degrees.PerPixel,
      value: (Int, Int) => Short = (_, _) => 0,
      point: Boolean = false,
      compression: Int = 1,
      sampleFormat: Int = 2,
      tiled: Boolean = false,
      georeferenced: Boolean = true
  ): Array[Byte] =
    val cols = width.value
    val rows = height.value
    val dataBytes = cols * rows * 2
    val buf = ByteBuffer.allocate(DataAt + dataBytes + rows * 8 + 256).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("II".getBytes).putShort(42).putInt(8)
    // arrays after the directory
    var at = DataAt + dataBytes // the next free byte for an array
    def array(write: ByteBuffer => Unit, size: Int): Int = { val o = at; buf.position(o); write(buf); at += size; o }
    val offsets = array(b => (0 until rows).foreach(r => b.putInt(DataAt + r * cols * 2)), rows * 4)
    val counts = array(b => (0 until rows).foreach(_ => b.putInt(cols * 2)), rows * 4)
    val scale = array(b => { b.putDouble(step.value); b.putDouble(step.value); b.putDouble(0) }, 24)
    val tie = array(
      b => {
        b.putDouble(0); b.putDouble(0); b.putDouble(0); b.putDouble(west.value); b.putDouble(north.value);
        b.putDouble(0)
      },
      48
    )
    val keys = array(b => List(1, 1, 0, 1, 1025, 0, 1, if point then 2 else 1).foreach(k => b.putShort(k.toShort)), 16)
    val noData = array(b => b.put("-32768\u0000".getBytes), 8)
    val entries = List[(Int, Int, Int, Int)](
      (256, 3, 1, cols),
      (257, 3, 1, rows),
      (258, 3, 1, 16),
      (259, 3, 1, compression),
      (273, 4, rows, offsets),
      (277, 3, 1, 1),
      (278, 3, 1, 1),
      (279, 4, rows, counts),
      (284, 3, 1, 1),
      (339, 3, 1, sampleFormat)
    ) ++
      (if tiled then List((322, 3, 1, 256)) else Nil) ++
      (if georeferenced then List((33550, 12, 3, scale), (33922, 12, 6, tie), (34735, 3, 8, keys)) else Nil) ++
      List((42113, 2, 7, noData))
    buf.position(8).putShort(entries.size.toShort)
    entries.sortBy(_._1).foreach { (tag, kind, count, v) =>
      buf.putShort(tag.toShort).putShort(kind.toShort).putInt(count)
      if kind == 3 && count == 1 then buf.putShort(v.toShort).putShort(0) else buf.putInt(v)
    }
    buf.putInt(0)
    buf.position(DataAt)
    for r <- 0 until rows; c <- 0 until cols do buf.putShort(value(r, c))
    buf.array()
