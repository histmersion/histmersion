package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*

/** Whether a source page shows the whole OCR'd paper. */
enum FullText:
  case Hidden
  case Missing
  case Shown(markdown: Shown.Markdown)

object FullText:
  object Shown:
    type Markdown = Markdown.T
    object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
