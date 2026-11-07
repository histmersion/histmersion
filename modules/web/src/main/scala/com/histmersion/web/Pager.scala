package com.histmersion.web

import com.raquo.laminar.api.L.*

/** A small OFFSET pager: "Items 26–50 of 112" with previous / next — for a list ordered by something other than time
  * (the manager window's items, most urgent first), where [[ListPaging]]'s newest-first keyset cursor does not apply.
  * The manager's events use [[ListPaging]].
  */
object Pager:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeText = RangeText.T
  object RangeText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The line a page shows: `a–b of n`, or `none` for an empty one. Pure. */

  def range(offset: com.histmersion.api.Offset, shown: com.histmersion.api.Paging.Shown, total: com.histmersion.api.Paging.Page.Total): com.histmersion.web.Pager.RangeText =
    if total.value <= 0 || shown.value <= 0 then com.histmersion.web.Pager.RangeText("none")
    else com.histmersion.web.Pager.RangeText(s"${offset.value + 1}–${offset.value + shown.value} of ${total.value}")

  /** The offset of the previous page (never below 0), and of the next one (None past the end). Pure. */
  def previous(offset: com.histmersion.api.Offset, limit: com.histmersion.api.Paging.Limit): com.histmersion.api.Offset = com.histmersion.api.Offset(math.max(0, offset.value - math.max(1, limit.value)))
  def next(offset: com.histmersion.api.Offset, limit: com.histmersion.api.Paging.Limit, total: com.histmersion.api.Paging.Page.Total): Option[com.histmersion.api.Offset] =
    Option(com.histmersion.api.Offset(offset.value + math.max(1, limit.value))).filter(_.value < total.value)

  /** The controls: `offset` moves; `limit` and `total` come from the last page read. */
  def apply(label: com.histmersion.web.Pager.Label, offset: Var[com.histmersion.api.Offset], limit: com.histmersion.api.Paging.Limit, shown: Signal[com.histmersion.api.Paging.Shown], total: Signal[com.histmersion.api.Paging.Page.Total]): HtmlElement =
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
          onClick --> (_ => offset.update(o => com.histmersion.api.Offset(o.value + limit.value)))
        )
      )
    )
