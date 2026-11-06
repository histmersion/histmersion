package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

/** Pure tests for the IIIF Image API parameter parsing (plan §4.3). No database, no HTTP — only the [[Iiif]] object's
  * parse/format logic.
  */

import com.histmersion.api.media.Iiif
class IiifSuite extends munit.FunSuite:

  // ── Region parsing ────────────────────────────────────────────────────────────────────────────

  test("Region.parse: full"):
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("full")), Right(Iiif.Region.Full))

  test("Region.parse: square maps to full (Level 1 fallback)"):
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("square")), Right(Iiif.Region.Full))

  test("Region.parse: pixel coordinates"):
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("10,20,100,200")), Right(Iiif.Region.Pixels(com.histmersion.api.media.Iiif.Region.Pixels.X(10), com.histmersion.api.media.Iiif.Region.Pixels.Y(20), com.histmersion.api.media.Iiif.Region.Pixels.W(100), com.histmersion.api.media.Iiif.Region.Pixels.H(200))))

  test("Region.parse: zero origin"):
    assertEquals(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("0,0,50,50")), Right(Iiif.Region.Pixels(com.histmersion.api.media.Iiif.Region.Pixels.X(0), com.histmersion.api.media.Iiif.Region.Pixels.Y(0), com.histmersion.api.media.Iiif.Region.Pixels.W(50), com.histmersion.api.media.Iiif.Region.Pixels.H(50))))

  test("Region.parse: rejects zero width"):
    assert(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("0,0,0,50")).isLeft)

  test("Region.parse: rejects zero height"):
    assert(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("0,0,50,0")).isLeft)

  test("Region.parse: rejects negative width"):
    assert(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("0,0,-10,50")).isLeft)

  test("Region.parse: rejects garbage"):
    assert(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("foo")).isLeft)

  test("Region.parse: rejects too few coords"):
    assert(Iiif.Region.parse(com.histmersion.api.media.Iiif.Region.Wire("10,20,100")).isLeft)

  // ── Size parsing ──────────────────────────────────────────────────────────────────────────────

  test("Size.parse: max"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("max")), Right(Iiif.Size.Max))

  test("Size.parse: ^max"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("^max")), Right(Iiif.Size.ScaleUp))

  test("Size.parse: width"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("200,")), Right(Iiif.Size.Width(com.histmersion.api.media.Iiif.Size.Width.W(200))))

  test("Size.parse: height"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire(",150")), Right(Iiif.Size.Height(com.histmersion.api.media.Iiif.Size.Height.H(150))))

  test("Size.parse: exact"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("300,200")), Right(Iiif.Size.Exact(com.histmersion.api.media.Iiif.Size.Exact.W(300), com.histmersion.api.media.Iiif.Size.Exact.H(200))))

  test("Size.parse: best fit"):
    assertEquals(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("!300,200")), Right(Iiif.Size.BestFit(com.histmersion.api.media.Iiif.Size.BestFit.W(300), com.histmersion.api.media.Iiif.Size.BestFit.H(200))))

  test("Size.parse: rejects zero width"):
    assert(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("0,")).isLeft)

  test("Size.parse: rejects zero height"):
    assert(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire(",0")).isLeft)

  test("Size.parse: rejects garbage"):
    assert(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("large")).isLeft)

  test("Size.parse: rejects negative"):
    assert(Iiif.Size.parse(com.histmersion.api.media.Iiif.Size.Wire("-100,")).isLeft)

  // ── Format parsing ────────────────────────────────────────────────────────────────────────────

  test("parseFormat: jpg"):
    assertEquals(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("jpg")), Right("jpg"))

  test("parseFormat: jpeg"):
    assertEquals(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("jpeg")), Right("jpg"))

  test("parseFormat: png"):
    assertEquals(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("png")), Right("png"))

  test("parseFormat: rejects unsupported"):
    assert(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("gif")).isLeft)
    assert(Iiif.parseFormat(com.histmersion.api.library.ArchiveFile.Format.Wire("webp")).isLeft)

  // ── Content type ──────────────────────────────────────────────────────────────────────────────

  test("contentType: jpg"):
    assertEquals(Iiif.contentType(com.histmersion.api.media.Iiif.Format("jpg")), "image/jpeg")

  test("contentType: png"):
    assertEquals(Iiif.contentType(com.histmersion.api.media.Iiif.Format("png")), "image/png")

  // ── info.json ─────────────────────────────────────────────────────────────────────────────────

  test("infoJson produces valid structure"):
    val json = Iiif.infoJson(com.histmersion.api.media.Iiif.ImageId("http://example.com/iiif/test/fig1.png"), com.histmersion.api.media.ImageSize.Width(800), com.histmersion.api.media.ImageSize.Height(600))
    val cursor = json.hcursor
    assertEquals(cursor.get[String]("@context"), Right(Iiif.ApiContext))
    assertEquals(cursor.get[String]("id"), Right("http://example.com/iiif/test/fig1.png"))
    assertEquals(cursor.get[String]("type"), Right("ImageService3"))
    assertEquals(cursor.get[String]("protocol"), Right(Iiif.Protocol))
    assertEquals(cursor.get[Int]("width"), Right(800))
    assertEquals(cursor.get[Int]("height"), Right(600))
    assertEquals(cursor.get[String]("profile"), Right(Iiif.Profile))
