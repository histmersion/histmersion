package com.histmersion.domain.media.figures

import com.histmersion.testsupport.ComparedByValue.given

/** Which files of a figures folder are figures, and what a note says of where they are (plan §1.8). */
class FiguresSuite extends munit.FunSuite:

  test("the images of a folder, in name order, each at most the byte cap, measured off the header"):
    val png = new Array[Byte](24)
    Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10).copyToArray(png)
    png(19) = 40; png(23) = 30
    val got = Figures.of(
      List(
        com.histmersion.domain.media.figures.Figures.FileName("b.txt") -> com.histmersion.domain.media.assets.NewFigure.Bytes(Array[Byte](4, 5)),
        com.histmersion.domain.media.figures.Figures.FileName("z.png") -> com.histmersion.domain.media.assets.NewFigure.Bytes(png),
        com.histmersion.domain.media.figures.Figures.FileName("a b.jpg") -> com.histmersion.domain.media.assets.NewFigure.Bytes(new Array[Byte](64)),
        com.histmersion.domain.media.figures.Figures.FileName("e.png") -> com.histmersion.domain.media.assets.NewFigure.Bytes(Array.emptyByteArray)
      ),
      maxBytes = com.histmersion.ByteCount(32)
    )
    assertEquals(got.map(f => (f.name, f.mime, f.width, f.height)), List(("z.png", "image/png", Some(40), Some(30))))
    assertEquals(Figures.of(List(com.histmersion.domain.media.figures.Figures.FileName("a b.jpg") -> com.histmersion.domain.media.assets.NewFigure.Bytes(new Array[Byte](64))), com.histmersion.ByteCount(1024)).map(_.name), List("a-b.jpg"))

  test("md_path is read from the frontmatter, quoted or not"):
    assertEquals(Figures.mdPathOf(com.histmersion.domain.media.figures.Figures.NoteText("---\nmd_path: md/x/paper.md\n---\n")), Some("md/x/paper.md"))
    assertEquals(Figures.mdPathOf(com.histmersion.domain.media.figures.Figures.NoteText("---\ntitle: t\n---\n")), None)
