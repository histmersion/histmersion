package com.histmersion.server.media

import com.histmersion.domain.media.covers.SourcePictures
import com.histmersion.domain.media.assets.SourceFigure

import com.histmersion.domain.media.figures.FigurePick

class SourcePicturesSuite extends munit.FunSuite:

  test("a stored figure as the pick weighs it: its name and size, one link until the text is read"):
    val row = SourceFigure(com.histmersion.domain.media.assets.SourceFigure.Uid("u1"), com.histmersion.domain.media.assets.SourceFigure.Name("fig-0002-01.png"), Some(com.histmersion.domain.media.assets.SourceFigure.Width(800)), None, hasThumb = com.histmersion.domain.media.assets.SourceFigure.HasThumb(true))
    assertEquals(
      SourcePictures.toFigure(row),
      FigurePick.Figure(com.histmersion.domain.media.figures.FigurePick.Figure.Name("fig-0002-01.png"), Some(com.histmersion.domain.media.figures.FigurePick.Figure.Width(800)), None, refs = com.histmersion.domain.media.figures.FigurePick.Figure.Refs(1), firstAt = None)
    )
