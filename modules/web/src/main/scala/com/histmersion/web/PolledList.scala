package com.histmersion
package web
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.Paging
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class PolledList[A](
    fetch: (Option[Paging.Cursor], Option[Paging.Cursor]) => Future[(List[A], Paging.Page)],
    id: A => PolledList.ItemId,
    live: (List[A], Paging.Page) => Boolean,
    after: (ListPaging.Listing[A], List[A]) => Unit = (_: ListPaging.Listing[A], _: List[A]) => (),
    later: (Poll.Revision, () => Unit) => PolledList.TimerId = (ms, f) =>
      PolledList.TimerId(dom.window.setTimeout(() => f(), ms.value)),
    cancel: PolledList.TimerId => Unit = id => dom.window.clearTimeout(id.value)
)(using ExecutionContext, CanEqual[A, A]):

  val listing: Var[ListPaging.Listing[A]] = Var(ListPaging.Listing.empty[A])
  val loaded: Var[PolledList.Loaded] = Var(PolledList.Loaded.Pending)
  val older: Var[PolledList.Older] = Var(PolledList.Older.Newest)
  val failure: Var[Option[Problem]] = Var(None)

  /** A restart (a filter changed) is on its way: the rows shown are the previous filter's until it lands. */
  private val restarting: Var[Boolean] = Var(false)
  def loading: Signal[Boolean] = restarting.signal
  private val latest = Latest()
  // `var`s: the one pending poll, its delay, and whether the window is there to be refreshed
  private var timer: PolledList.TimerId = PolledList.TimerId(0)
  private var delay: Option[Poll.Revision] = None
  private var mounted: Boolean = false

  /** The window opened: load the newest page (and poll while anything is live). */
  def open(): Unit = { mounted = true; loaded.set(PolledList.Loaded.Pending); restart() }

  /** The window closed: no poll, and an answer still on its way is dropped. */
  def close(): Unit = { mounted = false; cancel(timer); delay = None; latest.cancel(); restarting.set(false) }

  /** The two lifecycle hooks, for the window's root element. */
  def lifecycle: Modifier[HtmlElement] = List(onMountCallback(_ => open()), onUnmountCallback(_ => close()))

  /** Start over from the newest page: the window opened, a filter changed, or "refresh". */
  def restart(): Unit = load(reset = true, changed = Poll.DataChanged.Changed)

  /** A poll — or an outside change (a decision, a job started here): the rows shown, current again. */
  def refresh(changed: Poll.DataChanged = Poll.DataChanged.Unchanged): Unit = load(reset = false, changed)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def load(reset: Boolean, changed: Poll.DataChanged): Unit =
    if mounted then
      cancel(timer)
      val cur = listing.now()
      val upTo = if reset then None else ListPaging.pollUpTo(cur, a => id(a).value).map(Paging.Cursor(_))
      older.set(PolledList.Older.Newest)
      if reset then restarting.set(true)
      val ticket = latest.start()
      fetch(upTo, None).onComplete(r =>
        if mounted && latest.isCurrent(ticket) then answered(r, cur, upTo, reset, changed)
      )

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
        failure.set(Some(Problem.Unclassified(Legacy.Text(e.getMessage))))
        // keep trying while the window is open, at the slowest pace
        schedule(Some(Poll.Revision(Poll.MaxMs)))
      case Success((rows, page)) =>
        val next = if upTo.isEmpty then ListPaging.first(rows, page) else ListPaging.polled(cur, rows, page)
        val moved = changed.changed || next != cur
        if !reset && loaded.now().loaded then after(cur, rows)
        failure.set(None)
        listing.set(next)
        loaded.set(PolledList.Loaded.Loaded)
        schedule(
          Poll.next(
            delay,
            (if live(rows, page) then Poll.LiveMode.Live else Poll.LiveMode.Paused),
            (if moved then Poll.DataChanged.Changed else Poll.DataChanged.Unchanged)
          )
        )

  /** "Show older": the next page, appended. A poll on its way is superseded (and rescheduled here). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def showOlder(): Unit =
    listing.now().page.next.foreach { cursor =>
      if mounted then
        cancel(timer)
        older.set(PolledList.Older.Older)
        val ticket = latest.start()
        fetch(None, Some(cursor)).onComplete { r =>
          if mounted && latest.isCurrent(ticket) then
            older.set(PolledList.Older.Newest)
            r match
              case Failure(e) =>
                failure.set(Some(Problem.Unclassified(Legacy.Text(e.getMessage))));
                schedule(Some(Poll.Revision(Poll.MaxMs)))
              case Success((rows, page)) =>
                failure.set(None)
                listing.update(ListPaging.appended(_, rows, page, a => id(a).value))
                schedule(
                  Poll.next(
                    delay,
                    (if live(listing.now().rows, page) then Poll.LiveMode.Live else Poll.LiveMode.Paused),
                    changed = Poll.DataChanged.Unchanged
                  )
                )
        }
    }

  private def schedule(next: Option[Poll.Revision]): Unit =
    delay = next
    next.foreach(ms => timer = later(ms, () => refresh()))

  /** The one item of a [[PolledList.single]]: `None` until it has loaded. */
  def item: Signal[Option[A]] = listing.signal.map(_.rows.headOption)

  /** The "Show older" button under the list. */
  def olderButton: HtmlElement =
    ListPaging.olderButton(listing.signal.map(_.hasOlder), older.signal.map(_.older), () => showOlder())

  /** The error line: what the last request answered, `what` naming the list. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def errorLine(what: UiLabels.Text): HtmlElement =
    div(
      child.maybe <-- failure.signal.map(
        _.map(m => ErrorLine.text(Problem.Unclassified(Legacy.Text(s"${what.value}: ${m.sentence}"))))
      )
    )

object PolledList:
  type ItemId = ItemId.T
  object ItemId extends Newtype[String, Pure]
  type TimerId = TimerId.T
  object TimerId extends Newtype[Int, Pure]

  /** ONE polled item — an agent run's window (#1946): a one-row [[PolledList]], so it shares the schedule, the
    * `mounted` guard, the stale-answer guard and the error line with the list windows; it has no pages, so it never
    * shows older rows. `after` gets the item before and after each poll (not the first load).
    */
  enum Loaded derives CanEqual:
    case Loaded, Pending
  object Loaded:
    extension (loaded: Loaded) def loaded: Boolean = loaded == Loaded
  enum Older derives CanEqual:
    case Older, Newest
  object Older:
    extension (older: Older) def older: Boolean = older == Older
  enum Restarting derives CanEqual:
    case Restarting, Running
  object Restarting:
    extension (restarting: Restarting) def restarting: Boolean = restarting == Restarting

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
