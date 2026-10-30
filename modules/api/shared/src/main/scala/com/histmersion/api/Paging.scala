package com.histmersion.api

import sttp.tapir.*

/** Keyset paging of the newest-first lists — background jobs, agent runs, approvals.
  *
  * A list is ordered by `created_at` descending, ties broken by `id` descending, so every row has one fixed place in
  * it. A page is the newest `limit` rows; the next older page is asked for with `before=<the id of the last row shown>`
  * — the cursor IS a row's id (the server looks its `created_at` up, at full precision), so it is exact, and a person
  * can type one from a listing. The web windows also refresh with `upTo=<id>`: every row from the newest down to and
  * including that one, which keeps the rows a person has scrolled to while new ones arrive on top.
  *
  * What a page says about the rest of the list crosses in RESPONSE HEADERS, so the JSON body stays the plain array it
  * always was (older CLIs and MCP clients read it unchanged): [[TotalHeader]] — the rows matching every filter;
  * [[NextHeader]] — the cursor of the next older page, absent when there is none; [[CountsHeader]] — the rows in each
  * state, over every other filter (a goal) but NOT the state filter, for the filter buttons. Pure.
  */
object Paging:

  val TotalHeader = "X-Total-Count"
  val NextHeader = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  /** Where a page sits in its list. `next` = the cursor for the next older page (None: nothing older); `counts` = how
    * many rows each state holds (states with none are left out).
    */
  final case class Page(
      total: Int,
      next: Option[String],
      counts: List[(String, Int)],
      facets: List[(String, List[(String, Int)])] = Nil
  ):
    def hasMore: Boolean = next.isDefined
    def count(state: String): Int = counts.collectFirst { case (s, n) if s == state => n }.getOrElse(0)

    /** Every row of the list, whatever its state. */
    def all: Int = counts.map(_._2).sum

    /** One facet's counts ([[ListFilters]]: over every other filter), largest first; Nil when not sent. */
    def facet(name: String): List[(String, Int)] = facets.collectFirst { case (`name`, vs) => vs }.getOrElse(Nil)

  object Page:
    val empty: Page = Page(0, None, Nil)

    /** A page from the per-state `counts`, the `state` filter it was read under (None: every state) and the id of its
      * last row when more lie beyond it.
      */
    def of(counts: List[(String, Int)], state: Option[String], last: Option[String]): Page =
      filtered(counts, state.toList, last)

    /** [[of]] for a filter on ANY of `states` (none: every state), with the [[ListFilters]] facets. */
    def filtered(
        counts: List[(String, Int)],
        states: List[String],
        last: Option[String],
        facets: List[(String, List[(String, Int)])] = Nil
    ): Page =
      val total =
        if states.isEmpty then counts.map(_._2).sum else counts.collect { case (k, n) if states.contains(k) => n }.sum
      Page(total, last, counts, facets.filter(_._2.nonEmpty))

  /** `done=12,failed=3` — state names are plain words, never holding `,` or `=`. */
  def encodeCounts(counts: List[(String, Int)]): String =
    counts.map((s, n) => s"$s=$n").mkString(",")

  /** The inverse of [[encodeCounts]]; a malformed pair is skipped, not fatal. */
  def decodeCounts(s: String): List[(String, Int)] =
    s.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
      pair.split('=') match
        case Array(k, v) => v.trim.toIntOption.map(k.trim -> _)
        case _           => None
    }

  /** The headers as one output (with [[ListFilters.FacetsHeader]], the facet counts). A server that predates them (no
    * headers) reads as "the page is the whole list": total 0, no next page, no counts — [[heading]] then counts the
    * rows shown.
    */
  val headers: EndpointIO[Page] =
    header[Option[Int]](TotalHeader)
      .and(header[Option[String]](NextHeader))
      .and(header[Option[String]](CountsHeader))
      .and(header[Option[String]](ListFilters.FacetsHeader))
      .map((t, n, c, f) =>
        Page(
          t.getOrElse(0),
          n.map(_.trim).filter(_.nonEmpty),
          c.fold(Nil)(decodeCounts),
          f.fold(Nil)(ListFilters.decodeFacets)
        )
      )(p =>
        (
          Some(p.total),
          p.next,
          Some(encodeCounts(p.counts)).filter(_.nonEmpty),
          Some(ListFilters.encodeFacets(p.facets)).filter(_.nonEmpty)
        )
      )

  /** `2,431` — grouped by hand, never through a locale (JDK 25's `en_PL` groups with a space). */
  def grouped(n: Long): String =
    val s = math.abs(n).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if n < 0 then s"-$g" else g

  /** What a list window or listing heads itself with: `37 jobs` when every row is shown, else
    * `Latest 100 of 2,431 jobs`; `qualifier` names the state filter (`Latest 100 of 37 failed jobs`). `total` below
    * `shown` (a server without the headers) counts as `shown`.
    */
  def heading(shown: Int, total: Int, singular: String, plural: String = "", qualifier: Option[String] = None): String =
    val all = math.max(shown, total)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(all, singular, plural)
    if shown >= all then s"${grouped(all)} $noun"
    else s"Latest ${grouped(shown)} of ${grouped(all)} $noun"

  /** The line under a truncated listing that says how to ask for the next older page. */
  def olderLine(page: Page, ask: String => String): Option[String] =
    page.next.map(c => s"Older: ${ask(c)}")

  /** Split `limit + 1` fetched rows into the page and whether anything lies beyond it. */
  def cut[A](rows: Vector[A], limit: Int): (Vector[A], Boolean) =
    if rows.size > limit then (rows.take(limit), true) else (rows, false)

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
