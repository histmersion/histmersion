package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** What a filter-driven list tab shows: the rows loaded from the server (a page at a time), how many the list reveals,
  * the server's total, the count line's extras, and whether a request is out or failed. Pure.
  */
final case class TabState[Row](
    rows: List[Row] = Nil,
    total: com.histmersion.api.RowCount = com.histmersion.api.RowCount(0),
    shown: com.histmersion.api.RowCount = com.histmersion.api.RowCount(0),
    loading: TabState.Loading = false,
    error: Option[TabState.Error] = None,
    loaded: TabState.Loaded = false,
    extras: List[TabState.Extras] = Nil
):

  /** Rows the list does not show yet — loaded or still on the server. */
  def left: com.histmersion.web.TabState.LeftResult = math.max(0, math.max(total.value, rows.size) - shown.value)

  /** The "show more" label ([[ResultsPaging.moreLabel]]); `None` when every row is shown. */
  def moreLabel(page: com.histmersion.web.TabState.MoreLabelPage): Option[com.histmersion.web.TabState.MoreLabelResult] = ResultsPaging.moreLabel(left, page)

  /** The count line ([[ResultsPaging.countLine]]); `loading…` before the first answer. */
  def countLine(singular: com.histmersion.web.TabState.CountLineSingular, plural: com.histmersion.web.TabState.CountLinePlural): com.histmersion.web.TabState.CountLineResult =
    if !loaded then "loading…"
    else ResultsPaging.countLine(shown.value, math.max(total.value, rows.size), singular, plural, extras)

object TabState:
  /** A new query was sent: what is shown stays until the answer comes; any old error is gone. */
  type LeftResult = LeftResult.T
  object LeftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreLabelResult = MoreLabelResult.T
  object MoreLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreLabelPage = MoreLabelPage.T
  object MoreLabelPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineResult = CountLineResult.T
  object CountLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLineSingular = CountLineSingular.T
  object CountLineSingular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountLinePlural = CountLinePlural.T
  object CountLinePlural extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FirstPage = FirstPage.T
  object FirstPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppendedPage = AppendedPage.T
  object AppendedPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailedMessage = FailedMessage.T
  object FailedMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoreResultPart1 = MoreResultPart1.T
  object MoreResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MorePage = MorePage.T
  object MorePage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Loading = Loading.T
  object Loading extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Extras = Extras.T
  object Extras extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def loading[Row](s: TabState[Row]): TabState[Row] = s.copy(loading = true, error = None)

  /** The first page of a query's answer. */
  def first[Row](p: TabPage[Row], page: com.histmersion.web.TabState.FirstPage): TabState[Row] =
    TabState(p.rows, p.total, com.histmersion.api.RowCount(math.min(page, p.rows.size)), loaded = true, extras = p.extras)

  /** A further page from the server, appended (what "show more" asked for once the loaded rows ran out). */
  def appended[Row](s: TabState[Row], p: TabPage[Row], page: com.histmersion.web.TabState.AppendedPage): TabState[Row] =
    val rows = s.rows ++ p.rows
    s.copy(
      rows = rows,
      total = p.total,
      shown = com.histmersion.api.RowCount(math.min(s.shown.value + page, rows.size)),
      loading = false,
      error = None,
      extras = p.extras
    )

  /** A request failed: loading ends, the error is shown, the rows already there stay. */
  def failed[Row](s: TabState[Row], message: com.histmersion.web.TabState.FailedMessage): TabState[Row] =
    s.copy(loading = false, error = Some(message), loaded = true)

  /** "show more": reveal the next `page` loaded rows, or — when the loaded rows run out before the total —
    * `Left(offset)`, the next page to ask the server for (rows past the first fetch used to be unreachable).
    */
  def more[Row](s: TabState[Row], page: com.histmersion.web.TabState.MorePage): Either[com.histmersion.web.TabState.MoreResultPart1, TabState[Row]] =
    if s.shown.value + page <= s.rows.size || s.rows.size >= s.total.value then
      Right(s.copy(shown = com.histmersion.api.RowCount(math.min(s.shown.value + page, s.rows.size))))
    else Left(s.rows.size)
