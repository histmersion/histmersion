package com.histmersion.domain.automation.goals

import com.histmersion.db.FocusRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.values.GoalLinkKind
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference

/** The focus's reads of every goal at once, as the engine's other features reach them — each goal with what the focus
  * recorded about it, the goals linked to sources and articles, each goal's last stop and its sources — and what the
  * focus records: a goal joining and leaving the active set, a person's pin and priority.
  */
trait FocusService:
  /** Every goal with what the focus recorded about it, by slug. */
  def goals(): List[FocusRepo.GoalRow]

  /** The goals linked to each of `refs` by a link of `kind`. */
  def goalsOf(kind: String, refs: List[String]): Map[String, List[String]]

  /** Per goal: the outcome of its last settled model-using item when it stopped or failed. */
  def lastStops(): Map[String, String]

  /** Per goal: how many sources it links. */
  def sourceCounts(): Map[String, Int]

  /** The goal became active at `now`, `why`. */
  def joined(slug: String, why: String, now: Instant): Unit

  /** The goal left the active set at `now`, `why`. */
  def left(slug: String, why: String, now: Instant): Unit

  /** A person's pin; false = no such goal. */
  def pin(slug: String, pinned: Boolean): Boolean

  /** A person's priority; false = no such goal. */
  def priority(slug: String, n: Int): Boolean

object FocusService:
  def apply(db: Transactor): FocusService = Implementation(db)

  final private class Implementation(db: Transactor) extends FocusService:
    def goals(): List[FocusRepo.GoalRow] = db.read(FocusRepo.goals())
    def goalsOf(kind: String, refs: List[String]): Map[String, List[String]] = db.read(FocusRepo.goalsOf(kind, refs))
    def lastStops(): Map[String, String] = db.read(FocusRepo.lastStops())
    def sourceCounts(): Map[String, Int] = db.read(FocusRepo.sourceCounts())
    def joined(slug: String, why: String, now: Instant): Unit = db.transact(FocusRepo.joined(slug, why, now))
    def left(slug: String, why: String, now: Instant): Unit = db.transact(FocusRepo.left(slug, why, now))
    def pin(slug: String, pinned: Boolean): Boolean = db.transact(FocusRepo.pin(slug, pinned))
    def priority(slug: String, n: Int): Boolean = db.transact(FocusRepo.priority(slug, n))

  /** The focus in memory, over the shared state's goals, links and managers' items. */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends FocusService:
    private def t(at: Option[Instant]): Option[String] = at.map(AutomationState.text)
    def goals(): List[FocusRepo.GoalRow] =
      val s = state.get
      s.goals.values.toList.sortBy(_.goal.slug).map { g =>
        val f = s.focus.getOrElse(g.goal.slug, FocusRecord.none)
        FocusRepo.GoalRow(
          g.goal.slug,
          g.goal.title,
          g.goal.parentSlug,
          g.goal.status,
          AutomationState.text(g.createdAt),
          f.active,
          f.pinned,
          f.priority,
          t(f.since),
          f.why,
          t(f.leftAt),
          f.leftWhy
        )
      }
    def goalsOf(kind: String, refs: List[String]): Map[String, List[String]] =
      if refs.isEmpty then Map.empty
      else
        state.get.links
          .filter(l => l.kind.wire == kind && refs.contains(l.ref))
          .groupBy(_.ref)
          .view
          .mapValues(_.map(_.goal).sorted.toList)
          .toMap
    def lastStops(): Map[String, String] =
      val s = state.get
      val goalOf = s.managers.flatMap(m => m.goal.map(m.id -> _)).toMap
      s.items
        .filter(i =>
          goalOf.contains(i.managerId) && i.kind != "oa-ingest" && i.kind != "currency-check" &&
            Set("done", "stopped", "failed")(i.status)
        )
        .groupBy(i => goalOf(i.managerId))
        .view
        .mapValues(_.maxBy(i => (i.updatedAt, i.id)))
        .collect { case (g, i) if i.status == "stopped" || i.status == "failed" => g -> i.outcome.getOrElse(i.status) }
        .toMap
    def sourceCounts(): Map[String, Int] =
      state.get.links.filter(_.kind == GoalLinkKind.Source).groupBy(_.goal).view.mapValues(_.size).toMap
    private def changeFocus(slug: String, mustExist: Boolean)(f: FocusRecord => FocusRecord): Boolean =
      state
        .getAndUpdate(s =>
          if mustExist && !s.goals.contains(slug) then s
          else s.modify(_.focus).using(fs => fs.updated(slug, f(fs.getOrElse(slug, FocusRecord.none))))
        )
        .goals
        .contains(slug) || !mustExist
    def joined(slug: String, why: String, now: Instant): Unit =
      val _ = changeFocus(slug, mustExist = false)(
        _.modify(_.active).setTo(true).modify(_.since).setTo(Some(now)).modify(_.why).setTo(Some(why))
      )
    def left(slug: String, why: String, now: Instant): Unit =
      val _ = state.updateAndGet(
        _.modify(_.focus).using(fs =>
          fs.get(slug)
            .fold(fs)(f =>
              fs.updated(
                slug,
                f.modify(_.active).setTo(false).modify(_.leftAt).setTo(Some(now)).modify(_.leftWhy).setTo(Some(why))
              )
            )
        )
      )
    def pin(slug: String, pinned: Boolean): Boolean =
      changeFocus(slug, mustExist = true)(_.modify(_.pinned).setTo(pinned))
    def priority(slug: String, n: Int): Boolean = changeFocus(slug, mustExist = true)(_.modify(_.priority).setTo(n))
