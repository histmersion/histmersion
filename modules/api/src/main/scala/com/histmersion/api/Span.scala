package com.histmersion
package api

import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Kernel)
final case class Span(from: Option[Year], to: Option[Year]):
  def isEmpty: Boolean = (from.isEmpty && to.isEmpty)

object Span:

  val always: Span = Span(None, None)
