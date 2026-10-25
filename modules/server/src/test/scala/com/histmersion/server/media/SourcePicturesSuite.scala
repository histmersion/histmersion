package com.histmersion.server.media

import com.histmersion.db.MediaRepo
import com.histmersion.domain.FigurePick

class SourcePicturesSuite extends munit.FunSuite:

  test("a stored figure as the pick weighs it: its name and size, one link until the text is read"):
    val row = MediaRepo.SourceFigure("u1", "fig-0002-01.png", Some(800), None, hasThumb = true)
    assertEquals(SourcePictures.toFigure(row), FigurePick.Figure("fig-0002-01.png", Some(800), None, refs = 1, firstAt = None))
