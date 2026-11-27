package com.histmersion
package util.basemap

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.lint.{tolerated, LintRule}

/** Terrarium PNGs: what the encoder writes is a PNG any reader takes, with the elevation in its pixels, and the decoder
  * reads it back.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class TerrariumPngSuite extends munit.FunSuite:

  private val size = TerrariumPng.TileSize(16)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val side = size.value
  private val metres = Array.tabulate(side * side)(i => (i % side) * 37.5f - (i / side) * 600.25f)

  test("encoded: a PNG an image reader takes, each pixel the elevation in Terrarium's red, green and blue"):
    val png = TerrariumPng.encode(metres, size)
    val img = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(png.value))
    assertEquals((img.getWidth, img.getHeight), (size, size))
    for (x, y) <- List((0, 0), (5, 3), (15, 15)) do
      val rgb = img.getRGB(x, y)
      val v = ((rgb >> 16) & 0xff) * 256 + ((rgb >> 8) & 0xff) + (rgb & 0xff) / 256.0 - 32768
      assertEqualsDouble(v, metres(y * side + x).toDouble, 1.0 / 256, s"($x, $y)")

  test("decoded back to within 1/256 m; not a PNG, or another kind of PNG, refused"):
    val (back, w) = TerrariumPng.decode(TerrariumPng.encode(metres, size)).fold(e => fail(e.sentence), identity)
    assertEquals(w, size)
    metres.indices.foreach(i => assertEqualsDouble(back(i).toDouble, metres(i).toDouble, 1.0 / 256))
    assert(TerrariumPng.decode(Png.PngBytes("PNG0/0/0".getBytes)).isLeft)
    val grey = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(
      java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_BYTE_GRAY),
      "png",
      grey
    )
    assert(TerrariumPng.decode(Png.PngBytes(grey.toByteArray)).left.exists(_.sentence.contains("colour type 0")))

  test("decoded: every PNG row filter (an image writer's RGB tile)"):
    val img = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for x <- 0 until 64; y <- 0 until 64 do img.setRGB(x, y, (x * 3 << 16) | (y * 4 << 8) | ((x * y) & 0xff))
    val out = java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(img, "png", out)
    val (m, w) = TerrariumPng.decode(Png.PngBytes(out.toByteArray)).fold(e => fail(e.sentence), identity)
    assertEquals(w, 64)
    for (x, y) <- List((0, 0), (10, 20), (63, 63)) do
      assertEqualsDouble(m(y * 64 + x).toDouble, (x * 3) * 256 + y * 4 + ((x * y) & 0xff) / 256.0 - 32768, 1e-3)
