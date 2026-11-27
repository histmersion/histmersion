package com.histmersion
package web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.instance.{UiConfig, UiNotices}
import com.histmersion.api.instance.UiNotices.{Board, Counter, Counters}
import com.histmersion.web.ui.Layout
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The lists' notices ([[UiNotices]]), shown in Research's side navigation: each watched list's count (open approvals,
  * things to acquire, the open backlog) and whether it has something NEW since it was last opened — its button is bold
  * until then.
  *
  *   - the counters come from ONE request (`GET /api/ui/counters`), re-read every `ui.counters-poll-seconds` while the
  *     tab is visible, when it becomes visible again, and soon after the page's own changes ([[refreshSoon]],
  *     [[ownWrite]]);
  *   - an OPEN window is read: whenever a watched window is open (from its button, a link, the URL, a reload) and its
  *     board has news, the board is marked seen at the `latest` it showed — at once on the page (the button stops being
  *     bold in the same frame), then on the server (`POST /api/ui/seen/{board}`), which keeps the marks so every
  *     browser agrees.
  */
object Notices:
  type Spoken = Spoken.T
  object Spoken extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  // ── pure parts ──────────────────────────────────────────────────────────────────────────────────

  /** The board a window watches: its wire key is the window's own; other windows watch none. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def boardOf(key: WindowKey): Option[Board] = Board.parse(Board.Wire(key.wire.value))

  /** The boards to mark seen now: those whose window is open and that have news, at their `latest`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def toMark(open: List[WindowKey], cs: Map[Board.Wire, Counter]): List[(Board, Counter.Latest)] =
    open.flatMap(boardOf).distinct.flatMap { b =>
      cs.get(b.wire).filter(_.fresh.value).flatMap(_.latest).map(b -> _)
    }

  /** A counter once its window showed the items up to `at`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def seenAt(c: Counter, at: Counter.Seen): Counter =
    val s = UiNotices.advance(c.seen, at)
    c.modify(_.seen).setTo(Some(s)).modify(_.fresh).setTo(Counter.Fresh(UiNotices.fresh(c.latest, Some(s))))

  /** Is any board among `shown` new? (What the phone's tools button marks.) */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def anyFresh(cs: Map[Board.Wire, Counter], shown: Board => Boolean): Boolean =
    cs.values.exists(c => c.fresh.value && Board.parse(c.key).exists(shown))

  /** What a board's button says to a screen reader beside its label. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spoken(c: Option[Counter]): Spoken =
    val count = c.flatMap(x => UiNotices.badge(x.count)).map(n => s"$n open")
    val news = Option.when(c.exists(_.fresh.value))("something new")
    Spoken((count.toList ++ news).mkString(", "))

  // ── state ───────────────────────────────────────────────────────────────────────────────────────

  private val boards = Var(Map.empty[Board.Wire, Counter])
  private val glance = Var(Option.empty[UiNotices.AgentsGlance])

  /** What the research agents are doing (none where the server runs no agents). */
  def agents: Signal[Option[UiNotices.AgentsGlance]] = glance.signal.distinct

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def counter(b: Board): Signal[Option[Counter]] = boards.signal.map(_.get(b.wire)).distinct

  /** Is a board among those shown new? */
  def anyFresh(shown: Board => Boolean): Signal[Boolean] = boards.signal.map(anyFresh(_, shown)).distinct

  // `var`s: answers to a superseded request are dropped; the debounce timer; the marks on their way
  private var generation = 0
  private var soon = 0
  private val sending = mutable.Map.empty[Board, Counter.Latest]
  private var installed = false

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def apply(cs: Counters): Unit =
    boards.set(cs.boards.map(c => c.key -> c).toMap)
    glance.set(cs.agents)

  /** Re-read the counters; `after` sees them once they are in. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  def refresh(after: Map[Board.Wire, Counter] => Unit = _ => ()): Unit =
    generation += 1
    val mine = generation
    ApiClient.uiCounters().foreach { cs =>
      if mine == generation then
        apply(cs)
        after(boards.now())
    }

  /** Re-read the counters shortly — after a change the page made (several in a row read once). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refreshSoon(): Unit =
    dom.window.clearTimeout(soon)
    soon = dom.window.setTimeout(() => refresh(), Layout.countersRefreshMs.value)

  /** The page itself changed something on `b` from a window that is not `b`'s own (a goal edited in its own window, an
    * approval decided in a goal's): when `b` had no news before, the only change since is this one, which is not news
    * to the reader who made it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ownWrite(b: Board): Unit =
    val had = boards.now().get(b.wire).exists(_.fresh.value)
    dom.window.clearTimeout(soon)
    soon = dom.window.setTimeout(
      () =>
        refresh { cs =>
          if !had then cs.get(b.wire).filter(_.fresh.value).flatMap(_.latest).foreach(at => mark(b, at))
        },
      Layout.countersRefreshMs.value
    )

  /** Mark `b` seen at `at`: on the page now, then on the server. */
  @tolerated(LintRule.RawRequest, "pending G107 (requests through the one Request type)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def mark(b: Board, at: Counter.Latest): Unit =
    boards.update(m => m.get(b.wire).fold(m)(c => m.updated(b.wire, seenAt(c, Counter.Seen(at.value)))))
    if !sending.get(b).exists(_.value >= at.value) then
      sending(b) = at
      generation += 1 // a poll already on its way read the mark from before this one
      ApiClient.markSeen(b.wire, Counter.Seen(at.value)).onComplete { r =>
        if sending.get(b).contains(at) then sending.remove(b)
        r.fold(_ => refresh(), cs => { generation += 1; apply(cs) })
      }

  /** Once, when the page starts: the first read, the poll, and the open windows' marks. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def install(ui: UiConfig): Unit =
    if !installed then
      installed = true
      refresh()
      val every = math.max(5, ui.countersPollSeconds.value) * 1000
      dom.window.setInterval(() => if !dom.document.hidden then refresh(), every)
      dom.document.addEventListener("visibilitychange", (_: dom.Event) => if !dom.document.hidden then refresh())
      // a list is read while its page is shown, or its window open
      UiState.open.signal
        .combineWith(Router.route, boards.signal)
        .foreach { (open, page, cs) =>
          toMark(open ++ Route.board(page).toList, cs).foreach((b, at) => mark(b, at))
        }(using unsafeWindowOwner)
