package com.histmersion.domain.library.sources

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given

/** Citekey shape for a rename. */
object Citekeys:
  private val Shape = "^[a-z][a-z0-9_]*$".r
  def valid(ck: String): Boolean = Shape.matches(ck)
