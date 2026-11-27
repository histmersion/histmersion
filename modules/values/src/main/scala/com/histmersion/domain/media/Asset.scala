package com.histmersion
package domain.media

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

object Asset:

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]

  object LinkTarget:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val Event = "event"
    val Finding = "finding"
    val Place = "place"
    val Source = "source"
    val Article = "article"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: List[Wire] = List(Wire(Event), Wire(Finding), Wire(Place), Wire(Source), Wire(Article))
