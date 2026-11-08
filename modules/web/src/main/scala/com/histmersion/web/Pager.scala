package com.histmersion
package web

import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Offset
import com.histmersion.api.Paging

/** A small OFFSET pager: "Items 26–50 of 112" with previous / next — for a list ordered by something other than time
  * (the manager window's items, most urgent first), where [[ListPaging]]'s newest-first keyset cursor does not apply.
  * The manager's events use [[ListPaging]].
  */
object Pager:
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type RangeText = RangeText.T
  object RangeText extends RefinedType[String, Pure]

  /** The line a page shows: `a–b of n`, or `none` for an empty one. Pure. */

  def range(offset: Offset, shown: Paging.Shown, total: Paging.Page.Total): RangeText =
    if total.value <= 0 || shown.value <= 0 then RangeText("none")
    else RangeText(s"${offset.value + 1}–${offset.value + shown.value} of ${total.value}")

  /** The offset of the previous page (never below 0), and of the next one (None past the end). Pure. */
  def previous(offset: Offset, limit: Paging.Limit): Offset = Offset(math.max(0, offset.value - math.max(1, limit.value)))
  def next(offset: Offset, limit: Paging.Limit, total: Paging.Page.Total): Option[Offset] =
    Option(Offset(offset.value + math.max(1, limit.value))).filter(_.value < total.value)

  /** The controls: `offset` moves; `limit` and `total` come from the last page read. */
  def apply(label: Label, offset: Var[Offset], limit: Paging.Limit, shown: Signal[Paging.Shown], total: Signal[Paging.Page.Total]): HtmlElement =
    div(
      cls := "toolbar pager",
      span(
        cls := "muted pager-range",
        child.text <-- offset.signal.combineWith(shown, total).map((o, s, t) => s"${label.value} ${range(o, s, t).value}")
      ),
      div(
        cls := "btn-group",
        button(
          typ := "button",
          cls := "btn btn--sm",
          "previous",
          disabled <-- offset.signal.map(_.value <= 0),
          onClick --> (_ => offset.update(previous(_, limit)))
        ),
        button(
          typ := "button",
          cls := "btn btn--sm",
          "next",
          disabled <-- offset.signal.combineWith(total).map((o, t) => next(o, limit, t).isEmpty),
          onClick --> (_ => offset.update(o => Offset(o.value + limit.value)))
        )
      )
    )
