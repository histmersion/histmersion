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
    fetch: (Option[String], Option[String]) => Future[(List[A], Paging.Page)],
    id: A => String,
    live: (List[A], Paging.Page) => Boolean,
    after: (ListPaging.Listing[A], List[A]) => Unit = (_: ListPaging.Listing[A], _: List[A]) => (),
    later: (Int, () => Unit) => Int = (ms, f) => dom.window.setTimeout(() => f(), ms),
    cancel: Int => Unit = id => dom.window.clearTimeout(id)
)(using ExecutionContext):

  val listing: Var[ListPaging.Listing[A]] = Var(ListPaging.Listing.empty[A])
  val loaded: Var[com.histmersion.web.PolledList.Loaded] = Var(false)
  val older: Var[com.histmersion.web.PolledList.Older] = Var(false)
  val failure: Var[Option[com.histmersion.web.PolledList.Failure]] = Var(None)

  /** A restart (a filter changed) is on its way: the rows shown are the previous filter's until it lands. */
  private val restarting: Var[com.histmersion.web.PolledList.Restarting] = Var(false)
  def loading: Signal[com.histmersion.web.PolledList.LoadingResult] = restarting.signal
  private val latest = Latest()
  // `var`s: the one pending poll, its delay, and whether the window is there to be refreshed
  private var timer: com.histmersion.web.PolledList.Timer = 0
  private var delay: Option[com.histmersion.web.PolledList.Delay] = None
  private var mounted: com.histmersion.web.PolledList.Mounted = false

  /** The window opened: load the newest page (and poll while anything is live). */
  def open(): Unit = { mounted = true; loaded.set(false); restart() }

  /** The window closed: no poll, and an answer still on its way is dropped. */
  def close(): Unit = { mounted = false; cancel(timer); delay = None; latest.cancel(); restarting.set(false) }

  /** The two lifecycle hooks, for the window's root element. */
  def lifecycle: Modifier[HtmlElement] = List(onMountCallback(_ => open()), onUnmountCallback(_ => close()))

  /** Start over from the newest page: the window opened, a filter changed, or "refresh". */
  def restart(): Unit = load(reset = true, changed = true)

  /** A poll — or an outside change (a decision, a job started here): the rows shown, current again. */
  def refresh(changed: com.histmersion.web.PolledList.RefreshChanged = false): Unit = load(reset = false, changed)

  private def load(reset: com.histmersion.web.PolledList.LoadReset, changed: com.histmersion.web.PolledList.LoadChanged): Unit =
    if mounted then
      cancel(timer)
      val cur = listing.now()
      val upTo = if reset then None else ListPaging.pollUpTo(cur, id)
      older.set(false)
      if reset then restarting.set(true)
      val ticket = latest.start()
      fetch(upTo, None).onComplete(r =>
        if mounted && latest.isCurrent(ticket) then answered(r, cur, upTo, reset, changed)
      )

  private[web] def answered(
      r: Try[(List[A], Paging.Page)],
      cur: ListPaging.Listing[A],
      upTo: Option[com.histmersion.web.PolledList.AnsweredUpTo],
      reset: com.histmersion.web.PolledList.AnsweredReset,
      changed: com.histmersion.web.PolledList.AnsweredChanged
  ): Unit =
    restarting.set(false)
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
              case Failure(e)            => failure.set(Some(e.getMessage)); schedule(Some(Poll.MaxMs))
              case Success((rows, page)) =>
                failure.set(None)
                listing.update(ListPaging.appended(_, rows, page, id))
                schedule(Poll.next(delay, live(listing.now().rows, page), changed = false))
        }
    }

  private def schedule(next: Option[com.histmersion.web.PolledList.ScheduleNext]): Unit =
    delay = next
    next.foreach(ms => timer = later(ms, () => refresh()))

  /** The one item of a [[PolledList.single]]: `None` until it has loaded. */
  def item: Signal[Option[A]] = listing.signal.map(_.rows.headOption)

  /** The "Show older" button under the list. */
  def olderButton: HtmlElement = ListPaging.olderButton(listing.signal.map(_.hasOlder), older.signal, () => showOlder())

  /** The error line: what the last request answered, `what` naming the list. */
  def errorLine(what: com.histmersion.web.PolledList.ErrorLineWhat): HtmlElement =
    div(child.maybe <-- failure.signal.map(_.map(m => ErrorLine.text(s"$what: $m"))))

object PolledList:

  /** ONE polled item — an agent run's window (#1946): a one-row [[PolledList]], so it shares the schedule, the
    * `mounted` guard, the stale-answer guard and the error line with the list windows; it has no pages, so it never
    * shows older rows. `after` gets the item before and after each poll (not the first load).
    */
  type Loaded = Loaded.T
  object Loaded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Older = Older.T
  object Older extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failure = Failure.T
  object Failure extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Restarting = Restarting.T
  object Restarting extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadingResult = LoadingResult.T
  object LoadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Timer = Timer.T
  object Timer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Delay = Delay.T
  object Delay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mounted = Mounted.T
  object Mounted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshChanged = RefreshChanged.T
  object RefreshChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadReset = LoadReset.T
  object LoadReset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadChanged = LoadChanged.T
  object LoadChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnsweredUpTo = AnsweredUpTo.T
  object AnsweredUpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnsweredReset = AnsweredReset.T
  object AnsweredReset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnsweredChanged = AnsweredChanged.T
  object AnsweredChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleNext = ScheduleNext.T
  object ScheduleNext extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ErrorLineWhat = ErrorLineWhat.T
  object ErrorLineWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingleLiveResult = SingleLiveResult.T
  object SingleLiveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingleLaterArg1 = SingleLaterArg1.T
  object SingleLaterArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingleLaterResult = SingleLaterResult.T
  object SingleLaterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SingleCancelArg1 = SingleCancelArg1.T
  object SingleCancelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def single[A](
      fetch: () => Future[A],
      live: A => com.histmersion.web.PolledList.SingleLiveResult,
      after: (Option[A], A) => Unit = (_: Option[A], _: A) => (),
      later: (com.histmersion.web.PolledList.SingleLaterArg1, () => Unit) => com.histmersion.web.PolledList.SingleLaterResult = (ms, f) => dom.window.setTimeout(() => f(), ms),
      cancel: com.histmersion.web.PolledList.SingleCancelArg1 => Unit = id => dom.window.clearTimeout(id)
  )(using ExecutionContext): PolledList[A] =
    PolledList[A](
      fetch = (_, _) => fetch().map(a => (List(a), Paging.Page.empty)),
      id = _ => "",
      live = (rows, _) => rows.exists(live),
      after = (cur, rows) => rows.headOption.foreach(after(cur.rows.headOption, _)),
      later = later,
      cancel = cancel
    )
