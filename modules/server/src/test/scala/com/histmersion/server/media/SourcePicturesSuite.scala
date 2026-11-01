package com.histmersion.server.media

import com.histmersion.domain.media.covers.SourcePictures
import com.histmersion.domain.media.assets.SourceFigure

import com.histmersion.domain.media.figures.FigurePick

class SourcePicturesSuite extends munit.FunSuite:

  test("a stored figure as the pick weighs it: its name and size, one link until the text is read"):
    val row = SourceFigure("u1", "fig-0002-01.png", Some(800), None, hasThumb = true)
    assertEquals(
      SourcePictures.toFigure(row),
      FigurePick.Figure("fig-0002-01.png", Some(800), None, refs = 1, firstAt = None)
    )
