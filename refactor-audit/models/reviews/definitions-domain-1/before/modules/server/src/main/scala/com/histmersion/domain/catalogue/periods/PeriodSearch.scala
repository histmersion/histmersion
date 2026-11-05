package com.histmersion.domain.catalogue.periods

/** What a period search asks for: a label substring (any case, `%` and `_` literal), an exact region (any case), and an
  * overlap with `from..to`, either bound alone a span too.
  */
final case class PeriodSearch(
    text: Option[String] = None,
    region: Option[String] = None,
    from: Option[Long] = None,
    to: Option[Long] = None
)
