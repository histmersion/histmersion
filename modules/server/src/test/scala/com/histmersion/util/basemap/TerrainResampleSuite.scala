package com.histmersion.util.basemap

import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** The lossless-WebP decoder against `libwebp`'s own (`dwebp`: the digests below are of its output for the same files),
  * the terrarium resampling, and the PNG round trip.
  */
class TerrainResampleSuite extends munit.FunSuite:

  private def resource(name: String): Array[Byte] =
    val in = getClass.getResourceAsStream(s"/basemap/webp/$name")
    assert(in != null, name)
    try in.readAllBytes()
    finally in.close()

  /** The SHA-256 of an image's RGB bytes (what `dwebp -ppm` writes). */
  private def rgbDigest(img: Vp8l.Image): String =
    val b = new Array[Byte](img.pixels.length * 3)
    img.pixels.indices.foreach { i =>
      val p = img.pixels(i)
      b(3 * i) = (p >> 16).toByte; b(3 * i + 1) = (p >> 8).toByte; b(3 * i + 2) = p.toByte
    }
    MessageDigest.getInstance("SHA-256").digest(b).map(x => f"$x%02x").mkString

  // the digests of `dwebp -ppm` (libwebp 1.6) for the same files, and the files' sizes
  private val expected = List(
    ("terr-z0.webp", 161, 117, "997a7871d45c3a37a72ef72dca4ca3178c3e32d3883b9832e0fd6312baa9ef24"),
    ("terr-z6.webp", 161, 117, "997a7871d45c3a37a72ef72dca4ca3178c3e32d3883b9832e0fd6312baa9ef24"),
    ("terr-z9.webp", 161, 117, "997a7871d45c3a37a72ef72dca4ca3178c3e32d3883b9832e0fd6312baa9ef24"),
    ("noise.webp", 61, 45, "d2c1bd5ca0c6101baccf7e125e30e06f8123b7af5a4283f2399d11e82dce3de8"),
    ("noise-z1.webp", 61, 45, "d2c1bd5ca0c6101baccf7e125e30e06f8123b7af5a4283f2399d11e82dce3de8"),
    ("pal12.webp", 50, 33, "03fad86d3ed929e7391eb88c113646cc6a0c7bba113e3df834cdcc7cf34366c4"),
    ("palette.webp", 37, 23, "9048d2ab3ebd65ec3172db5a77b2ee8c4315c6cd45d44c5fc3fd4daab9a37d06")
  )

  expected.foreach { (name, w, h, digest) =>
    test(s"a lossless WebP decodes as libwebp decodes it: $name") {
      val img = Vp8l.decode(resource(name)).fold(e => fail(e), identity)
      assertEquals((img.width, img.height), (w, h))
      assertEquals(rgbDigest(img), digest)
      assert(img.pixels.forall(p => (p >>> 24) == 0xff), "opaque")
    }
  }

  /** The browser fixture's relief (Mapterhorn's tiles, as `histmersion basemap fetch` stores them). */
  private def fixture: Option[Path] =
    Iterator
      .iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve("modules/web/test/fixtures/basemap/terrain.pmtiles"))
      .find(Files.isRegularFile(_))

  test("the fixture's Mapterhorn relief tiles decode as libwebp decodes them") {
    val f = fixture.getOrElse(fail("the browser fixture is not in the repository"))
    val pm = PmTilesFile.open(f).fold(e => fail(e), identity)
    try
      val zero = pm.tile(0, 0, 0).fold(e => fail(e), identity).getOrElse(fail("no tile 0/0/0"))
      assertEquals(
        rgbDigest(Vp8l.decode(zero).fold(e => fail(e), identity)),
        "aaf149389ba79323fb5bfe65cc6c4fd4b9f39ec6a2c6cddb2ae3a9d6e6019a72"
      )
      // tile 4805 of the archive's order: a zoom-6 tile of the Aegean
      val (x, y) = (for x <- 0L until 64L; y <- 0L until 64L if PmTiles.tileId(6, x, y) == 4805L yield (x, y)).head
      val six = pm.tile(6, x, y).fold(e => fail(e), identity).getOrElse(fail(s"no tile 6/$x/$y"))
      val img = Vp8l.decode(six).fold(e => fail(e), identity)
      assertEquals((img.width, img.height), (512, 512))
      assertEquals(rgbDigest(img), "86c85c084ed47555729aa200be04a4e436f59151824b816128e873d41e07ff58")
      // terrarium: heights on Earth
      val hs = img.pixels.map(TerrainResample.metres)
      assert(hs.min > -11000 && hs.max < 9000, s"${hs.min} … ${hs.max}")
    finally pm.close()
  }

  test("what is not a lossless WebP is refused with the reason") {
    assert(Vp8l.decode("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes("ISO-8859-1") ++ new Array[Byte](12)).isLeft)
    assertEquals(Vp8l.decode("hello".getBytes).left.map(_.take(10)), Left("not a WebP"))
    // a truncated stream: an error, never an exception
    val t = resource("terr-z6.webp")
    assert(Vp8l.decode(t.take(t.length / 2)).isLeft)
  }

  test("terrarium encodes and decodes metres, to 1/256 m") {
    for m <- List(-10994.0, -0.5, 0.0, 1.25, 431.7, 8848.86) do
      assertEqualsDouble(TerrainResample.metres(TerrainResample.encode(m)), m, 1.0 / 256)
    assertEquals(TerrainResample.encode(0) & 0xffffff, 0x800000)
  }

  test("a deeper tile is its ancestor's quarter resampled in metres, continuous where the high byte rolls over") {
    // a 4 × 4 ancestor: a ramp of heights across the 256 m step of terrarium's red byte
    val n = 4
    val src = Array.tabulate(n * n)(i => TerrainResample.encode(-2 + 1.5 * (i % n) + 0.25 * (i / n)))
    val out = TerrainResample.resample(src, n, 1, 1, 0) // the north-east quarter, one zoom deeper
    val m = out.map(TerrainResample.metres)
    // monotone along a row, inside the source's range, nothing near ±256 m (a byte-wise blend's error)
    for j <- 0 until n do
      val row = m.slice(j * n, j * n + n)
      assert(row.sliding(2).forall(p => p(1) >= p(0) - 1e-6), row.mkString(" "))
    assert(m.forall(v => v > -3 && v < 5), m.mkString(" "))
    // a flat ancestor stays flat at every depth
    val flat = Array.fill(n * n)(TerrainResample.encode(123.5))
    for dz <- 1 to 5 do
      assert(
        TerrainResample.resample(flat, n, dz, 3, 7).forall(p => math.abs(TerrainResample.metres(p) - 123.5) < 1.0 / 256)
      )
  }

  test("PNG: what is encoded decodes to the same pixels") {
    val w = 33
    val h = 17
    val px = Array.tabulate(w * h)(i => 0xff000000 | ((i * 7919) & 0xffffff))
    val png = Png.encodeRgb(w, h, px)
    assert(Png.isPng(png))
    val back = Png.decode(png).fold(e => fail(e), identity)
    assertEquals((back.width, back.height), (w, h))
    assert(back.pixels.sameElements(px))
    // the relief: a resampled tile decodes with either decoder
    assert(TerrainResample.decode(png).isRight)
    assert(TerrainResample.decode(resource("noise.webp")).isRight)
  }
