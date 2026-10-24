package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

/** Pure tests for the IIIF Image API parameter parsing (plan §4.3).
  * No database, no HTTP — only the [[Iiif]] object's parse/format logic. */
class IiifSuite extends munit.FunSuite:

  // ── Region parsing ────────────────────────────────────────────────────────────────────────────

  test("Region.parse: full"):
    assertEquals(Iiif.Region.parse("full"), Right(Iiif.Region.Full))

  test("Region.parse: square maps to full (Level 1 fallback)"):
    assertEquals(Iiif.Region.parse("square"), Right(Iiif.Region.Full))

  test("Region.parse: pixel coordinates"):
    assertEquals(Iiif.Region.parse("10,20,100,200"), Right(Iiif.Region.Pixels(10, 20, 100, 200)))

  test("Region.parse: zero origin"):
    assertEquals(Iiif.Region.parse("0,0,50,50"), Right(Iiif.Region.Pixels(0, 0, 50, 50)))

  test("Region.parse: rejects zero width"):
    assert(Iiif.Region.parse("0,0,0,50").isLeft)

  test("Region.parse: rejects zero height"):
    assert(Iiif.Region.parse("0,0,50,0").isLeft)

  test("Region.parse: rejects negative width"):
    assert(Iiif.Region.parse("0,0,-10,50").isLeft)

  test("Region.parse: rejects garbage"):
    assert(Iiif.Region.parse("foo").isLeft)

  test("Region.parse: rejects too few coords"):
    assert(Iiif.Region.parse("10,20,100").isLeft)

  // ── Size parsing ──────────────────────────────────────────────────────────────────────────────

  test("Size.parse: max"):
    assertEquals(Iiif.Size.parse("max"), Right(Iiif.Size.Max))

  test("Size.parse: ^max"):
    assertEquals(Iiif.Size.parse("^max"), Right(Iiif.Size.ScaleUp))

  test("Size.parse: width"):
    assertEquals(Iiif.Size.parse("200,"), Right(Iiif.Size.Width(200)))

  test("Size.parse: height"):
    assertEquals(Iiif.Size.parse(",150"), Right(Iiif.Size.Height(150)))

  test("Size.parse: exact"):
    assertEquals(Iiif.Size.parse("300,200"), Right(Iiif.Size.Exact(300, 200)))

  test("Size.parse: best fit"):
    assertEquals(Iiif.Size.parse("!300,200"), Right(Iiif.Size.BestFit(300, 200)))

  test("Size.parse: rejects zero width"):
    assert(Iiif.Size.parse("0,").isLeft)

  test("Size.parse: rejects zero height"):
    assert(Iiif.Size.parse(",0").isLeft)

  test("Size.parse: rejects garbage"):
    assert(Iiif.Size.parse("large").isLeft)

  test("Size.parse: rejects negative"):
    assert(Iiif.Size.parse("-100,").isLeft)

  // ── Format parsing ────────────────────────────────────────────────────────────────────────────

  test("parseFormat: jpg"):
    assertEquals(Iiif.parseFormat("jpg"), Right("jpg"))

  test("parseFormat: jpeg"):
    assertEquals(Iiif.parseFormat("jpeg"), Right("jpg"))

  test("parseFormat: png"):
    assertEquals(Iiif.parseFormat("png"), Right("png"))

  test("parseFormat: rejects unsupported"):
    assert(Iiif.parseFormat("gif").isLeft)
    assert(Iiif.parseFormat("webp").isLeft)

  // ── Content type ──────────────────────────────────────────────────────────────────────────────

  test("contentType: jpg"):
    assertEquals(Iiif.contentType("jpg"), "image/jpeg")

  test("contentType: png"):
    assertEquals(Iiif.contentType("png"), "image/png")

  // ── info.json ─────────────────────────────────────────────────────────────────────────────────

  test("infoJson produces valid structure"):
    val json = Iiif.infoJson("http://example.com/iiif/test/fig1.png", 800, 600)
    val cursor = json.hcursor
    assertEquals(cursor.get[String]("@context"), Right(Iiif.ApiContext))
    assertEquals(cursor.get[String]("id"), Right("http://example.com/iiif/test/fig1.png"))
    assertEquals(cursor.get[String]("type"), Right("ImageService3"))
    assertEquals(cursor.get[String]("protocol"), Right(Iiif.Protocol))
    assertEquals(cursor.get[Int]("width"), Right(800))
    assertEquals(cursor.get[Int]("height"), Right(600))
    assertEquals(cursor.get[String]("profile"), Right(Iiif.Profile))
