package com.histmersion.domain

import hearth.kindlings.optics.syntax.*
import com.histmersion.files.*
import com.histmersion.api.given

/** Citekey shape for a rename. */
object Citekeys:
  private val Shape = "^[a-z][a-z0-9_]*$".r
  def valid(ck: String): Boolean = Shape.matches(ck)
