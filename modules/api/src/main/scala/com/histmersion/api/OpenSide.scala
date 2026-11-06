package com.histmersion.api

import com.histmersion.api.catalogue.Period
enum OpenSide:
  case Start, End

object OpenSide:

  def bounds(period: Option[Period], open: Option[OpenSide]): (Option[com.histmersion.api.catalogue.Year], Option[com.histmersion.api.catalogue.Year]) =
    (period.map(_.start).filterNot(_ => open.contains(Start)), period.map(_.end).filterNot(_ => open.contains(End)))

  def split(s: Span): (Option[Period], Option[OpenSide]) = s match
    case Span(Some(a), Some(b)) => (Some(if a.value <= b.value then Period(a, b) else Period(b, a)), None)
    case Span(Some(a), None)    => (Some(Period(a, a)), Some(End))
    case Span(None, Some(b))    => (Some(Period(b, b)), Some(Start))
    case Span(None, None)       => (None, None)
