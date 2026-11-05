package com.histmersion.domain.catalogue.periods

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
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
