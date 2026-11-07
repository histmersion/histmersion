package com.histmersion.web

import com.histmersion.api.Plural
import com.raquo.laminar.api.L.*

/** The ONE pagination look of the sidebar's result tabs (sources, articles, events, by time, by region): a count line
  * on top — `showing 1–20 of 285 sources · 491 findings · 1475 BCE → 1465 BCE` — and under the rows one
  * `show 20 more (265 left)` button, which reads `loading…` while the next page is on its way. The sources tab's look
  * is the reference.
  */
object ResultsPaging:
  type Failed = Failed.T
  object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Loading = Loading.T
  object Loading extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageNumber = PageNumber.T
  object PageNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The count line: `showing 1–shown of total things` while rows are left, `total things` when all are shown,
    * `no things` for none; then the tab's own `extras`, each after a ` · `.
    */

  def countLine(shown: com.histmersion.api.Paging.Shown, total: com.histmersion.api.Paging.Page.Total, singular: com.histmersion.api.Plural.Noun.Singular, plural: com.histmersion.api.Plural.Noun.Plural = com.histmersion.api.Plural.Noun.Plural(""), extras: List[com.histmersion.api.UiLabels.Text] = Nil): com.histmersion.api.UiLabels.Text =
    val many = if plural.nonEmpty then plural else singular + "s"
    val head =
      if total <= 0 then s"no $many"
      else if shown < total then s"showing 1–$shown of ${Plural.count(total.toLong, singular, many)}"
      else Plural.count(total.toLong, singular, many)
    (head :: extras.filter(_.nonEmpty)).mkString(" · ")

  /** The button's label: `show 20 more (265 left)` — how many the next click brings (at most `page`) and how many are
    * left; `None` when nothing is.
    */
  def moreLabel(left: com.histmersion.api.RowCount, page: com.histmersion.web.ResultsPaging.PageNumber): Option[com.histmersion.api.UiLabels.Text] =
    Option.when(left > 0)(s"show ${math.min(page, left)} more ($left left)")

  /** The filter's time window, as every tab's count line ends: `1475 BCE → 1465 BCE`. */
  def span(from: Option[com.histmersion.api.Years.Astronomical], to: Option[com.histmersion.api.Years.Astronomical]): com.histmersion.api.UiLabels.Text = s"${Fmt.yr(from)} → ${Fmt.yr(to)}"

  /** The count line's element (`.count`; `is-loading` adds `· loading…`, `is-error` colours a failure). `id` keeps the
    * sources tab's `#count`, which the map's tests wait on.
    */
  def countBar(
      text: Signal[com.histmersion.api.UiLabels.Text],
      loading: Signal[com.histmersion.web.ResultsPaging.Loading],
      error: Signal[com.histmersion.web.ResultsPaging.Failed],
      id: Option[String] = None
  ): HtmlElement =
    div(
      id.map(idAttr := _),
      cls := "count",
      cls("is-loading") <-- loading,
      cls("is-error") <-- error,
      child.text <-- text
    )

  /** The button under the rows: `label` (none = no button), disabled and reading `loading…` while a page is on its way.
    */
  def moreButton(label: Signal[Option[com.histmersion.api.UiLabels.Text]], loading: Signal[com.histmersion.web.ResultsPaging.Loading], onMore: () => Unit): HtmlElement =
    div(
      cls := "results-more",
      child <-- label.map(
        _.fold(emptyNode)(l =>
          button(
            typ := "button",
            cls := "btn btn--block more",
            child.text <-- loading.map(b => if b then "loading…" else l),
            disabled <-- loading,
            onClick --> (_ => onMore())
          )
        )
      )
    )
