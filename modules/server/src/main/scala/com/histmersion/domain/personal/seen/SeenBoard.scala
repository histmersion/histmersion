package com.histmersion.domain.personal.seen

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A window of the web page whose news a person has seen up to some time (`tasks`, `approvals`, `goals`, …). Which
  * boards there are is the page's; personal keeps only the marks.
  */
type SeenBoard = SeenBoard.T

object SeenBoard extends RefinedType[String, Pure]
