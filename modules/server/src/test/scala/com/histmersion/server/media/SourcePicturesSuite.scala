package com.histmersion
package server.media

import com.histmersion.domain.media.covers.SourcePictures
import com.histmersion.domain.media.assets.SourceFigure

import com.histmersion.domain.media.figures.FigurePick

class SourcePicturesSuite extends munit.FunSuite:

  test("a stored figure as the pick weighs it: its name and size, one link until the text is read"):
    val row = SourceFigure(
      SourceFigure.Uid("u1"),
      SourceFigure.Name("fig-0002-01.png"),
      Some(SourceFigure.Width(800)),
      None,
      hasThumb = SourceFigure.HasThumb.WithThumb
    )
    assertEquals(
      SourcePictures.toFigure(row),
      FigurePick.Figure(
        FigurePick.Figure.Name("fig-0002-01.png"),
        Some(FigurePick.Figure.Width(800)),
        None,
        refs = FigurePick.Figure.Refs(1),
        firstAt = None
      )
    )
