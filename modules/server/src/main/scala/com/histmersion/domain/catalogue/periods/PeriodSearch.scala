package com.histmersion
package domain.catalogue.periods

import io.github.iltotore.iron.constraint.any.Pure

/** What a period search asks for: a label substring (any case, `%` and `_` literal), an exact region (any case), and an
  * overlap with `from..to`, either bound alone a span too.
  */
final case class PeriodSearch(
    text: Option[PeriodSearch.Text] = None,
    region: Option[PeriodSearch.Region] = None,
    from: Option[PeriodSearch.From] = None,
    to: Option[PeriodSearch.To] = None
)

object PeriodSearch:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type From = From.T
  object From extends Newtype[Long, Pure]
  type To = To.T
  object To extends Newtype[Long, Pure]
