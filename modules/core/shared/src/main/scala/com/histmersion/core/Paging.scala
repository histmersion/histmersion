package com.histmersion.core

import sttp.tapir.*

/** Keyset paging of the newest-first lists — background jobs, agent runs, approvals.
  *
  * A list is ordered by `created_at` descending, ties broken by `id` descending, so every row has one
  * fixed place in it. A page is the newest `limit` rows; the next older page is asked for with
  * `before=<the id of the last row shown>` — the cursor IS a row's id (the server looks its
  * `created_at` up, at full precision), so it is exact, and a person can type one from a listing. The
  * web windows also refresh with `upTo=<id>`: every row from the newest down to and including that
  * one, which keeps the rows a person has scrolled to while new ones arrive on top.
  *
  * What a page says about the rest of the list crosses in RESPONSE HEADERS, so the JSON body stays
  * the plain array it always was (older CLIs and MCP clients read it unchanged):
  * [[TotalHeader]] — the rows matching every filter; [[NextHeader]] — the cursor of the next older
  * page, absent when there is none; [[CountsHeader]] — the rows in each state, over every other
  * filter (a goal) but NOT the state filter, for the filter buttons. Pure. */
object Paging:

  val TotalHeader  = "X-Total-Count"
  val NextHeader   = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  /** Where a page sits in its list. `next` = the cursor for the next older page (None: nothing older);
    * `counts` = how many rows each state holds (states with none are left out). */
  final case class Page(total: Int, next: Option[String], counts: List[(String, Int)]):
    def hasMore: Boolean          = next.isDefined
    def count(state: String): Int = counts.collectFirst { case (s, n) if s == state => n }.getOrElse(0)
    /** Every row of the list, whatever its state. */
    def all: Int = counts.map(_._2).sum

  object Page:
    val empty: Page = Page(0, None, Nil)

    /** A page from the per-state `counts`, the `state` filter it was read under (None: every state)
      * and the id of its last row when more lie beyond it. */
    def of(counts: List[(String, Int)], state: Option[String], last: Option[String]): Page =
      val total = state.fold(counts.map(_._2).sum)(s => counts.collectFirst { case (k, n) if k == s => n }.getOrElse(0))
      Page(total, last, counts)

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

  /** The three headers as one output. A server that predates them (no headers) reads as "the page is
    * the whole list": total 0, no next page, no counts — [[heading]] then counts the rows shown. */
  val headers: EndpointIO[Page] =
    header[Option[Int]](TotalHeader)
      .and(header[Option[String]](NextHeader))
      .and(header[Option[String]](CountsHeader))
      .map((t, n, c) => Page(t.getOrElse(0), n.map(_.trim).filter(_.nonEmpty), c.fold(Nil)(decodeCounts)))(p =>
        (Some(p.total), p.next, Some(encodeCounts(p.counts))))

  /** `2,431` — grouped by hand, never through a locale (JDK 25's `en_PL` groups with a space). */
  def grouped(n: Long): String =
    val s = math.abs(n).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if n < 0 then s"-$g" else g

  /** What a list window or listing heads itself with: `37 jobs` when every row is shown, else
    * `Latest 100 of 2,431 jobs`; `qualifier` names the state filter (`Latest 100 of 37 failed jobs`).
    * `total` below `shown` (a server without the headers) counts as `shown`. */
  def heading(shown: Int, total: Int, singular: String, plural: String = "", qualifier: Option[String] = None): String =
    val all  = math.max(shown, total)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(all, singular, plural)
    if shown >= all then s"${grouped(all)} $noun"
    else s"Latest ${grouped(shown)} of ${grouped(all)} $noun"

  /** The line under a truncated listing that says how to ask for the next older page. */
  def olderLine(page: Page, ask: String => String): Option[String] =
    page.next.map(c => s"Older: ${ask(c)}")

  /** Split `limit + 1` fetched rows into the page and whether anything lies beyond it. */
  def cut[A](rows: Vector[A], limit: Int): (Vector[A], Boolean) =
    if rows.size > limit then (rows.take(limit), true) else (rows, false)
