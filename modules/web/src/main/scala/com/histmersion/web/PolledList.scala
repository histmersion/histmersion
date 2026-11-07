package com.histmersion.web

import com.histmersion.api.Paging
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** ONE controller for the newest-first list windows — jobs, agent runs, approvals (#1798 item 6): the rows
  * ([[ListPaging]]: keyset pages, "Show older", a poll that keeps the pages loaded), the poll ([[Poll]], none for a
  * list with nothing `live`), the `mounted` guard (an answer that lands after the window closed is dropped), the
  * restart when the last row shown is gone, and one error line.
  *
  * @param fetch
  *   a page: `upTo` (a poll of an extended listing) or `before` (the next older page)
  * @param live
  *   whether anything is still moving — `_ => false` for a list that is not polled
  * @param after
  *   called with the listing before and the rows of each successful poll (jobs: an OCR job that ticked an acquisition
  *   item off reloads that list)
  * @param later
  *   `setTimeout`, injectable for a test; `cancel` its `clearTimeout`
  */
final class PolledList[A](
    fetch: (Option[PolledList.FetchArg1], Option[PolledList.FetchArg2]) => Future[(List[A], Paging.Page)],
    id: A => PolledList.IdResult,
    live: (List[A], Paging.Page) => PolledList.LiveResult,
    after: (ListPaging.Listing[A], List[A]) => Unit = (_: ListPaging.Listing[A], _: List[A]) => (),
    later: (PolledList.LaterArg1, () => Unit) => PolledList.LaterResult = (ms, f) => dom.window.setTimeout(() => f(), ms),
    cancel: PolledList.CancelArg1 => Unit = id => dom.window.clearTimeout(id)
)(using ExecutionContext):

  val listing: Var[ListPaging.Listing[A]] = Var(ListPaging.Listing.empty[A])
  val loaded: Var[com.histmersion.web.PolledList.Loaded] = Var(com.histmersion.web.PolledList.Loaded(false))
  val older: Var[com.histmersion.web.PolledList.Older] = Var(com.histmersion.web.PolledList.Older(false))
  val failure: Var[Option[com.histmersion.web.PolledList.Problem]] = Var(None)

  /** A restart (a filter changed) is on its way: the rows shown are the previous filter's until it lands. */
  private val restarting: Var[com.histmersion.web.PolledList.Restarting] = Var(com.histmersion.web.PolledList.Restarting(false))
  def loading: Signal[Boolean] = restarting.signal
  private val latest = Latest()
  // `var`s: the one pending poll, its delay, and whether the window is there to be refreshed
  private var timer: Int = 0
  private var delay: Option[Int] = None
  private var mounted: Boolean = false

  /** The window opened: load the newest page (and poll while anything is live). */
  def open(): Unit = { mounted = true; loaded.set(com.histmersion.web.PolledList.Loaded(false)); restart() }

  /** The window closed: no poll, and an answer still on its way is dropped. */
  def close(): Unit = { mounted = false; cancel(timer); delay = None; latest.cancel(); restarting.set(com.histmersion.web.PolledList.Restarting(false)) }

  /** The two lifecycle hooks, for the window's root element. */
  def lifecycle: Modifier[HtmlElement] = List(onMountCallback(_ => open()), onUnmountCallback(_ => close()))

  /** Start over from the newest page: the window opened, a filter changed, or "refresh". */
  def restart(): Unit = load(reset = com.histmersion.web.PolledList.Reset(true), changed = com.histmersion.web.Poll.DataChanged(true))

  /** A poll — or an outside change (a decision, a job started here): the rows shown, current again. */
  def refresh(changed: com.histmersion.web.Poll.DataChanged = com.histmersion.web.Poll.DataChanged(false)): Unit = load(reset = com.histmersion.web.PolledList.Reset(false), changed)

  private def load(reset: com.histmersion.web.PolledList.Reset, changed: com.histmersion.web.Poll.DataChanged): Unit =
    if mounted then
      cancel(timer)
      val cur = listing.now()
      val upTo = if reset then None else ListPaging.pollUpTo(cur, id)
      older.set(com.histmersion.web.PolledList.Older(false))
      if reset then restarting.set(com.histmersion.web.PolledList.Restarting(true))
      val ticket = latest.start()
      fetch(upTo, None).onComplete(r =>
        if mounted && latest.isCurrent(ticket) then answered(r, cur, upTo, reset, changed)
      )

  private[web] def answered(
      r: Try[(List[A], Paging.Page)],
      cur: ListPaging.Listing[A],
      upTo: Option[com.histmersion.api.Paging.Cursor],
      reset: com.histmersion.web.PolledList.Reset,
      changed: com.histmersion.web.Poll.DataChanged
  ): Unit =
    restarting.set(com.histmersion.web.PolledList.Restarting(false))
    r match
      // the last row shown is gone (the server cannot page from there): start over
      case Failure(_) if upTo.isDefined => restart()
      case Failure(e)                   =>
        failure.set(Some(e.getMessage))
        // keep trying while the window is open, at the slowest pace
        schedule(Some(Poll.MaxMs))
      case Success((rows, page)) =>
        val next = if upTo.isEmpty then ListPaging.first(rows, page) else ListPaging.polled(cur, rows, page)
        val moved = changed || next != cur
        if !reset && loaded.now() then after(cur, rows)
        failure.set(None)
        listing.set(next)
        loaded.set(com.histmersion.web.PolledList.Loaded(true))
        schedule(Poll.next(delay, live(rows, page), moved))

  /** "Show older": the next page, appended. A poll on its way is superseded (and rescheduled here). */
  def showOlder(): Unit =
    listing.now().page.next.foreach { cursor =>
      if mounted then
        cancel(timer)
        older.set(com.histmersion.web.PolledList.Older(true))
        val ticket = latest.start()
        fetch(None, Some(cursor)).onComplete { r =>
          if mounted && latest.isCurrent(ticket) then
            older.set(com.histmersion.web.PolledList.Older(false))
            r match
              case Failure(e)            => failure.set(Some(e.getMessage)); schedule(Some(Poll.MaxMs))
              case Success((rows, page)) =>
                failure.set(None)
                listing.update(ListPaging.appended(_, rows, page, id))
                schedule(Poll.next(delay, live(listing.now().rows, page), changed = com.histmersion.web.Poll.DataChanged(false)))
        }
    }

  private def schedule(next: Option[Int]): Unit =
    delay = next
    next.foreach(ms => timer = later(ms, () => refresh()))

  /** The one item of a [[PolledList.single]]: `None` until it has loaded. */
  def item: Signal[Option[A]] = listing.signal.map(_.rows.headOption)

  /** The "Show older" button under the list. */
  def olderButton: HtmlElement = ListPaging.olderButton(listing.signal.map(_.hasOlder), older.signal, () => showOlder())

  /** The error line: what the last request answered, `what` naming the list. */
  def errorLine(what: com.histmersion.api.UiLabels.Text): HtmlElement =
    div(child.maybe <-- failure.signal.map(_.map(m => ErrorLine.text(s"$what: $m"))))

object PolledList:
  type ItemId = ItemId.T
  object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reset = Reset.T
  object Reset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** ONE polled item — an agent run's window (#1946): a one-row [[PolledList]], so it shares the schedule, the
    * `mounted` guard, the stale-answer guard and the error line with the list windows; it has no pages, so it never
    * shows older rows. `after` gets the item before and after each poll (not the first load).
    */
  type FetchArg1 = com.histmersion.api.Paging.Cursor
  val FetchArg1: _root_.com.histmersion.api.Paging.Cursor.type = _root_.com.histmersion.api.Paging.Cursor
  type FetchArg2 = com.histmersion.api.Paging.Cursor
  val FetchArg2: _root_.com.histmersion.api.Paging.Cursor.type = _root_.com.histmersion.api.Paging.Cursor
  type IdResult = com.histmersion.web.PolledList.ItemId
  val IdResult: _root_.com.histmersion.web.PolledList.ItemId.type = _root_.com.histmersion.web.PolledList.ItemId
  type LiveResult = LiveResult.T
  object LiveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaterArg1 = LaterArg1.T
  object LaterArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaterResult = LaterResult.T
  object LaterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelArg1 = CancelArg1.T
  object CancelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Older = Older.T
  object Older extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Restarting = Restarting.T
  object Restarting extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def single[A](
      fetch: () => Future[A],
      live: A => Boolean,
      after: (Option[A], A) => Unit = (_: Option[A], _: A) => (),
      later: (Int, () => Unit) => Int = (ms, f) => dom.window.setTimeout(() => f(), ms),
      cancel: Int => Unit = id => dom.window.clearTimeout(id)
  )(using ExecutionContext): PolledList[A] =
    PolledList[A](
      fetch = (_, _) => fetch().map(a => (List(a), Paging.Page.empty)),
      id = _ => com.histmersion.web.PolledList.IdResult(""),
      live = (rows, _) => rows.exists(live),
      after = (cur, rows) => rows.headOption.foreach(after(cur.rows.headOption, _)),
      later = later,
      cancel = cancel
    )
