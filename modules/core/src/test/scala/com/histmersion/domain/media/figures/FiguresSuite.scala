package com.histmersion.domain.media.figures

/** Which files of a figures folder are figures, and what a note says of where they are (plan §1.8). */
class FiguresSuite extends munit.FunSuite:

  test("the images of a folder, in name order, each at most the byte cap, measured off the header"):
    val png = new Array[Byte](24)
    Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10).copyToArray(png)
    png(19) = 40; png(23) = 30
    val got = Figures.of(
      List("b.txt" -> Array[Byte](4, 5), "z.png" -> png, "a b.jpg" -> new Array[Byte](64), "e.png" -> Array.emptyByteArray),
      maxBytes = 32
    )
    assertEquals(got.map(f => (f.name, f.mime, f.width, f.height)), List(("z.png", "image/png", Some(40), Some(30))))
    assertEquals(Figures.of(List("a b.jpg" -> new Array[Byte](64)), 1024).map(_.name), List("a-b.jpg"))

  test("md_path is read from the frontmatter, quoted or not"):
    assertEquals(Figures.mdPathOf("---\nmd_path: md/x/paper.md\n---\n"), Some("md/x/paper.md"))
    assertEquals(Figures.mdPathOf("---\ntitle: t\n---\n"), None)
