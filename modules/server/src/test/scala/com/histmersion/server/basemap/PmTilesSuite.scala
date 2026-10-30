package com.histmersion.server.basemap

import io.circe.Json

import java.nio.file.Files

/** The PMTiles codec, reader and writer (pure, no network, no database). */
class PmTilesSuite extends munit.FunSuite:

  test("tile ids follow the spec's Hilbert numbering (its own examples)"):
    assertEquals(PmTiles.tileId(0, 0, 0), 0L)
    assertEquals(PmTiles.tileId(1, 0, 0), 1L)
    assertEquals(PmTiles.tileId(1, 0, 1), 2L)
    assertEquals(PmTiles.tileId(1, 1, 1), 3L)
    assertEquals(PmTiles.tileId(1, 1, 0), 4L)
    assertEquals(PmTiles.tileId(2, 0, 0), 5L)
    assertEquals(PmTiles.zoomBase(12), 5592405L)
    // go-pmtiles: ZxyToID(3, 3, 3) = 31… check by the inverse instead, over whole zooms
    for z <- 0 to 6; x <- 0L until (1L << z); y <- 0L until (1L << z) do
      assertEquals(PmTiles.zxy(PmTiles.tileId(z, x, y)), (z, x, y))

  test("ids at one zoom are a permutation of its range"):
    val z = 4
    val ids = for x <- 0L until 16L; y <- 0L until 16L yield PmTiles.tileId(z, x, y)
    assertEquals(ids.sorted, (PmTiles.zoomBase(4) until PmTiles.zoomBase(5)).toVector)

  test("the header round-trips"):
    val h = PmTiles.Header(
      127,
      300,
      427,
      50,
      477,
      0,
      477,
      9999,
      10,
      8,
      7,
      true,
      PmTiles.Compression.Gzip,
      PmTiles.Compression.Gzip,
      PmTiles.TileType.Mvt,
      0,
      10,
      -12.5,
      20.25,
      90.0,
      62.0,
      5,
      30.0,
      38.0
    )
    val b = PmTiles.encodeHeader(h)
    assertEquals(b.length, 127)
    assertEquals(PmTiles.parseHeader(b), Right(h))
    assert(PmTiles.parseHeader(Array.fill(127)(0.toByte)).isLeft)

  test("a directory round-trips, contiguous offsets stored as 0"):
    val es = Vector(
      PmTiles.Entry(0, 0, 10, 1),
      PmTiles.Entry(1, 10, 20, 3),
      PmTiles.Entry(9, 0, 10, 1),
      PmTiles.Entry(20, 500, 7, 0)
    )
    assertEquals(PmTiles.decodeDirectory(PmTiles.encodeDirectory(es)), Right(es))
    assert(PmTiles.decodeDirectory(Array(5.toByte)).isLeft)

  test("find: a run covers its ids, a leaf is returned to look further"):
    val es = Vector(PmTiles.Entry(1, 0, 10, 3), PmTiles.Entry(10, 10, 5, 0))
    assertEquals(PmTiles.find(es, 0), None)
    assertEquals(PmTiles.find(es, 3).map(_.offset), Some(0L))
    assertEquals(PmTiles.find(es, 4), None)
    assertEquals(PmTiles.find(es, 99).map(_.isLeaf), Some(true))

  private def template(maxZoom: Int) = PmTiles.Header(
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    true,
    PmTiles.Compression.Gzip,
    PmTiles.Compression.None,
    PmTiles.TileType.Png,
    0,
    maxZoom,
    20,
    34,
    30,
    42,
    3,
    25,
    38
  )

  test("written and read back: tiles, runs of identical tiles, metadata"):
    val dir = Files.createTempDirectory("pmtiles")
    val out = dir.resolve("t.pmtiles")
    val w = PmTilesWriter(out)
    val tiles = for z <- 0 to 3; x <- 0L until (1L << z); y <- 0L until (1L << z)
    yield (PmTiles.tileId(z, x, y), if z == 3 && x > 3 then Array[Byte](9, 9) else s"$z/$x/$y".getBytes)
    tiles.sortBy(_._1).foreach((id, b) => w.add(id, b))
    w.finish(template(3), Json.obj("name" -> Json.fromString("test")))
    val r = PmTilesFile.open(out).fold(fail(_), identity)
    try
      assertEquals(r.header.maxZoom, 3)
      assertEquals(r.header.addressedTiles, 85L)
      assert(r.header.tileContents < 85L, "the identical tiles are stored once")
      assertEquals(r.metadata.hcursor.get[String]("name"), Right("test"))
      assertEquals(r.tile(2, 1, 3).map(_.map(String(_))), Right(Some("2/1/3")))
      assertEquals(r.tile(3, 5, 0).map(_.map(_.toList)), Right(Some(List[Byte](9, 9))))
      assertEquals(r.tile(4, 0, 0), Right(None))
      assert(!Files.exists(dir.resolve("t.pmtiles.data")))
    finally r.close()

  test("many entries go to leaf directories, and are still found"):
    val dir = Files.createTempDirectory("pmtiles")
    val out = dir.resolve("big.pmtiles")
    val w = PmTilesWriter(out)
    // irregular gaps and lengths: 40 000 entries that do not compress into one root
    val rnd = scala.util.Random(7)
    val ids = Iterator.iterate(0L)(_ + 2 + rnd.nextInt(5)).take(40000).toVector
    ids.foreach(id => w.add(id, java.nio.ByteBuffer.allocate(8 + rnd.nextInt(200)).putLong(id).array()))
    w.finish(template(9), Json.obj())
    val r = PmTilesFile.open(out).fold(fail(_), identity)
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
    val f = PmTilesFile.open(p).fold(fail(_), identity)
    try
      val before = f.tile(2, 1, 1).fold(fail(_), identity).get
      // an interrupted thread reading: the JDK closes the shared channel
      val t = Thread.ofVirtual().unstarted { () => Thread.currentThread().interrupt(); scala.util.Try(f.tile(3, 2, 2)) }
      t.start(); t.join()
      assertEquals(f.tile(2, 1, 1).map(_.map(_.toList)), Right(Some(before.toList)), "read again on a reopened channel")
      assert(f.size > 0)
    finally f.close()
    intercept[java.nio.channels.ClosedChannelException](f.tile(2, 1, 1))
