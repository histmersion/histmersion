package com.histmersion.api

/** The side of a dating its source left OPEN (#1403): a timespan with a start and no end is "from the start on", one
  * with an end and no start "until the end" — never the single known year.
  *
  * A [[Place]], [[PlaceName]] or [[PlaceRelation]] keeps its known bound(s) in `period` (a one-ended span as the one
  * year it names) and the open side here; [[OpenSide.bounds]] is what is stored, the `int8range` `[start,)` or `(,end]`
  * (`PeriodSql.bounds`).
  */
enum OpenSide:
  case Start, End

object OpenSide:
  /** The inclusive bounds to store: the known years of `period`, with the `open` side dropped. */
  def bounds(period: Option[Period], open: Option[OpenSide]): (Option[Long], Option[Long]) =
    (period.map(_.start).filterNot(_ => open.contains(Start)), period.map(_.end).filterNot(_ => open.contains(End)))

  /** A span with optional sides as (`period`, `open`): both sides known is a closed period; one side known is that year
    * with the other side open; neither is no period.
    */
  def split(s: Span): (Option[Period], Option[OpenSide]) = s match
    case Span(Some(a), Some(b)) => (Some(if a <= b then Period(a, b) else Period(b, a)), None)
    case Span(Some(a), None)    => (Some(Period(a, a)), Some(End))
    case Span(None, Some(b))    => (Some(Period(b, b)), Some(Start))
    case Span(None, None)       => (None, None)
