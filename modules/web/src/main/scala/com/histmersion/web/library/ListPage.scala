package com.histmersion.web.library

/** One page of a Library list as the server answered it: its rows, where it starts, and how many the filter selects in
  * all; `loading` while a newer page is on its way, `error` when the last one failed.
  */
final case class ListPage[Row](
    rows: List[Row] = Nil,
    offset: com.histmersion.api.Offset = com.histmersion.api.Offset(0),
    total: com.histmersion.api.RowCount = com.histmersion.api.RowCount(0),
    loaded: ListPage.Loaded = false,
    loading: ListPage.Loading = false,
    error: Option[ListPage.Error] = None
):

  def hasPrevious: Boolean = offset.value > 0
  def hasNext: Boolean = offset.value + rows.size < total.value

  /** Where the page before and after this one start, for pages of `size` rows. */
  def previousOffset(size: com.histmersion.web.library.ListPage.PreviousOffsetSize): com.histmersion.api.Offset = math.max(0, offset.value - size)
  def nextOffset(size: com.histmersion.web.library.ListPage.NextOffsetSize): com.histmersion.api.Offset = offset.value + size

object ListPage:
  type GroupLabel = GroupLabel.T
  object GroupLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeLabel = RangeLabel.T
  object RangeLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: a whole number with its thousands grouped by commas (`1,026`), whatever the browser's locale. */
  type PreviousOffsetSize = PreviousOffsetSize.T
  object PreviousOffsetSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextOffsetSize = NextOffsetSize.T
  object NextOffsetSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupedN = GroupedN.T
  object GroupedN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeLineNoun = RangeLineNoun.T
  object RangeLineNoun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Loading = Loading.T
  object Loading extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = com.histmersion.web.library.ListPage.Problem
  val Error: _root_.com.histmersion.web.library.ListPage.Problem.type = _root_.com.histmersion.web.library.ListPage.Problem

  def grouped(n: com.histmersion.web.library.ListPage.GroupedN): com.histmersion.web.library.ListPage.GroupLabel =
    val digits = math.abs(n.toLong).toString
    val head = digits.length % 3
    val groups = (if head > 0 then List(digits.take(head)) else Nil) ++ digits.drop(head).grouped(3).toList
    (if n < 0 then "-" else "") + groups.mkString(",")

  /** Pure: the line over the table — `1–50 of 1,026`; `No sources` for an empty answer, `Loading…` before the first. */
  def rangeLine(p: ListPage[?], noun: com.histmersion.web.library.ListPage.RangeLineNoun): com.histmersion.web.library.ListPage.RangeLabel =
    if !p.loaded then "Loading…"
    else if p.total.value == 0 || p.rows.isEmpty then s"No $noun"
    else s"${grouped(p.offset.value + 1)}–${grouped(p.offset.value + p.rows.size)} of ${grouped(p.total.value)}"
