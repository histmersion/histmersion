package com.histmersion.server.basemap

import com.histmersion.api.BBox

import java.nio.{ByteBuffer, ByteOrder}

/** The GeoTIFF reader over grids written the way GEBCO writes its own (little-endian, one band of uncompressed signed
  * 16-bit rows in strips, a tie point and a pixel scale), and the ones it refuses.
  */
class GeoTiffSuite extends munit.FunSuite:
  import GeoTiffSuite.*

  private def parsed(b: Array[Byte]) = GeoTiffGrid.parse(b.take(64), (o, l) => Right(b.slice(o.toInt, o.toInt + l)))

  test("a GEBCO-shaped grid: where it lies, its rows, its no-data value (the strip offsets read past the head)"):
    val g =
      parsed(tiff(width = 8, height = 6, west = 10, north = 50, step = 0.5, value = (r, c) => (-100 * r - c).toShort))
        .fold(e => fail(e), identity)
    assertEquals((g.west.value, g.north.value, g.stepX.value, g.width.value, g.height.value), (10.0, 50.0, 0.5, 8, 6))
    assertEquals((g.east, g.south), (14.0, 47.0))
    assertEquals(g.noData, Some(-32768.toShort))
    assertEquals(g.rowOffsets.map(_.value).take(2), Vector(DataAt.toLong, DataAt + 16L))
    assertEquals(g.pixelAt(10.75, 49.25), (1.5, 1.5))

  test("pixel-is-point grids are shifted half a pixel to their area"):
    val g = parsed(tiff(8, 6, west = 10, north = 50, step = 0.5, point = true)).fold(e => fail(e), identity)
    assertEquals((g.west.value, g.north.value), (9.75, 50.25))

  test("the window of a box, half-open on the east and south; none for a box that only touches the grid"):
    val g = parsed(tiff(8, 6, west = 10, north = 50, step = 0.5)).fold(e => fail(e), identity)
    assertEquals(g.window(BBox(10.6, 48.1, 11.4, 49.9)), Some(GridWindow(0, 3, 1, 2)))
    assertEquals(g.window(BBox(0, 0, 100, 80)), Some(GridWindow(0, 5, 0, 7)))
    assertEquals(g.window(BBox(14, 40, 20, 45)), None)
    assertEquals(g.window(BBox(0, 50, 20, 60)), None)

  test("row runs: whole rows that follow each other share a request, at most the size asked"):
    val g = parsed(tiff(8, 6, west = 10, north = 50, step = 0.5)).fold(e => fail(e), identity)
    val w = GridWindow(1, 5, 2, 3)
    assertEquals(
      g.runs(w, 1 << 20).map(r => (r.firstRow, r.rows, r.offset.value, r.length)),
      List((1, 5, DataAt + 16L, 80L))
    )
    assertEquals(g.runs(w, 32).map(r => (r.firstRow, r.rows)), List((1, 2), (3, 2), (5, 1)))
    assertEquals(g.runs(w, 1).map(_.rows), List(1, 1, 1, 1, 1), "a request holds a row at least")

  test("refused: big-endian, BigTIFF, compressed, unsigned, tiled, or no georeference"):
    def why(b: Array[Byte]) = parsed(b).left.getOrElse(fail("read"))
    val ok = tiff(4, 4, 0, 10, 1)
    assert(why("MM".getBytes ++ ok.drop(2)).contains("little-endian"))
    assert(why(ok.updated(2, 43.toByte)).contains("BigTIFF"))
    assert(why(tiff(4, 4, 0, 10, 1, compression = 8)).contains("compression 8"))
    assert(why(tiff(4, 4, 0, 10, 1, sampleFormat = 1)).contains("sample format 1"))
    assert(why(tiff(4, 4, 0, 10, 1, tiled = true)).contains("tiled"))
    assert(why(tiff(4, 4, 0, 10, 1, georeferenced = false)).contains("not a GeoTIFF"))

object GeoTiffSuite:
  /** Where the samples begin in [[tiff]]'s files. */
  val DataAt = 1024

  /** A little-endian GeoTIFF of `width` × `height` signed 16-bit samples, one row a strip: the directory first, the
    * samples from [[DataAt]], the directory's arrays after them.
    */
  def tiff(
      width: Int,
      height: Int,
      west: Double,
      north: Double,
      step: Double,
      value: (Int, Int) => Short = (_, _) => 0,
      point: Boolean = false,
      compression: Int = 1,
      sampleFormat: Int = 2,
      tiled: Boolean = false,
      georeferenced: Boolean = true
  ): Array[Byte] =
    val dataBytes = width * height * 2
    val buf = ByteBuffer.allocate(DataAt + dataBytes + height * 8 + 256).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("II".getBytes).putShort(42).putInt(8)
    // arrays after the directory
    var at = DataAt + dataBytes // the next free byte for an array
    def array(write: ByteBuffer => Unit, size: Int): Int = { val o = at; buf.position(o); write(buf); at += size; o }
    val offsets = array(b => (0 until height).foreach(r => b.putInt(DataAt + r * width * 2)), height * 4)
    val counts = array(b => (0 until height).foreach(_ => b.putInt(width * 2)), height * 4)
    val scale = array(b => { b.putDouble(step); b.putDouble(step); b.putDouble(0) }, 24)
    val tie = array(
      b => { b.putDouble(0); b.putDouble(0); b.putDouble(0); b.putDouble(west); b.putDouble(north); b.putDouble(0) },
      48
    )
    val keys = array(b => List(1, 1, 0, 1, 1025, 0, 1, if point then 2 else 1).foreach(k => b.putShort(k.toShort)), 16)
    val noData = array(b => b.put("-32768\u0000".getBytes), 8)
    val entries = List[(Int, Int, Int, Int)](
      (256, 3, 1, width),
      (257, 3, 1, height),
      (258, 3, 1, 16),
      (259, 3, 1, compression),
      (273, 4, height, offsets),
      (277, 3, 1, 1),
      (278, 3, 1, 1),
      (279, 4, height, counts),
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
    for r <- 0 until height; c <- 0 until width do buf.putShort(value(r, c))
    buf.array()
