package com.histmersion.api

import sttp.tapir.*

/** OFFSET paging of the lists ordered by something other than time — sources (by relevance), events, goals,
  * attestations, asset links, pictures, the map feed (#1798 item 8). One shape everywhere: the JSON carries `total`
  * (rows matching every filter, ignoring limit and offset), `offset` (where this page starts) and `next` (the offset of
  * the next page, absent on the last one), and the Markdown every MCP tool and CLI command prints heads the list with
  * [[header]] and ends it with [[footer]] — so an agent that sees 50 of 412 events is told to pass `offset=50`. Pure.
  */
final case class OffsetPage(total: Int, offset: Int, shown: Int):
  /** The offset of the next page, or `None` when this one reaches the end. */
  def next: Option[Int] = Option.when(shown > 0 && offset + shown < total)(offset + shown)

  /** Whether this page is the whole list. */
  def isAll: Boolean = offset == 0 && shown >= total

object OffsetPage:
  /** A list asking for at most this many rows is offered the `full set: limit=<total>` hint. */
  val FullSetMax: Int = 500

  /** `### 412 event(s)` when every row is shown, else `### 50 of 412 event(s)`. */
  def header(p: OffsetPage, noun: String): String =
    if p.isAll then s"### ${math.max(p.total, p.shown)} $noun"
    else s"### ${p.shown} of ${p.total} $noun"

  /** `_showing 51–100 of 412 — next: offset=100_` (plus `— full set: limit=<total>` when that is small enough to ask
    * for), or empty when the page is the whole list.
    */
  def footer(p: OffsetPage): String =
    if p.isAll || p.shown == 0 then ""
    else
      val next = p.next.fold("")(n => s" — next: offset=$n")
      val full = if p.total <= FullSetMax then s" — full set: limit=${p.total}" else ""
      s"\n\n_showing ${p.offset + 1}–${p.offset + p.shown} of ${p.total}$next${full}_"

  /** A whole list as Markdown: `empty` when nothing is on this page, else [[header]], the lines and [[footer]].
    */
  def markdown[A](p: OffsetPage, noun: String, empty: String, items: List[A])(line: A => String): String =
    if items.isEmpty then
      p.offset match
        case 0 => empty
        case o => s"$empty\n\n_offset=$o is past the end: there are ${p.total}_"
    else s"${header(p, noun)}\n\n${items.map(line).mkString("\n")}${footer(p)}"
