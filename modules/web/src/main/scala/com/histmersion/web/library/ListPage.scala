package com.histmersion.web.library

/** One page of a Library list as the server answered it: its rows, where it starts, and how many the
  * filter selects in all; `loading` while a newer page is on its way, `error` when the last one failed. */
final case class ListPage[Row](
    rows: List[Row] = Nil,
    offset: Int = 0,
    total: Int = 0,
    loaded: Boolean = false,
    loading: Boolean = false,
    error: Option[String] = None):

  def hasPrevious: Boolean = offset > 0
  def hasNext: Boolean     = offset + rows.size < total

  /** Where the page before and after this one start, for pages of `size` rows. */
  def previousOffset(size: Int): Int = math.max(0, offset - size)
  def nextOffset(size: Int): Int     = offset + size

object ListPage:

  /** Pure: a whole number with its thousands grouped by commas (`1,026`), whatever the browser's locale. */
  def grouped(n: Int): String =
    val digits = math.abs(n.toLong).toString
    val head   = digits.length % 3
    val groups = (if head > 0 then List(digits.take(head)) else Nil) ++ digits.drop(head).grouped(3).toList
    (if n < 0 then "-" else "") + groups.mkString(",")

  /** Pure: the line over the table — `1–50 of 1,026`; `No sources` for an empty answer, `Loading…` before the first. */
  def rangeLine(p: ListPage[?], noun: String): String =
    if !p.loaded then "Loading…"
    else if p.total == 0 || p.rows.isEmpty then s"No ${noun}"
    else s"${grouped(p.offset + 1)}–${grouped(p.offset + p.rows.size)} of ${grouped(p.total)}"
