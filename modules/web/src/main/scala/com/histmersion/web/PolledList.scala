package com.histmersion.web

import com.histmersion.core.Paging
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** The polling schedule of every polled window (jobs, agent runs, a manager): a quick first look,
  * then slower while nothing changes, and none once nothing is live. Pure. */
object Poll:
  val FirstMs = 1000
  val MaxMs   = 15000
  val Factor  = 1.6

  /** The delay before the next poll, or `None` to stop: nothing left to wait for. A change (a row
    * moved on, or one was started from this page) goes back to the quick delay. */
  def next(previous: Option[Int], live: Boolean, changed: Boolean): Option[Int] =
    if !live then None
    else if changed then Some(FirstMs)
    else Some(previous.fold(FirstMs)(p => math.min(MaxMs, math.round(p * Factor).toInt)))

/** ONE controller for the newest-first list windows — jobs, agent runs, approvals (#1798 item 6): the
  * rows ([[ListPaging]]: keyset pages, "Show older", a poll that keeps the pages loaded), the poll
  * ([[Poll]], none for a list with nothing `live`), the `mounted` guard (an answer that lands after the
  * window closed is dropped), the restart when the last row shown is gone, and one error line.
  *
  * @param fetch  a page: `upTo` (a poll of an extended listing) or `before` (the next older page)
  * @param live   whether anything is still moving — `_ => false` for a list that is not polled
  * @param after  called with the listing before and the rows of each successful poll (jobs: an OCR
  *               job that ticked an acquisition item off reloads that list)
  * @param later  `setTimeout`, injectable for a test; `cancel` its `clearTimeout` */
final class PolledList[A](
    fetch: (Option[String], Option[String]) => Future[(List[A], Paging.Page)],
    id: A => String,
    live: (List[A], Paging.Page) => Boolean,
    after: (ListPaging.Listing[A], List[A]) => Unit = (_: ListPaging.Listing[A], _: List[A]) => (),
    later: (Int, () => Unit) => Int = (ms, f) => dom.window.setTimeout(() => f(), ms),
    cancel: Int => Unit = id => dom.window.clearTimeout(id))(using ExecutionContext):

  val listing: Var[ListPaging.Listing[A]] = Var(ListPaging.Listing.empty[A])
  val loaded: Var[Boolean]                = Var(false)
  val older: Var[Boolean]                 = Var(false)
  val failure: Var[Option[String]]        = Var(None)
  private val latest = Latest()
  // `var`s: the one pending poll, its delay, and whether the window is there to be refreshed
  private var timer: Int         = 0
  private var delay: Option[Int] = None
  private var mounted: Boolean   = false

  /** The window opened: load the newest page (and poll while anything is live). */
  def open(): Unit = { mounted = true; loaded.set(false); restart() }

  /** The window closed: no poll, and an answer still on its way is dropped. */
  def close(): Unit = { mounted = false; cancel(timer); delay = None; latest.cancel() }

  /** The two lifecycle hooks, for the window's root element. */
  def lifecycle: Modifier[HtmlElement] = List(onMountCallback(_ => open()), onUnmountCallback(_ => close()))

  /** Start over from the newest page: the window opened, a filter changed, or "refresh". */
  def restart(): Unit = load(reset = true, changed = true)

  /** A poll — or an outside change (a decision, a job started here): the rows shown, current again. */
  def refresh(changed: Boolean = false): Unit = load(reset = false, changed)

  private def load(reset: Boolean, changed: Boolean): Unit =
    if mounted then
      cancel(timer)
      val cur  = listing.now()
      val upTo = if reset then None else ListPaging.pollUpTo(cur, id)
      older.set(false)
      val ticket = latest.start()
      fetch(upTo, None).onComplete(r => if mounted && latest.isCurrent(ticket) then answered(r, cur, upTo, reset, changed))

  private[web] def answered(r: Try[(List[A], Paging.Page)], cur: ListPaging.Listing[A], upTo: Option[String],
      reset: Boolean, changed: Boolean): Unit = r match
    // the last row shown is gone (the server cannot page from there): start over
    case Failure(_) if upTo.isDefined => restart()
    case Failure(e) =>
      failure.set(Some(e.getMessage))
      // keep trying while the window is open, at the slowest pace
      schedule(Some(Poll.MaxMs))
    case Success((rows, page)) =>
      val next  = if upTo.isEmpty then ListPaging.first(rows, page) else ListPaging.polled(cur, rows, page)
      val moved = changed || next != cur
      if !reset && loaded.now() then after(cur, rows)
      failure.set(None)
      listing.set(next)
      loaded.set(true)
      schedule(Poll.next(delay, live(rows, page), moved))

  /** "Show older": the next page, appended. A poll on its way is superseded (and rescheduled here). */
  def showOlder(): Unit =
    listing.now().page.next.foreach { cursor =>
      if mounted then
        cancel(timer)
        older.set(true)
        val ticket = latest.start()
        fetch(None, Some(cursor)).onComplete { r =>
          if mounted && latest.isCurrent(ticket) then
            older.set(false)
            r match
              case Failure(e) => failure.set(Some(e.getMessage)); schedule(Some(Poll.MaxMs))
              case Success((rows, page)) =>
                failure.set(None)
                listing.update(ListPaging.appended(_, rows, page, id))
                schedule(Poll.next(delay, live(listing.now().rows, page), changed = false))
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
  def errorLine(what: String): HtmlElement =
    div(child.maybe <-- failure.signal.map(_.map(m => ErrorLine.text(s"$what: $m"))))

object PolledList:

  /** ONE polled item — an agent run's window (#1946): a one-row [[PolledList]], so it shares the
    * schedule, the `mounted` guard, the stale-answer guard and the error line with the list windows;
    * it has no pages, so it never shows older rows. `after` gets the item before and after each poll
    * (not the first load). */
  def single[A](
      fetch: () => Future[A],
      live: A => Boolean,
      after: (Option[A], A) => Unit = (_: Option[A], _: A) => (),
      later: (Int, () => Unit) => Int = (ms, f) => dom.window.setTimeout(() => f(), ms),
      cancel: Int => Unit = id => dom.window.clearTimeout(id))(using ExecutionContext): PolledList[A] =
    PolledList[A](
      fetch = (_, _) => fetch().map(a => (List(a), Paging.Page.empty)),
      id = _ => "",
      live = (rows, _) => rows.exists(live),
      after = (cur, rows) => rows.headOption.foreach(after(cur.rows.headOption, _)),
      later = later,
      cancel = cancel)

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:
  def text(message: String): HtmlElement = p(cls := "form-error", role := "alert", message)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[String]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))
