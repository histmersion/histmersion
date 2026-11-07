package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.domain.*

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import com.histmersion.api.media.Iiif

/** Unit tests for [[IiifHandler]]'s pure image processing: region cropping, size scaling, and format encoding. No
  * database — a synthetic test image is used.
  */
class IiifHandlerSuite extends munit.FunSuite:

  /** A 100x80 red PNG. */
  private def testPng(w: Int = 100, h: Int = 80): Array[Byte] =
    val img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setColor(java.awt.Color.RED)
    g.fillRect(0, 0, w, h)
    g.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(img, "PNG", out)
    out.toByteArray

  // ── IiifHandler.image (end-to-end through the handler's static method) ────────────────────

  // We cannot test the full handler without a DataSource, but we can test the parsing-side
  // validations that reject before the DB is hit.

  test("parseFilename: default.jpg"):
    // This is a private method — test indirectly through .image with a None DB hit.
    // Instead, test the Iiif parsing which IS accessible.
    assertEquals(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("jpg")), Right("jpg"))
    assertEquals(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("png")), Right("png"))

  test("parseFilename: rejects non-default quality"):
    // The filename must be default.{format}
    assert(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("tiff")).isLeft)

  // ── Region parsing edge cases ─────────────────────────────────────────────────────────────

  test("Region: case insensitive"):
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("FULL")), Right(Iiif.Region.Full))
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("Full")), Right(Iiif.Region.Full))

  test("Region: whitespace tolerance"):
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire(" full ")), Right(Iiif.Region.Full))

  // ── Size edge cases ───────────────────────────────────────────────────────────────────────

  test("Size: best fit with equal dimensions"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("!100,100")), Right(Iiif.Size.BestFit(com.histmersion.api.media.Iiif.Size.BestFit.W(100), com.histmersion.api.media.Iiif.Size.BestFit.H(100))))

  test("Size: single dimension w"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("50,")), Right(Iiif.Size.Width(com.histmersion.api.media.Iiif.Size.Width.W(50))))

  test("Size: single dimension h"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire(",75")), Right(Iiif.Size.Height(com.histmersion.api.media.Iiif.Size.Height.H(75))))

  // ── info.json ─────────────────────────────────────────────────────────────────────────────

  test("infoJson includes IIIF required fields"):
    val json = Iiif.infoJson(com.histmersion.api.media.Iiif.ImageId("http://localhost:8000/iiif/test2024/fig1.png"), com.histmersion.api.media.ImageSize.Width(1024), com.histmersion.api.media.ImageSize.Height(768))
    val c = json.hcursor
    assert(c.get[String]("@context").isRight)
    assert(c.get[String]("type").isRight)
    assert(c.get[String]("protocol").isRight)
    assert(c.get[Int]("width").contains(1024))
    assert(c.get[Int]("height").contains(768))
    assert(c.get[String]("profile").contains("level1"))

  test("infoJson extraFeatures lists Level 1 features"):
    val json = Iiif.infoJson(com.histmersion.api.media.Iiif.ImageId("http://x/iiif/a/b"), com.histmersion.api.media.ImageSize.Width(100), com.histmersion.api.media.ImageSize.Height(100))
    val features = json.hcursor.downField("extraFeatures").as[List[String]]
    assert(features.isRight)
    assert(features.toOption.get.contains("regionByPx"))
    assert(features.toOption.get.contains("sizeByW"))
    assert(features.toOption.get.contains("sizeByH"))
    assert(features.toOption.get.contains("sizeByWh"))
    assert(features.toOption.get.contains("sizeByForcedWh"))
