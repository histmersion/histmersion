package com.histmersion
package web

import com.histmersion.api.Paging
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.constraint.any.Pure

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
    fetch: (Option[Paging.Cursor], Option[Paging.Cursor]) => Future[(List[A], Paging.Page)],
    id: A => PolledList.ItemId,
    live: (List[A], Paging.Page) => Boolean,
    after: (ListPaging.Listing[A], List[A]) => Unit = (_: ListPaging.Listing[A], _: List[A]) => (),
    later: (Poll.Revision, () => Unit) => PolledList.TimerId = (ms, f) => PolledList.TimerId(dom.window.setTimeout(() => f(), ms.value)),
    cancel: PolledList.TimerId => Unit = id => dom.window.clearTimeout(id.value)
)(using ExecutionContext, CanEqual[A, A]):

  val listing: Var[ListPaging.Listing[A]] = Var(ListPaging.Listing.empty[A])
  val loaded: Var[PolledList.Loaded] = Var(PolledList.Loaded(false))
  val older: Var[PolledList.Older] = Var(PolledList.Older(false))
  val failure: Var[Option[PolledList.Problem]] = Var(None)

  /** A restart (a filter changed) is on its way: the rows shown are the previous filter's until it lands. */
  private val restarting: Var[Boolean] = Var(false)
  def loading: Signal[Boolean] = restarting.signal
  private val latest = Latest()
  // `var`s: the one pending poll, its delay, and whether the window is there to be refreshed
  private var timer: PolledList.TimerId = PolledList.TimerId(0)
  private var delay: Option[Poll.Revision] = None
  private var mounted: Boolean = false

  /** The window opened: load the newest page (and poll while anything is live). */
  def open(): Unit = { mounted = true; loaded.set(PolledList.Loaded(false)); restart() }

  /** The window closed: no poll, and an answer still on its way is dropped. */
  def close(): Unit = { mounted = false; cancel(timer); delay = None; latest.cancel(); restarting.set(false) }

  /** The two lifecycle hooks, for the window's root element. */
  def lifecycle: Modifier[HtmlElement] = List(onMountCallback(_ => open()), onUnmountCallback(_ => close()))

  /** Start over from the newest page: the window opened, a filter changed, or "refresh". */
  def restart(): Unit = load(reset = true, changed = Poll.DataChanged(true))

  /** A poll — or an outside change (a decision, a job started here): the rows shown, current again. */
  def refresh(changed: Poll.DataChanged = Poll.DataChanged(false)): Unit = load(reset = false, changed)

  private def load(reset: Boolean, changed: Poll.DataChanged): Unit =
    if mounted then
      cancel(timer)
      val cur = listing.now()
      val upTo = if reset then None else ListPaging.pollUpTo(cur, a => id(a).value).map(Paging.Cursor(_))
      older.set(PolledList.Older(false))
      if reset then restarting.set(true)
      val ticket = latest.start()
      fetch(upTo, None).onComplete(r =>
        if mounted && latest.isCurrent(ticket) then answered(r, cur, upTo, reset, changed)
      )

  private[web] def answered(
      r: Try[(List[A], Paging.Page)],
      cur: ListPaging.Listing[A],
      upTo: Option[Paging.Cursor],
      reset: Boolean,
      changed: Poll.DataChanged
  ): Unit =
    restarting.set(false)
    r match
      // the last row shown is gone (the server cannot page from there): start over
      case Failure(_) if upTo.isDefined => restart()
      case Failure(e)                   =>
        failure.set(Some(PolledList.Problem(e.getMessage)))
        // keep trying while the window is open, at the slowest pace
        schedule(Some(Poll.Revision(Poll.MaxMs)))
      case Success((rows, page)) =>
        val next = if upTo.isEmpty then ListPaging.first(rows, page) else ListPaging.polled(cur, rows, page)
        val moved = changed.value || next != cur
        if !reset && loaded.now().value then after(cur, rows)
        failure.set(None)
        listing.set(next)
        loaded.set(PolledList.Loaded(true))
        schedule(Poll.next(delay, Poll.LiveMode(live(rows, page)), Poll.DataChanged(moved)))

  /** "Show older": the next page, appended. A poll on its way is superseded (and rescheduled here). */
  def showOlder(): Unit =
    listing.now().page.next.foreach { cursor =>
      if mounted then
        cancel(timer)
        older.set(PolledList.Older(true))
        val ticket = latest.start()
        fetch(None, Some(cursor)).onComplete { r =>
          if mounted && latest.isCurrent(ticket) then
            older.set(PolledList.Older(false))
            r match
              case Failure(e)            => failure.set(Some(PolledList.Problem(e.getMessage))); schedule(Some(Poll.Revision(Poll.MaxMs)))
              case Success((rows, page)) =>
                failure.set(None)
                listing.update(ListPaging.appended(_, rows, page, a => id(a).value))
                schedule(Poll.next(delay, Poll.LiveMode(live(listing.now().rows, page)), changed = Poll.DataChanged(false)))
        }
    }

  private def schedule(next: Option[Poll.Revision]): Unit =
    delay = next
    next.foreach(ms => timer = later(ms, () => refresh()))

  /** The one item of a [[PolledList.single]]: `None` until it has loaded. */
  def item: Signal[Option[A]] = listing.signal.map(_.rows.headOption)

  /** The "Show older" button under the list. */
  def olderButton: HtmlElement = ListPaging.olderButton(listing.signal.map(_.hasOlder), older.signal.map(_.value), () => showOlder())

  /** The error line: what the last request answered, `what` naming the list. */
  def errorLine(what: UiLabels.Text): HtmlElement =
    div(child.maybe <-- failure.signal.map(_.map(m => ErrorLine.text(ErrorLine.Problem(s"${what.value}: ${m.value}")))))

object PolledList:
  type ItemId = ItemId.T
  object ItemId extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type TimerId = TimerId.T
  object TimerId extends Newtype[Int, Pure]

  /** ONE polled item — an agent run's window (#1946): a one-row [[PolledList]], so it shares the schedule, the
    * `mounted` guard, the stale-answer guard and the error line with the list windows; it has no pages, so it never
    * shows older rows. `after` gets the item before and after each poll (not the first load).
    */
  type Loaded = Loaded.T
  object Loaded extends Newtype[Boolean, Pure]
  type Older = Older.T
  object Older extends Newtype[Boolean, Pure]
  type Restarting = Restarting.T
  object Restarting extends Newtype[Boolean, Pure]

  def single[A](
      fetch: () => Future[A],
      live: A => Boolean,
      after: (Option[A], A) => Unit = (_: Option[A], _: A) => (),
      later: (Poll.Revision, () => Unit) => TimerId = (ms, f) => TimerId(dom.window.setTimeout(() => f(), ms.value)),
      cancel: TimerId => Unit = id => dom.window.clearTimeout(id.value)
  )(using ExecutionContext, CanEqual[A, A]): PolledList[A] =
    PolledList[A](
      fetch = (_, _) => fetch().map(a => (List(a), Paging.Page.empty)),
      id = _ => ItemId(""),
      live = (rows, _) => rows.exists(live),
      after = (cur, rows) => rows.headOption.foreach(after(cur.rows.headOption, _)),
      later = later,
      cancel = cancel
    )
