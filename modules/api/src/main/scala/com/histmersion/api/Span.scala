package com.histmersion
package api

import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Kernel)
final case class Span(from: Option[Year], to: Option[Year]):
  def isEmpty: Boolean = (from.isEmpty && to.isEmpty)

object Span:

  val always: Span = Span(None, None)
