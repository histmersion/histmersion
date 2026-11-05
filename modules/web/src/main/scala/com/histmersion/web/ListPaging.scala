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
  type AppendedIdResult = AppendedIdResult.T
  object AppendedIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepOlderOlderThanResult = KeepOlderOlderThanResult.T
  object KeepOlderOlderThanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PollUpToResult = PollUpToResult.T
  object PollUpToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PollUpToIdResult = PollUpToIdResult.T
  object PollUpToIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingResult = HeadingResult.T
  object HeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingSingular = HeadingSingular.T
  object HeadingSingular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingState = HeadingState.T
  object HeadingState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingStateOfResult = HeadingStateOfResult.T
  object HeadingStateOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingFilters = HeadingFilters.T
  object HeadingFilters extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountState = CountState.T
  object CountState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountStateOfResult = CountStateOfResult.T
  object CountStateOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderButtonHasOlder = OlderButtonHasOlder.T
  object OlderButtonHasOlder extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderButtonBusy = OlderButtonBusy.T
  object OlderButtonBusy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Listing[A](rows: List[A], page: Paging.Page, extended: Listing.Extended = false):
    def hasOlder: com.histmersion.web.ListPaging.Listing.HasOlderResult = page.hasMore

  object Listing:
    type HasOlderResult = HasOlderResult.T
    object HasOlderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Extended = Extended.T
    object Extended extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def empty[A]: Listing[A] = Listing(Nil, Paging.Page.empty)

  /** The newest page — a first load, a restart, or a poll of a listing that was not extended. */
  def first[A](rows: List[A], page: Paging.Page): Listing[A] = Listing(rows, page)

  /** The answer to "Show older": appended below, any row already shown left out (it can only be there twice if the list
    * changed between the two requests).
    */
  def appended[A](cur: Listing[A], rows: List[A], page: Paging.Page, id: A => com.histmersion.web.ListPaging.AppendedIdResult): Listing[A] =
    val shown = cur.rows.iterator.map(id).toSet
    Listing(cur.rows ++ rows.filterNot(r => shown(id(r))), page, extended = true)

  /** The answer to a poll ([[pollUpTo]]): it replaces the rows; still extended if it was. */
  def polled[A](cur: Listing[A], rows: List[A], page: Paging.Page): Listing[A] =
    Listing(rows, page, cur.extended)

  /** A poll's NEWEST page of a list paged by a cursor of its own (a manager's events, `before=<id>`): the fresh rows on
    * top, then the rows already loaded that are older than all of them — so a poll keeps the older pages a person
    * loaded (it used to replace them with the newest page).
    */
  def keepOlder[A](cur: List[A], fresh: List[A], olderThan: (A, A) => com.histmersion.web.ListPaging.KeepOlderOlderThanResult): List[A] =
    fresh.lastOption.fold(fresh)(last => fresh ++ cur.filter(olderThan(_, last)))

  /** What a poll asks for: `upTo` the last row shown when older pages are loaded, else nothing (the newest page).
    */
  def pollUpTo[A](cur: Listing[A], id: A => com.histmersion.web.ListPaging.PollUpToIdResult): Option[com.histmersion.web.ListPaging.PollUpToResult] =
    if cur.extended then cur.rows.lastOption.map(id) else None

  /** The window's heading: `37 jobs`, `Latest 100 of 2,431 jobs`, `Latest 100 of 412 jobs · Failed`. `l` holds the rows
    * the `state` filter shows, and the total is that state's [[count]] — so the moment a filter changes, before its own
    * page arrives, the heading is already right. `filters` are what the list is narrowed to, as a person reads them
    * (the label dictionary's words, never the identifiers the command line's heading uses).
    */
  def heading[A](
      l: Listing[A],
      singular: com.histmersion.web.ListPaging.HeadingSingular,
      state: Option[com.histmersion.web.ListPaging.HeadingState],
      stateOf: A => com.histmersion.web.ListPaging.HeadingStateOfResult,
      filters: List[com.histmersion.web.ListPaging.HeadingFilters] = Nil
  ): com.histmersion.web.ListPaging.HeadingResult =
    Paging.heading(l.rows.size, count(l, state, stateOf), singular) +
      (if filters.isEmpty then "" else filters.mkString(" · ", ", ", ""))

  /** A filter button's count: the server's count of that state over the whole list (`None` = every state); the loaded
    * rows' when the server sent none.
    */
  def count[A](l: Listing[A], state: Option[com.histmersion.web.ListPaging.CountState], stateOf: A => com.histmersion.web.ListPaging.CountStateOfResult): com.histmersion.web.ListPaging.CountResult =
    if l.page.counts.nonEmpty then state.fold(l.page.all)(l.page.count)
    else state.fold(l.rows.size)(s => l.rows.count(stateOf(_) == s))

  /** The "Show older" button under a list: there only while older rows exist, disabled while a page is on its way.
    */
  def olderButton(hasOlder: Signal[com.histmersion.web.ListPaging.OlderButtonHasOlder], busy: Signal[com.histmersion.web.ListPaging.OlderButtonBusy], load: () => Unit): HtmlElement =
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
