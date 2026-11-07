package com.histmersion.web

import com.histmersion.api.Paging
import com.raquo.laminar.api.L.*

/** The rows a newest-first list window (jobs, agent runs, approvals) shows, page by page ([[Paging]]: keyset cursors,
  * the total and the per-state counts from the server).
  *
  * How the windows load and refresh, the same in all three:
  *   - opening the window, a filter change and the refresh button START OVER: the newest page;
  *   - "Show older" appends the next page (`before` = the last row's id) and marks the listing as [[Listing.extended]];
  *   - a POLL of an extended listing asks for every row from the newest down to the last one shown (`upTo` = its id)
  *     and replaces the rows with that answer: new rows appear on top, rows that left the filter go, every row is
  *     current, and the bottom of the list — where the person who paged down is reading — stays where it is. A poll of
  *     the first page alone just reloads the newest page (new rows push the oldest of it off; "Show older" brings them
  *     back). The server caps a page at `limits.max-page`: past that, a poll keeps the newest rows up to the cap and
  *     "Show older" goes on from there.
  *
  * Pure, but for [[olderButton]].
  */
object ListPaging:
  /** What is shown: the rows, newest first, and the last page's word on the rest of the list. */
  final case class Listing[A](rows: List[A], page: Paging.Page, extended: Boolean = false):
    def hasOlder: Boolean = page.hasMore

  object Listing:
    def empty[A]: Listing[A] = Listing(Nil, Paging.Page.empty)

  /** The newest page — a first load, a restart, or a poll of a listing that was not extended. */
  def first[A](rows: List[A], page: Paging.Page): Listing[A] = Listing(rows, page)

  /** The answer to "Show older": appended below, any row already shown left out (it can only be there twice if the list
    * changed between the two requests).
    */
  def appended[A](cur: Listing[A], rows: List[A], page: Paging.Page, id: A => String): Listing[A] =
    val shown = cur.rows.iterator.map(id).toSet
    Listing(cur.rows ++ rows.filterNot(r => shown(id(r))), page, extended = true)

  /** The answer to a poll ([[pollUpTo]]): it replaces the rows; still extended if it was. */
  def polled[A](cur: Listing[A], rows: List[A], page: Paging.Page): Listing[A] =
    Listing(rows, page, cur.extended)

  /** A poll's NEWEST page of a list paged by a cursor of its own (a manager's events, `before=<id>`): the fresh rows on
    * top, then the rows already loaded that are older than all of them — so a poll keeps the older pages a person
    * loaded (it used to replace them with the newest page).
    */
  def keepOlder[A](cur: List[A], fresh: List[A], olderThan: (A, A) => Boolean): List[A] =
    fresh.lastOption.fold(fresh)(last => fresh ++ cur.filter(olderThan(_, last)))

  /** What a poll asks for: `upTo` the last row shown when older pages are loaded, else nothing (the newest page).
    */
  def pollUpTo[A](cur: Listing[A], id: A => String): Option[String] =
    if cur.extended then cur.rows.lastOption.map(id) else None

  /** The window's heading: `37 jobs`, `Latest 100 of 2,431 jobs`, `Latest 100 of 412 jobs · Failed`. `l` holds the rows
    * the `state` filter shows, and the total is that state's [[count]] — so the moment a filter changes, before its own
    * page arrives, the heading is already right. `filters` are what the list is narrowed to, as a person reads them
    * (the label dictionary's words, never the identifiers the command line's heading uses).
    */
  def heading[A](
      l: Listing[A],
      singular: com.histmersion.api.Plural.Noun.Singular,
      state: Option[String],
      stateOf: A => String,
      filters: List[String] = Nil
  ): String =
    Paging.heading(Paging.ShownCount(l.rows.size), Paging.Page.Total(count(l, state, stateOf)), singular).value +
      (if filters.isEmpty then "" else filters.mkString(" · ", ", ", ""))

  /** A filter button's count: the server's count of that state over the whole list (`None` = every state); the loaded
    * rows' when the server sent none.
    */
  def count[A](l: Listing[A], state: Option[String], stateOf: A => String): Int =
    if l.page.counts.nonEmpty then state.fold(l.page.all.value)(s => l.page.count(Paging.Page.CountState(s)).value)
    else state.fold(l.rows.size)(s => l.rows.count(stateOf(_) == s))

  /** The "Show older" button under a list: there only while older rows exist, disabled while a page is on its way.
    */
  def olderButton(hasOlder: Signal[Boolean], busy: Signal[Boolean], load: () => Unit): HtmlElement =
    div(
      cls := "list-more",
      cls("is-hidden") <-- hasOlder.map(!_),
      button(
        typ := "button",
        cls := "btn btn--sm",
        disabled <-- busy,
        child.text <-- busy.map(b => if b then "Loading older…" else "Show older"),
        onClick --> (_ => load())
      )
    )
