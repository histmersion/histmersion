package com.histmersion.web

import com.histmersion.api.Plural
import com.raquo.laminar.api.L.*

/** The ONE pagination look of the sidebar's result tabs (sources, articles, events, by time, by region): a count line
  * on top — `showing 1–20 of 285 sources · 491 findings · 1475 BCE → 1465 BCE` — and under the rows one
  * `show 20 more (265 left)` button, which reads `loading…` while the next page is on its way. The sources tab's look
  * is the reference.
  */
object ResultsPaging:

  /** The count line: `showing 1–shown of total things` while rows are left, `total things` when all are shown,
    * `no things` for none; then the tab's own `extras`, each after a ` · `.
    */
  type CountLineResult = CountLineResult.T
  object CountLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineShown = CountLineShown.T
  object CountLineShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineTotal = CountLineTotal.T
  object CountLineTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineSingular = CountLineSingular.T
  object CountLineSingular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLinePlural = CountLinePlural.T
  object CountLinePlural extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineExtras = CountLineExtras.T
  object CountLineExtras extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreLabelResult = MoreLabelResult.T
  object MoreLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreLabelLeft = MoreLabelLeft.T
  object MoreLabelLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreLabelPage = MoreLabelPage.T
  object MoreLabelPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanResult = SpanResult.T
  object SpanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanFrom = SpanFrom.T
  object SpanFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanTo = SpanTo.T
  object SpanTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountBarText = CountBarText.T
  object CountBarText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountBarLoading = CountBarLoading.T
  object CountBarLoading extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountBarError = CountBarError.T
  object CountBarError extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountBarId = CountBarId.T
  object CountBarId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreButtonLabel = MoreButtonLabel.T
  object MoreButtonLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreButtonLoading = MoreButtonLoading.T
  object MoreButtonLoading extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def countLine(shown: com.histmersion.web.ResultsPaging.CountLineShown, total: com.histmersion.web.ResultsPaging.CountLineTotal, singular: com.histmersion.web.ResultsPaging.CountLineSingular, plural: com.histmersion.web.ResultsPaging.CountLinePlural = "", extras: List[com.histmersion.web.ResultsPaging.CountLineExtras] = Nil): com.histmersion.web.ResultsPaging.CountLineResult =
    val many = if plural.nonEmpty then plural else singular + "s"
    val head =
      if total <= 0 then s"no $many"
      else if shown < total then s"showing 1–$shown of ${Plural.count(total.toLong, singular, many)}"
      else Plural.count(total.toLong, singular, many)
    (head :: extras.filter(_.nonEmpty)).mkString(" · ")

  /** The button's label: `show 20 more (265 left)` — how many the next click brings (at most `page`) and how many are
    * left; `None` when nothing is.
    */
  def moreLabel(left: com.histmersion.web.ResultsPaging.MoreLabelLeft, page: com.histmersion.web.ResultsPaging.MoreLabelPage): Option[com.histmersion.web.ResultsPaging.MoreLabelResult] =
    Option.when(left > 0)(s"show ${math.min(page, left)} more ($left left)")

  /** The filter's time window, as every tab's count line ends: `1475 BCE → 1465 BCE`. */
  def span(from: Option[com.histmersion.web.ResultsPaging.SpanFrom], to: Option[com.histmersion.web.ResultsPaging.SpanTo]): com.histmersion.web.ResultsPaging.SpanResult = s"${Fmt.yr(from)} → ${Fmt.yr(to)}"

  /** The count line's element (`.count`; `is-loading` adds `· loading…`, `is-error` colours a failure). `id` keeps the
    * sources tab's `#count`, which the map's tests wait on.
    */
  def countBar(
      text: Signal[com.histmersion.web.ResultsPaging.CountBarText],
      loading: Signal[com.histmersion.web.ResultsPaging.CountBarLoading],
      error: Signal[com.histmersion.web.ResultsPaging.CountBarError],
      id: Option[com.histmersion.web.ResultsPaging.CountBarId] = None
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
  def moreButton(label: Signal[Option[com.histmersion.web.ResultsPaging.MoreButtonLabel]], loading: Signal[com.histmersion.web.ResultsPaging.MoreButtonLoading], onMore: () => Unit): HtmlElement =
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
