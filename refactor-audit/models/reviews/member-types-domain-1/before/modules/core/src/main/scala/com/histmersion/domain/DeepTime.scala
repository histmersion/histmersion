package com.histmersion.domain

import com.histmersion.{Period, Year}

/** Years as the corpus stores them (signed `Long`s, BCE negative) made into the model's `Year` and `Period`.
  */
object DeepTime:
  /** `None` for a year beyond what a `Year` holds — no source dates anything to it. */
  def year(y: Long): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  /** The period between two years given in either order; `None` when either lies beyond a `Year`. */
  def span(a: Long, b: Long): Option[Period] =
    for
      start <- year(a.min(b))
      end <- year(a.max(b))
      period <- Period.from(start, end).toOption
    yield period
