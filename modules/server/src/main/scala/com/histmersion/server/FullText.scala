package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** Whether a source page shows the whole OCR'd paper. */
@movesTo(FeaturePlace.LibrarySources)
enum FullText derives CanEqual:
  case Hidden
  case Missing
  case Shown(markdown: FullText.Shown.Markdown)

object FullText:
  object Shown:
    type Markdown = Markdown.T
    object Markdown extends Newtype[String, Pure]
