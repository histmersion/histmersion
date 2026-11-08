package com.histmersion
package domain

import com.histmersion.{Period, Year}
import io.github.iltotore.iron.constraint.any.Pure

/** Years as the corpus stores them (signed `Long`s, BCE negative) made into the model's `Year` and `Period`.
  */
object DeepTime:
  type SignedYear = SignedYear.T
  object SignedYear extends Newtype[Long, Pure]

  /** `None` for a year beyond what a `Year` holds — no source dates anything to it. */

  def year(y: SignedYear): Option[Year] = yearOf(y.value)

  private def yearOf(y: Long): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  /** The period between two years given in either order; `None` when either lies beyond a `Year`. */
  def span(a: SignedYear, b: SignedYear): Option[Period] =
    for
      start <- yearOf(a.value.min(b.value))
      end <- yearOf(a.value.max(b.value))
      period <- Period.from(start, end).toOption
    yield period
