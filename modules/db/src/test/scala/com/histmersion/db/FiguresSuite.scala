package com.histmersion.db

import java.nio.file.{Files, Path}

/** Pure path arithmetic for the old pipeline's figures (plan §1.8) — no database. */
class FiguresSuite extends munit.FunSuite:

  test("mdPathOf reads the frontmatter key, quoted or not"):
    assertEquals(Figures.mdPathOf("---\nmd_path: md/haak2015/paper.md\n---\n"), Some("md/haak2015/paper.md"))
    assertEquals(Figures.mdPathOf("---\nmd_path: \"md/x/paper.md\"\n---\n"), Some("md/x/paper.md"))
    assertEquals(Figures.mdPathOf("---\ntitle: no md path\n---\n"), None)

  test("dirFor puts figures next to the converted body named by md_path"):
    val note = Path.of("/corpus/wiki/sources/haak2015.md")
    val dir  = Figures.dirFor(note, "---\nmd_path: md/haak2015/paper.md\n---\n", "haak2015")
    assertEquals(dir.map(_.toString), Some("/corpus/md/haak2015/figures"))

  test("dirFor falls back to the md/<citekey>/figures convention"):
    val note = Path.of("/corpus/wiki/sources/x.md")
    assertEquals(Figures.dirFor(note, "---\ntitle: t\n---\n", "x").map(_.toString),
      Some("/corpus/md/x/figures"))

  test("dirFor gives up when the note is not three levels deep"):
    assertEquals(Figures.dirFor(Path.of("x.md"), "", "x"), None)

  test("load reads images, skips non-images, and respects the byte cap"):
    val dir = Files.createTempDirectory("histmersion-figs")
    Files.write(dir.resolve("a.png"), Array[Byte](1, 2, 3))
    Files.write(dir.resolve("b.txt"), Array[Byte](4, 5))
    Files.write(dir.resolve("big.jpg"), new Array[Byte](64))
    val loaded = Figures.load(dir, maxBytes = 8)
    assertEquals(loaded.map(_.name), List("a.png"))
    assertEquals(loaded.head.mime, "image/png")
    assertEquals(Figures.load(dir, maxBytes = 1024).map(_.name), List("a.png", "big.jpg"))

  test("load measures the dimensions off the header"):
    val dir = Files.createTempDirectory("histmersion-figs-size")
    val png = new Array[Byte](24)
    Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10).copyToArray(png)
    png(19) = 40; png(23) = 30                      // width = 40, height = 30 (big-endian)
    Files.write(dir.resolve("a.png"), png)
    val a = Figures.load(dir, 1024).head
    assertEquals((a.width, a.height), (Some(40), Some(30)))

  test("load on a missing directory is empty, not an error"):
    assertEquals(Figures.load(Path.of("/no/such/dir"), 1024), Nil)
