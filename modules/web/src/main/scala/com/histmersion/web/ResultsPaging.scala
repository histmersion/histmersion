package com.histmersion
package web

import com.histmersion.api.Plural
import com.raquo.laminar.api.L.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Paging
import com.histmersion.api.UiLabels
import com.histmersion.api.RowCount
import com.histmersion.api.catalogue.Year

/** The ONE pagination look of the sidebar's result tabs (sources, articles, events, by time, by region): a count line
  * on top — `showing 1–20 of 285 sources · 491 findings · 1475 BCE → 1465 BCE` — and under the rows one
  * `show 20 more (265 left)` button, which reads `loading…` while the next page is on its way. The sources tab's look
  * is the reference.
  */
object ResultsPaging:
  type Failed = Failed.T
  object Failed extends Newtype[Boolean, Pure]
  type Loading = Loading.T
  object Loading extends Newtype[Boolean, Pure]
  type PageNumber = PageNumber.T
  object PageNumber extends Newtype[Int, Pure]

  /** The count line: `showing 1–shown of total things` while rows are left, `total things` when all are shown,
    * `no things` for none; then the tab's own `extras`, each after a ` · `.
    */

  def countLine(
      shown: Paging.Shown,
      total: Paging.Page.Total,
      singular: Plural.Noun.Singular,
      plural: Plural.Noun.Plural = Plural.Noun.Plural(""),
      extras: List[UiLabels.Text] = Nil
  ): UiLabels.Text =
    val many = if plural.value.nonEmpty then plural.value else singular.value + "s"
    val count = Plural.Count(total.value.toLong)
    val head =
      if total.value <= 0 then s"no $many"
      else if shown.value < total.value then
        s"showing 1–$shown of ${Plural.count(count, singular, Plural.Noun.Plural(many))}"
      else Plural.count(count, singular, Plural.Noun.Plural(many)).value
    UiLabels.Text((head :: extras.map(_.value).filter(_.nonEmpty)).mkString(" · "))

  /** The button's label: `show 20 more (265 left)` — how many the next click brings (at most `page`) and how many are
    * left; `None` when nothing is.
    */
  def moreLabel(left: RowCount, page: PageNumber): Option[UiLabels.Text] =
    Option.when(left.value > 0)(UiLabels.Text(s"show ${math.min(page.value, left.value)} more ($left left)"))

  /** The filter's time window, as every tab's count line ends: `1475 BCE → 1465 BCE`. */
  def span(from: Option[Year], to: Option[Year]): UiLabels.Text = UiLabels.Text(s"${Fmt.yr(from)} → ${Fmt.yr(to)}")

  /** The count line's element (`.count`; `is-loading` adds `· loading…`, `is-error` colours a failure). `id` keeps the
    * sources tab's `#count`, which the map's tests wait on.
    */
  def countBar(
      text: Signal[UiLabels.Text],
      loading: Signal[Loading],
      error: Signal[Failed],
      id: Option[String] = None
  ): HtmlElement =
    div(
      id.map(idAttr := _),
      cls := "count",
      cls("is-loading") <-- loading.map(_.value),
      cls("is-error") <-- error.map(_.value),
      child.text <-- text.map(_.value)
    )

  /** The button under the rows: `label` (none = no button), disabled and reading `loading…` while a page is on its way.
    */
  def moreButton(label: Signal[Option[UiLabels.Text]], loading: Signal[Loading], onMore: () => Unit): HtmlElement =
    div(
      cls := "results-more",
      child <-- label.map(
        _.fold(emptyNode)(l =>
          button(
            typ := "button",
            cls := "btn btn--block more",
            child.text <-- loading.map(b => if b.value then "loading…" else l.value),
            disabled <-- loading.map(_.value),
            onClick --> (_ => onMore())
          )
        )
      )
    )
