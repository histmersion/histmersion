package com.histmersion
package util.basemap

import com.histmersion.api.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import io.circe.Json

import java.nio.file.Files

/** The PMTiles codec, reader and writer (pure, no network, no database). */
class PmTilesSuite extends munit.FunSuite:

  test("tile ids follow the spec's Hilbert numbering (its own examples)"):
    assertEquals(PmTiles.tileId(PmTiles.Zoom(0), PmTiles.TileColumn(0), PmTiles.TileRow(0)), 0L)
    assertEquals(PmTiles.tileId(PmTiles.Zoom(1), PmTiles.TileColumn(0), PmTiles.TileRow(0)), 1L)
    assertEquals(PmTiles.tileId(PmTiles.Zoom(1), PmTiles.TileColumn(0), PmTiles.TileRow(1)), 2L)
    assertEquals(PmTiles.tileId(PmTiles.Zoom(1), PmTiles.TileColumn(1), PmTiles.TileRow(1)), 3L)
    assertEquals(PmTiles.tileId(PmTiles.Zoom(1), PmTiles.TileColumn(1), PmTiles.TileRow(0)), 4L)
    assertEquals(PmTiles.tileId(PmTiles.Zoom(2), PmTiles.TileColumn(0), PmTiles.TileRow(0)), 5L)
    assertEquals(PmTiles.zoomBase(PmTiles.Zoom(12)), 5592405L)
    // go-pmtiles: ZxyToID(3, 3, 3) = 31… check by the inverse instead, over whole zooms
    for z <- 0 to 6; x <- 0L until (1L << z); y <- 0L until (1L << z) do
      assertEquals(
        PmTiles.zxy(PmTiles.tileId(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y))),
        (z, x, y)
      )

  test("ids at one zoom are a permutation of its range"):
    val z = PmTiles.Zoom(4)
    val ids = for x <- 0L until 16L; y <- 0L until 16L
    yield PmTiles.tileId(z, PmTiles.TileColumn(x), PmTiles.TileRow(y))
    assertEquals(
      ids.sortBy[Long](_.value),
      (PmTiles.zoomBase(PmTiles.Zoom(4)).value until PmTiles.zoomBase(PmTiles.Zoom(5)).value).toVector
    )

  test("the header round-trips"):
    val h = PmTiles.Header(
      PmTiles.Header.RootOffset(127),
      PmTiles.Header.RootLength(300),
      PmTiles.Header.MetadataOffset(427),
      PmTiles.Header.MetadataLength(50),
      PmTiles.Header.LeafOffset(477),
      PmTiles.Header.LeafLength(0),
      PmTiles.Header.DataOffset(477),
      PmTiles.Header.DataLength(9999),
      PmTiles.Header.AddressedTiles(10),
      PmTiles.Header.TileEntries(8),
      PmTiles.Header.TileContents(7),
      PmTiles.Header.Clustered(true),
      PmTiles.Compression.Gzip,
      PmTiles.Compression.Gzip,
      PmTiles.TileType.Mvt,
      PmTiles.Header.MinZoom(0),
      PmTiles.Header.MaxZoom(10),
      PmTiles.Header.MinLon(-12.5),
      PmTiles.Header.MinLat(20.25),
      PmTiles.Header.MaxLon(90.0),
      PmTiles.Header.MaxLat(62.0),
      PmTiles.Header.CenterZoom(5),
      PmTiles.Header.CenterLon(30.0),
      PmTiles.Header.CenterLat(38.0)
    )
    val b = PmTiles.encodeHeader(h)
    assertEquals(b.value.length, 127)
    assertEquals(PmTiles.parseHeader(b), Right(h))
    assert(PmTiles.parseHeader(PmTiles.HeaderBytes(Array.fill(127)(0.toByte))).isLeft)

  test("a directory round-trips, contiguous offsets stored as 0"):
    val es = Vector(
      PmTiles
        .Entry(PmTiles.Entry.TileId(0), PmTiles.Entry.Offset(0), PmTiles.Entry.Length(10), PmTiles.Entry.RunLength(1)),
      PmTiles
        .Entry(PmTiles.Entry.TileId(1), PmTiles.Entry.Offset(10), PmTiles.Entry.Length(20), PmTiles.Entry.RunLength(3)),
      PmTiles
        .Entry(PmTiles.Entry.TileId(9), PmTiles.Entry.Offset(0), PmTiles.Entry.Length(10), PmTiles.Entry.RunLength(1)),
      PmTiles.Entry(
        PmTiles.Entry.TileId(20),
        PmTiles.Entry.Offset(500),
        PmTiles.Entry.Length(7),
        PmTiles.Entry.RunLength(0)
      )
    )
    assertEquals(PmTiles.decodeDirectory(PmTiles.encodeDirectory(es)), Right(es))
    assert(PmTiles.decodeDirectory(PmTiles.PlainBytes(Array(5.toByte))).isLeft)

  test("find: a run covers its ids, a leaf is returned to look further"):
    val es = Vector(
      PmTiles
        .Entry(PmTiles.Entry.TileId(1), PmTiles.Entry.Offset(0), PmTiles.Entry.Length(10), PmTiles.Entry.RunLength(3)),
      PmTiles.Entry(
        PmTiles.Entry.TileId(10),
        PmTiles.Entry.Offset(10),
        PmTiles.Entry.Length(5),
        PmTiles.Entry.RunLength(0)
      )
    )
    assertEquals(PmTiles.find(es, PmTiles.Entry.TileId(0)), None)
    assertEquals(PmTiles.find(es, PmTiles.Entry.TileId(3)).map(_.offset), Some(0L))
    assertEquals(PmTiles.find(es, PmTiles.Entry.TileId(4)), None)
    assertEquals(PmTiles.find(es, PmTiles.Entry.TileId(99)).map(_.isLeaf), Some(true))

  private def template(maxZoom: PmTiles.Zoom) = PmTiles.Header(
    PmTiles.Header.RootOffset(0),
    PmTiles.Header.RootLength(0),
    PmTiles.Header.MetadataOffset(0),
    PmTiles.Header.MetadataLength(0),
    PmTiles.Header.LeafOffset(0),
    PmTiles.Header.LeafLength(0),
    PmTiles.Header.DataOffset(0),
    PmTiles.Header.DataLength(0),
    PmTiles.Header.AddressedTiles(0),
    PmTiles.Header.TileEntries(0),
    PmTiles.Header.TileContents(0),
    PmTiles.Header.Clustered(true),
    PmTiles.Compression.Gzip,
    PmTiles.Compression.None,
    PmTiles.TileType.Png,
    PmTiles.Header.MinZoom(0),
    maxZoom,
    PmTiles.Header.MinLon(20),
    PmTiles.Header.MinLat(34),
    PmTiles.Header.MaxLon(30),
    PmTiles.Header.MaxLat(42),
    PmTiles.Header.CenterZoom(3),
    PmTiles.Header.CenterLon(25),
    PmTiles.Header.CenterLat(38)
  )

  test("written and read back: tiles, runs of identical tiles, metadata"):
    val dir = Files.createTempDirectory("pmtiles")
    val out = dir.resolve("t.pmtiles")
    val w = BasemapFixtures.writer(out)
    val tiles = for z <- 0 to 3; x <- 0L until (1L << z); y <- 0L until (1L << z)
    yield (
      PmTiles.tileId(PmTiles.Zoom(z), PmTiles.TileColumn(x), PmTiles.TileRow(y)),
      PmTiles.TileBytes(if z == 3 && x > 3 then Array[Byte](9, 9) else s"$z/$x/$y".getBytes)
    )
    tiles.sortBy[Long](_._1.value).foreach((id, b) => w.add(id, b))
    w.finish(template(PmTiles.Zoom(3)), Json.obj("name" -> Json.fromString("test")))
    val r = BasemapFixtures.open(out).fold(e => fail(e.sentence), identity)
    try
      assertEquals(r.header.maxZoom, 3)
      assertEquals(r.header.addressedTiles, 85L)
      assert(r.header.tileContents.value < 85L, "the identical tiles are stored once")
      assertEquals(r.metadata.hcursor.get[String]("name"), Right("test"))
      assertEquals(
        r.tile(PmTiles.Zoom(2), PmTiles.TileColumn(1), PmTiles.TileRow(3)).map(_.map(b => String(b.value))),
        Right(Some("2/1/3"))
      )
      assertEquals(
        r.tile(PmTiles.Zoom(3), PmTiles.TileColumn(5), PmTiles.TileRow(0)).map(_.map(_.value.toList)),
        Right(Some(List[Byte](9, 9)))
      )
      assertEquals(r.tile(PmTiles.Zoom(4), PmTiles.TileColumn(0), PmTiles.TileRow(0)), Right(None))
      assert(!Files.exists(dir.resolve("t.pmtiles.data")))
    finally r.close()

  test("many entries go to leaf directories, and are still found"):
    val dir = Files.createTempDirectory("pmtiles")
    val out = dir.resolve("big.pmtiles")
    val w = BasemapFixtures.writer(out)
    // irregular gaps and lengths: 40 000 entries that do not compress into one root
    val rnd = scala.util.Random(7)
    val ids = Iterator.iterate(0L)(_ + 2 + rnd.nextInt(5)).take(40000).map(PmTiles.Entry.TileId(_)).toVector
    ids.foreach(id =>
      w.add(id, PmTiles.TileBytes(java.nio.ByteBuffer.allocate(8 + rnd.nextInt(200)).putLong(id.value).array()))
    )
    w.finish(template(PmTiles.Zoom(9)), Json.obj())
    val r = BasemapFixtures.open(out).fold(e => fail(e.sentence), identity)
    try
      assert(r.header.leafLength.value > 0, "leaves were needed")
      List(ids(0), ids(1), ids(20000), ids.last).foreach { id =>
        val (z, x, y) = PmTiles.zxy(id)
        assertEquals(r.tile(z, x, y).map(_.map(b => java.nio.ByteBuffer.wrap(b.value).getLong)), Right(Some(id)))
      }
      val (z, x, y) = PmTiles.zxy(PmTiles.Entry.TileId(ids(5).value + 1))
      assertEquals(r.tile(z, x, y), Right(None))
    finally r.close()

  test("a read interrupted mid-way (a cancelled tile request) does not break the archive for the other requests"):
    val dir = java.nio.file.Files.createTempDirectory("pmtiles-int")
    val p = BasemapFixtures.vectorArchive(dir.resolve("v.pmtiles"), maxZoom = PmTiles.Zoom(3))
    val f = BasemapFixtures.open(p).fold(e => fail(e.sentence), identity)
    try
      val before =
        f.tile(PmTiles.Zoom(2), PmTiles.TileColumn(1), PmTiles.TileRow(1)).fold(e => fail(e.sentence), identity).get
      // an interrupted thread reading: the JDK closes the shared channel
      val t = Thread.ofVirtual().unstarted { () =>
        Thread.currentThread().interrupt();
        scala.util.Try(f.tile(PmTiles.Zoom(3), PmTiles.TileColumn(2), PmTiles.TileRow(2)))
      }
      t.start(); t.join()
      assertEquals(
        f.tile(PmTiles.Zoom(2), PmTiles.TileColumn(1), PmTiles.TileRow(1)).map(_.map(_.value.toList)),
        Right(Some(before.value.toList)),
        "read again on a reopened channel"
      )
      assert(f.size.value > 0)
    finally f.close()
    intercept[java.nio.channels.ClosedChannelException](
      f.tile(PmTiles.Zoom(2), PmTiles.TileColumn(1), PmTiles.TileRow(1))
    )
