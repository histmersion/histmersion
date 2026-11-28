package com.histmersion
package domain

import com.histmersion.{Period, Year}
import com.histmersion.lint.{tolerated, LintRule}

/** Years as the corpus stores them (signed `Long`s, BCE negative) made into the model's `Year` and `Period`.
  */
object DeepTime:
  export api.catalogue.Year as SignedYear

  /** `None` for a year beyond what a `Year` holds — no source dates anything to it. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def year(y: SignedYear): Option[Year] = yearOf(y.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def yearOf(y: Long): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  /** The period between two years given in either order; `None` when either lies beyond a `Year`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def span(a: SignedYear, b: SignedYear): Option[Period] =
    for
      start <- yearOf(a.value.min(b.value))
      end <- yearOf(a.value.max(b.value))
      period <- Period.from(start, end).toOption
    yield period
