package com.histmersion.util.basemap

import com.histmersion.testsupport.ComparedByValue.given

import io.circe.Json

import java.nio.file.Files

/** The PMTiles codec, reader and writer (pure, no network, no database). */
class PmTilesSuite extends munit.FunSuite:

  test("tile ids follow the spec's Hilbert numbering (its own examples)"):
    assertEquals(PmTiles.tileId(com.histmersion.util.basemap.PmTiles.Zoom(0), com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(0)), 0L)
    assertEquals(PmTiles.tileId(com.histmersion.util.basemap.PmTiles.Zoom(1), com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(0)), 1L)
    assertEquals(PmTiles.tileId(com.histmersion.util.basemap.PmTiles.Zoom(1), com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(1)), 2L)
    assertEquals(PmTiles.tileId(com.histmersion.util.basemap.PmTiles.Zoom(1), com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(1)), 3L)
    assertEquals(PmTiles.tileId(com.histmersion.util.basemap.PmTiles.Zoom(1), com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(0)), 4L)
    assertEquals(PmTiles.tileId(com.histmersion.util.basemap.PmTiles.Zoom(2), com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(0)), 5L)
    assertEquals(PmTiles.zoomBase(com.histmersion.util.basemap.PmTiles.Zoom(12)), 5592405L)
    // go-pmtiles: ZxyToID(3, 3, 3) = 31… check by the inverse instead, over whole zooms
    for z <- 0 to 6; x <- 0L until (1L << z); y <- 0L until (1L << z) do
      assertEquals(PmTiles.zxy(PmTiles.tileId(z, x, y)), (z, x, y))

  test("ids at one zoom are a permutation of its range"):
    val z = 4
    val ids = for x <- 0L until 16L; y <- 0L until 16L yield PmTiles.tileId(z, x, y)
    assertEquals(ids.sorted, (PmTiles.zoomBase(com.histmersion.util.basemap.PmTiles.Zoom(4)) until PmTiles.zoomBase(com.histmersion.util.basemap.PmTiles.Zoom(5))).toVector)

  test("the header round-trips"):
    val h = PmTiles.Header(
      com.histmersion.util.basemap.PmTiles.Header.RootOffset(127),
      com.histmersion.util.basemap.PmTiles.Header.RootLength(300),
      com.histmersion.util.basemap.PmTiles.Header.MetadataOffset(427),
      com.histmersion.util.basemap.PmTiles.Header.MetadataLength(50),
      com.histmersion.util.basemap.PmTiles.Header.DataOffset(477),
      com.histmersion.util.basemap.PmTiles.Header.LeafLength(0),
      com.histmersion.util.basemap.PmTiles.Header.DataOffset(477),
      com.histmersion.util.basemap.PmTiles.Header.DataLength(9999),
      com.histmersion.util.basemap.PmTiles.Header.AddressedTiles(10),
      com.histmersion.util.basemap.PmTiles.Header.TileEntries(8),
      com.histmersion.util.basemap.PmTiles.Header.TileContents(7),
      com.histmersion.util.basemap.PmTiles.Header.Clustered(true),
      PmTiles.Compression.Gzip,
      PmTiles.Compression.Gzip,
      PmTiles.TileType.Mvt,
      com.histmersion.util.basemap.PmTiles.Header.MinZoom(0),
      com.histmersion.util.basemap.PmTiles.Header.MaxZoom(10),
      -12.5,
      20.25,
      90.0,
      62.0,
      com.histmersion.util.basemap.PmTiles.Header.CenterZoom(5),
      30.0,
      38.0
    )
    val b = PmTiles.encodeHeader(h)
    assertEquals(b.length, 127)
    assertEquals(PmTiles.parseHeader(b), Right(h))
    assert(PmTiles.parseHeader(Array.fill(127)(0.toByte)).isLeft)

  test("a directory round-trips, contiguous offsets stored as 0"):
    val es = Vector(
      PmTiles.Entry(com.histmersion.util.basemap.PmTiles.Entry.TileId(0), com.histmersion.util.basemap.PmTiles.Entry.Offset(0), com.histmersion.util.basemap.PmTiles.Entry.Length(10), com.histmersion.util.basemap.PmTiles.Entry.RunLength(1)),
      PmTiles.Entry(com.histmersion.util.basemap.PmTiles.Entry.TileId(1), com.histmersion.util.basemap.PmTiles.Entry.Offset(10), com.histmersion.util.basemap.PmTiles.Entry.Length(20), com.histmersion.util.basemap.PmTiles.Entry.RunLength(3)),
      PmTiles.Entry(com.histmersion.util.basemap.PmTiles.Entry.TileId(9), com.histmersion.util.basemap.PmTiles.Entry.Offset(0), com.histmersion.util.basemap.PmTiles.Entry.Length(10), com.histmersion.util.basemap.PmTiles.Entry.RunLength(1)),
      PmTiles.Entry(com.histmersion.util.basemap.PmTiles.Entry.TileId(20), com.histmersion.util.basemap.PmTiles.Entry.Offset(500), com.histmersion.util.basemap.PmTiles.Entry.Length(7), com.histmersion.util.basemap.PmTiles.Entry.RunLength(0))
    )
    assertEquals(PmTiles.decodeDirectory(PmTiles.encodeDirectory(es)), Right(es))
    assert(PmTiles.decodeDirectory(Array(5.toByte)).isLeft)

  test("find: a run covers its ids, a leaf is returned to look further"):
    val es = Vector(PmTiles.Entry(com.histmersion.util.basemap.PmTiles.Entry.TileId(1), com.histmersion.util.basemap.PmTiles.Entry.Offset(0), com.histmersion.util.basemap.PmTiles.Entry.Length(10), com.histmersion.util.basemap.PmTiles.Entry.RunLength(3)), PmTiles.Entry(com.histmersion.util.basemap.PmTiles.Entry.TileId(10), com.histmersion.util.basemap.PmTiles.Entry.Offset(10), com.histmersion.util.basemap.PmTiles.Entry.Length(5), com.histmersion.util.basemap.PmTiles.Entry.RunLength(0)))
    assertEquals(PmTiles.find(es, com.histmersion.util.basemap.PmTiles.Entry.TileId(0)), None)
    assertEquals(PmTiles.find(es, com.histmersion.util.basemap.PmTiles.Entry.TileId(3)).map(_.offset), Some(0L))
    assertEquals(PmTiles.find(es, com.histmersion.util.basemap.PmTiles.Entry.TileId(4)), None)
    assertEquals(PmTiles.find(es, com.histmersion.util.basemap.PmTiles.Entry.TileId(99)).map(_.isLeaf), Some(true))

  private def template(maxZoom: Int) = PmTiles.Header(
    com.histmersion.util.basemap.PmTiles.Header.MetadataOffset(0),
    com.histmersion.util.basemap.PmTiles.Header.MetadataLength(0),
    com.histmersion.util.basemap.PmTiles.Header.LeafOffset(0),
    com.histmersion.util.basemap.PmTiles.Header.LeafLength(0),
    com.histmersion.util.basemap.PmTiles.Header.DataOffset(0),
    com.histmersion.util.basemap.PmTiles.Header.DataLength(0),
    com.histmersion.util.basemap.PmTiles.Header.AddressedTiles(0),
    com.histmersion.util.basemap.PmTiles.Header.TileEntries(0),
    com.histmersion.util.basemap.PmTiles.Header.TileContents(0),
    com.histmersion.util.basemap.PmTiles.Header.TileEntries(0),
    com.histmersion.util.basemap.PmTiles.Header.TileContents(0),
    com.histmersion.util.basemap.PmTiles.Header.Clustered(true),
    PmTiles.Compression.Gzip,
    PmTiles.Compression.None,
    PmTiles.TileType.Png,
    com.histmersion.util.basemap.PmTiles.Header.MinZoom(0),
    maxZoom,
    com.histmersion.util.basemap.PmTiles.Header.MinLon(20),
    com.histmersion.util.basemap.PmTiles.Header.MinLat(34),
    com.histmersion.util.basemap.PmTiles.Header.CenterZoom(3)0,
    42,
    3,
    25,
    38
  )

  test("written and read back: tiles, runs of identical tiles, metadata"):
    val dir = Files.createTempDirectory("pmtiles")
    val out = dir.resolve("t.pmtiles")
    val w = BasemapFixtures.writer(out)
    val tiles = for z <- 0 to 3; x <- 0L until (1L << z); y <- 0L until (1L << z)
    yield (PmTiles.tileId(z, x, y), if z == 3 && x > 3 then Array[Byte](9, 9) else s"$z/$x/$y".getBytes)
    tiles.sortBy(_._1).foreach((id, b) => w.add(id, b))
    w.finish(template(3), Json.obj("name" -> Json.fromString("test")))
    val r = BasemapFixtures.open(out).fold(fail(_), identity)
    try
      assertEquals(r.header.maxZoom, 3)
      assertEquals(r.header.addressedTiles, 85L)
      assert(r.header.tileContents < 85L, "the identical tiles are stored once")
      assertEquals(r.metadata.hcursor.get[String]("name"), Right("test"))
      assertEquals(r.tile(com.histmersion.util.basemap.PmTiles.Zoom(2), com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(3)).map(_.map(String(_))), Right(Some("2/1/3")))
      assertEquals(r.tile(com.histmersion.util.basemap.PmTiles.Zoom(3), com.histmersion.util.basemap.PmTiles.TileColumn(5), com.histmersion.util.basemap.PmTiles.TileRow(0)).map(_.map(_.toList)), Right(Some(List[Byte](9, 9))))
      assertEquals(r.tile(com.histmersion.util.basemap.PmTiles.Zoom(4), com.histmersion.util.basemap.PmTiles.TileColumn(0), com.histmersion.util.basemap.PmTiles.TileRow(0)), Right(None))
      assert(!Files.exists(dir.resolve("t.pmtiles.data")))
    finally r.close()

  test("many entries go to leaf directories, and are still found"):
    val dir = Files.createTempDirectory("pmtiles")
    val out = dir.resolve("big.pmtiles")
    val w = BasemapFixtures.writer(out)
    // irregular gaps and lengths: 40 000 entries that do not compress into one root
    val rnd = scala.util.Random(7)
    val ids = Iterator.iterate(0L)(_ + 2 + rnd.nextInt(5)).take(40000).toVector
    ids.foreach(id => w.add(id, java.nio.ByteBuffer.allocate(8 + rnd.nextInt(200)).putLong(id).array()))
    w.finish(template(9), Json.obj())
    val r = BasemapFixtures.open(out).fold(fail(_), identity)
    try
      assert(r.header.leafLength > 0, "leaves were needed")
      List(ids(0), ids(1), ids(20000), ids.last).foreach { id =>
        val (z, x, y) = PmTiles.zxy(id)
        assertEquals(r.tile(z, x, y).map(_.map(java.nio.ByteBuffer.wrap(_).getLong)), Right(Some(id)))
      }
      val (z, x, y) = PmTiles.zxy(ids(5) + 1)
      assertEquals(r.tile(z, x, y), Right(None))
    finally r.close()

  test("a read interrupted mid-way (a cancelled tile request) does not break the archive for the other requests"):
    val dir = java.nio.file.Files.createTempDirectory("pmtiles-int")
    val p = BasemapFixtures.vectorArchive(dir.resolve("v.pmtiles"), maxZoom = 3)
    val f = BasemapFixtures.open(p).fold(fail(_), identity)
    try
      val before = f.tile(com.histmersion.util.basemap.PmTiles.Zoom(2), com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(1)).fold(fail(_), identity).get
      // an interrupted thread reading: the JDK closes the shared channel
      val t = Thread.ofVirtual().unstarted { () => Thread.currentThread().interrupt(); scala.util.Try(f.tile(com.histmersion.util.basemap.PmTiles.Zoom(3), com.histmersion.util.basemap.PmTiles.TileColumn(2), com.histmersion.util.basemap.PmTiles.TileRow(2))) }
      t.start(); t.join()
      assertEquals(f.tile(com.histmersion.util.basemap.PmTiles.Zoom(2), com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(1)).map(_.map(_.toList)), Right(Some(before.toList)), "read again on a reopened channel")
      assert(f.size > 0)
    finally f.close()
    intercept[java.nio.channels.ClosedChannelException](f.tile(com.histmersion.util.basemap.PmTiles.Zoom(2), com.histmersion.util.basemap.PmTiles.TileColumn(1), com.histmersion.util.basemap.PmTiles.TileRow(1)))
