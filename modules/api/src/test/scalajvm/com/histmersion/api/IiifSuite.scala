package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

/** Pure tests for the IIIF Image API parameter parsing (plan §4.3). No database, no HTTP — only the [[Iiif]] object's
  * parse/format logic.
  */

import com.histmersion.api.media.Iiif
import com.histmersion.api.library.ArchiveFile
import com.histmersion.api.media.ImageSize
class IiifSuite extends munit.FunSuite:

  // ── Region parsing ────────────────────────────────────────────────────────────────────────────

  test("Region.parse: full"):
    assertEquals(Iiif.Region.parse(Iiif.Region.Wire("full")), Right(Iiif.Region.Full))

  test("Region.parse: square maps to full (Level 1 fallback)"):
    assertEquals(Iiif.Region.parse(Iiif.Region.Wire("square")), Right(Iiif.Region.Full))

  test("Region.parse: pixel coordinates"):
    assertEquals(
      Iiif.Region.parse(Iiif.Region.Wire("10,20,100,200")),
      Right(
        Iiif.Region.Pixels(
          Iiif.Region.Pixels.X(10),
          Iiif.Region.Pixels.Y(20),
          Iiif.Region.Pixels.W(100),
          Iiif.Region.Pixels.H(200)
        )
      )
    )

  test("Region.parse: zero origin"):
    assertEquals(
      Iiif.Region.parse(Iiif.Region.Wire("0,0,50,50")),
      Right(
        Iiif.Region
          .Pixels(Iiif.Region.Pixels.X(0), Iiif.Region.Pixels.Y(0), Iiif.Region.Pixels.W(50), Iiif.Region.Pixels.H(50))
      )
    )

  test("Region.parse: rejects zero width"):
    assert(Iiif.Region.parse(Iiif.Region.Wire("0,0,0,50")).isLeft)

  test("Region.parse: rejects zero height"):
    assert(Iiif.Region.parse(Iiif.Region.Wire("0,0,50,0")).isLeft)

  test("Region.parse: rejects negative width"):
    assert(Iiif.Region.parse(Iiif.Region.Wire("0,0,-10,50")).isLeft)

  test("Region.parse: rejects garbage"):
    assert(Iiif.Region.parse(Iiif.Region.Wire("foo")).isLeft)

  test("Region.parse: rejects too few coords"):
    assert(Iiif.Region.parse(Iiif.Region.Wire("10,20,100")).isLeft)

  // ── Size parsing ──────────────────────────────────────────────────────────────────────────────

  test("Size.parse: max"):
    assertEquals(Iiif.Size.parse(Iiif.Size.Wire("max")), Right(Iiif.Size.Max))

  test("Size.parse: ^max"):
    assertEquals(Iiif.Size.parse(Iiif.Size.Wire("^max")), Right(Iiif.Size.ScaleUp))

  test("Size.parse: width"):
    assertEquals(Iiif.Size.parse(Iiif.Size.Wire("200,")), Right(Iiif.Size.Width(Iiif.Size.Width.W(200))))

  test("Size.parse: height"):
    assertEquals(Iiif.Size.parse(Iiif.Size.Wire(",150")), Right(Iiif.Size.Height(Iiif.Size.Height.H(150))))

  test("Size.parse: exact"):
    assertEquals(
      Iiif.Size.parse(Iiif.Size.Wire("300,200")),
      Right(Iiif.Size.Exact(Iiif.Size.Exact.W(300), Iiif.Size.Exact.H(200)))
    )

  test("Size.parse: best fit"):
    assertEquals(
      Iiif.Size.parse(Iiif.Size.Wire("!300,200")),
      Right(Iiif.Size.BestFit(Iiif.Size.BestFit.W(300), Iiif.Size.BestFit.H(200)))
    )

  test("Size.parse: rejects zero width"):
    assert(Iiif.Size.parse(Iiif.Size.Wire("0,")).isLeft)

  test("Size.parse: rejects zero height"):
    assert(Iiif.Size.parse(Iiif.Size.Wire(",0")).isLeft)

  test("Size.parse: rejects garbage"):
    assert(Iiif.Size.parse(Iiif.Size.Wire("large")).isLeft)

  test("Size.parse: rejects negative"):
    assert(Iiif.Size.parse(Iiif.Size.Wire("-100,")).isLeft)

  // ── Format parsing ────────────────────────────────────────────────────────────────────────────

  test("parseFormat: jpg"):
    assertEquals(Iiif.parseFormat(ArchiveFile.Format.Wire("jpg")), Right("jpg"))

  test("parseFormat: jpeg"):
    assertEquals(Iiif.parseFormat(ArchiveFile.Format.Wire("jpeg")), Right("jpg"))

  test("parseFormat: png"):
    assertEquals(Iiif.parseFormat(ArchiveFile.Format.Wire("png")), Right("png"))

  test("parseFormat: rejects unsupported"):
    assert(Iiif.parseFormat(ArchiveFile.Format.Wire("gif")).isLeft)
    assert(Iiif.parseFormat(ArchiveFile.Format.Wire("webp")).isLeft)

  // ── Content type ──────────────────────────────────────────────────────────────────────────────

  test("contentType: jpg"):
    assertEquals(Iiif.contentType(Iiif.Format("jpg")), "image/jpeg")

  test("contentType: png"):
    assertEquals(Iiif.contentType(Iiif.Format("png")), "image/png")

  // ── info.json ─────────────────────────────────────────────────────────────────────────────────

  test("infoJson produces valid structure"):
    val json =
      Iiif.infoJson(Iiif.ImageId("http://example.com/iiif/test/fig1.png"), ImageSize.Width(800), ImageSize.Height(600))
    val cursor = json.hcursor
    assertEquals(cursor.get[String]("@context"), Right(Iiif.ApiContext))
    assertEquals(cursor.get[String]("id"), Right("http://example.com/iiif/test/fig1.png"))
    assertEquals(cursor.get[String]("type"), Right("ImageService3"))
    assertEquals(cursor.get[String]("protocol"), Right(Iiif.Protocol))
    assertEquals(cursor.get[Int]("width"), Right(800))
    assertEquals(cursor.get[Int]("height"), Right(600))
    assertEquals(cursor.get[String]("profile"), Right(Iiif.Profile))
