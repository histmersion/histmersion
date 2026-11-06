package com.histmersion.web

import com.raquo.laminar.api.L.*

/** A small OFFSET pager: "Items 26–50 of 112" with previous / next — for a list ordered by something other than time
  * (the manager window's items, most urgent first), where [[ListPaging]]'s newest-first keyset cursor does not apply.
  * The manager's events use [[ListPaging]].
  */
object Pager:

  /** The line a page shows: `a–b of n`, or `none` for an empty one. Pure. */
  type RangeResult = RangeResult.T
  object RangeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeOffset = RangeOffset.T
  object RangeOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeShown = RangeShown.T
  object RangeShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeTotal = RangeTotal.T
  object RangeTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviousResult = PreviousResult.T
  object PreviousResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviousOffset = PreviousOffset.T
  object PreviousOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviousLimit = _root_.com.histmersion.web.Pager.NextLimit
  val PreviousLimit: _root_.com.histmersion.web.Pager.NextLimit.type = _root_.com.histmersion.web.Pager.NextLimit
  type NextResult = NextResult.T
  object NextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextOffset = NextOffset.T
  object NextOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextLimit = NextLimit.T
  object NextLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextTotal = NextTotal.T
  object NextTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLabel = ApplyLabel.T
  object ApplyLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyOffset = ApplyOffset.T
  object ApplyOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLimit = _root_.com.histmersion.web.Pager.NextLimit
  val ApplyLimit: _root_.com.histmersion.web.Pager.NextLimit.type = _root_.com.histmersion.web.Pager.NextLimit
  type ApplyShown = ApplyShown.T
  object ApplyShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyTotal = ApplyTotal.T
  object ApplyTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def range(offset: com.histmersion.web.Pager.RangeOffset, shown: com.histmersion.web.Pager.RangeShown, total: com.histmersion.web.Pager.RangeTotal): com.histmersion.web.Pager.RangeResult =
    if total <= 0 || shown <= 0 then "none"
    else s"${offset + 1}–${offset + shown} of $total"

  /** The offset of the previous page (never below 0), and of the next one (None past the end). Pure. */
  def previous(offset: com.histmersion.web.Pager.PreviousOffset, limit: com.histmersion.web.Pager.PreviousLimit): com.histmersion.web.Pager.PreviousResult = math.max(0, offset - math.max(1, limit))
  def next(offset: com.histmersion.web.Pager.NextOffset, limit: com.histmersion.web.Pager.NextLimit, total: com.histmersion.web.Pager.NextTotal): Option[com.histmersion.web.Pager.NextResult] =
    Option(offset + math.max(1, limit)).filter(_ < total)

  /** The controls: `offset` moves; `limit` and `total` come from the last page read. */
  def apply(label: com.histmersion.web.Pager.ApplyLabel, offset: Var[com.histmersion.web.Pager.ApplyOffset], limit: com.histmersion.web.Pager.ApplyLimit, shown: Signal[com.histmersion.web.Pager.ApplyShown], total: Signal[com.histmersion.web.Pager.ApplyTotal]): HtmlElement =
    div(
      cls := "toolbar pager",
      span(
        cls := "muted pager-range",
        child.text <-- offset.signal.combineWith(shown, total).map((o, s, t) => s"$label ${range(o, s, t)}")
      ),
      div(
        cls := "btn-group",
        button(
          typ := "button",
          cls := "btn btn--sm",
          "previous",
          disabled <-- offset.signal.map(_ <= 0),
          onClick --> (_ => offset.update(previous(_, limit)))
        ),
        button(
          typ := "button",
          cls := "btn btn--sm",
          "next",
          disabled <-- offset.signal.combineWith(total).map((o, t) => next(o, limit, t).isEmpty),
          onClick --> (_ => offset.update(o => o + limit))
        )
      )
    )
