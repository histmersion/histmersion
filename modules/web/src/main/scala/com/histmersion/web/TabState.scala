package com.histmersion
package web
import com.histmersion.util.problem.Problem

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import com.histmersion.api.RowCount
import com.histmersion.api.UiLabels
import com.histmersion.api.Plural
import com.histmersion.api.Paging
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** What a filter-driven list tab shows: the rows loaded from the server (a page at a time), how many the list reveals,
  * the server's total, the count line's extras, and whether a request is out or failed. Pure.
  */
final case class TabState[Row](
    rows: List[Row] = Nil,
    total: RowCount = RowCount(0),
    shown: RowCount = RowCount(0),
    loading: TabState.Loading = TabState.Loading(false),
    error: Option[Problem] = None,
    loaded: TabState.Loaded = TabState.Loaded(false),
    extras: List[TabState.Extras] = Nil
):

  /** Rows the list does not show yet — loaded or still on the server. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def left: RowCount = RowCount(math.max(0, math.max(total.value, rows.size) - shown.value))

  /** The "show more" label ([[ResultsPaging.moreLabel]]); `None` when every row is shown. */
  def moreLabel(page: ResultsPaging.PageNumber): Option[UiLabels.Text] = ResultsPaging.moreLabel(left, page)

  /** The count line ([[ResultsPaging.countLine]]); `loading…` before the first answer. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def countLine(singular: Plural.Noun.Singular, plural: Plural.Noun.Plural): UiLabels.Text =
    if !loaded.value then UiLabels.Text("loading…")
    else
      ResultsPaging.countLine(
        Paging.Shown(shown.value),
        Paging.Page.Total(math.max(total.value, rows.size)),
        singular,
        plural,
        extras.map(e => UiLabels.Text(e.value))
      )

object TabState:

  /** A new query was sent: what is shown stays until the answer comes; any old error is gone. */

  type Loading = Loading.T
  object Loading extends Newtype[Boolean, Pure]
  type Loaded = Loaded.T
  object Loaded extends Newtype[Boolean, Pure]
  export TabPage.Extras

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  def loading[Row](s: TabState[Row]): TabState[Row] = s.copy(loading = Loading(true), error = None)

  /** The first page of a query's answer. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def first[Row](p: TabPage[Row], page: ResultsPaging.PageNumber): TabState[Row] =
    TabState(p.rows, p.total, RowCount(math.min(page.value, p.rows.size)), loaded = Loaded(true), extras = p.extras)

  /** A further page from the server, appended (what "show more" asked for once the loaded rows ran out). */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def appended[Row](s: TabState[Row], p: TabPage[Row], page: ResultsPaging.PageNumber): TabState[Row] =
    val rows = s.rows ++ p.rows
    s.copy(
      rows = rows,
      total = p.total,
      shown = RowCount(math.min(s.shown.value + page.value, rows.size)),
      loading = Loading(false),
      error = None,
      extras = p.extras
    )

  /** A request failed: loading ends, the error is shown, the rows already there stay. */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  def failed[Row](s: TabState[Row], message: Problem): TabState[Row] =
    s.copy(loading = Loading(false), error = Some(message), loaded = Loaded(true))

  /** "show more": reveal the next `page` loaded rows, or — when the loaded rows run out before the total —
    * `Left(offset)`, the next page to ask the server for (rows past the first fetch used to be unreachable).
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def more[Row](s: TabState[Row], page: ResultsPaging.PageNumber): Either[ResultsPaging.PageNumber, TabState[Row]] =
    if s.shown.value + page.value <= s.rows.size || s.rows.size >= s.total.value then
      Right(s.copy(shown = RowCount(math.min(s.shown.value + page.value, s.rows.size))))
    else Left(ResultsPaging.PageNumber(s.rows.size))
