package com.histmersion.domain.automation.goals

import com.histmersion.domain.automation.goals.FocusRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
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
  def goalsOf(kind: com.histmersion.domain.automation.goals.FocusService.GoalsOfKind, refs: List[com.histmersion.domain.automation.goals.BGoalLinkRow.Ref]): Map[com.histmersion.domain.automation.goals.FocusService.GoalsOfResultPart1, List[com.histmersion.domain.automation.goals.FocusService.GoalsOfResultPart2]]

  /** Per goal: the outcome of its last settled model-using item when it stopped or failed. */
  def lastStops(): Map[com.histmersion.domain.automation.goals.FocusService.LastStopsResultPart1, com.histmersion.domain.automation.goals.FocusService.LastStopsResultPart2]

  /** Per goal: how many sources it links. */
  def sourceCounts(): Map[com.histmersion.domain.automation.goals.FocusService.SourceCountsResultPart1, com.histmersion.domain.automation.goals.FocusService.SourceCountsResultPart2]

  /** The goal became active at `now`, `why`. */
  def joined(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.JoinWhy, now: Instant): Unit

  /** The goal left the active set at `now`, `why`. */
  def left(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy, now: Instant): Unit

  /** A person's pin; false = no such goal. */
  def pin(slug: com.histmersion.domain.automation.Goal.Slug, pinned: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned): Boolean

  /** A person's priority; false = no such goal. */
  def priority(slug: com.histmersion.domain.automation.Goal.Slug, n: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority): Boolean

object FocusService:
  type ChangeFocusMustExist = ChangeFocusMustExist.T
  object ChangeFocusMustExist extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A goal as the focus reads it. */
  type GoalsOfResultPart1 = GoalsOfResultPart1.T
  object GoalsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfResultPart2 = GoalsOfResultPart2.T
  object GoalsOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfKind = GoalsOfKind.T
  object GoalsOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastStopsResultPart1 = LastStopsResultPart1.T
  object LastStopsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastStopsResultPart2 = LastStopsResultPart2.T
  object LastStopsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceCountsResultPart1 = SourceCountsResultPart1.T
  object SourceCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceCountsResultPart2 = SourceCountsResultPart2.T
  object SourceCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Goal = FocusRepo.GoalRow
  def apply(db: Transactor): FocusService = Implementation(db)

  final private class Implementation(db: Transactor) extends FocusService:
    def goals(): List[FocusRepo.GoalRow] = db.read(FocusRepo.goals())
    def goalsOf(kind: com.histmersion.domain.automation.goals.FocusService.Implementation.GoalsOfKind, refs: List[com.histmersion.domain.automation.goals.BGoalLinkRow.Ref]): Map[com.histmersion.domain.automation.goals.FocusService.Implementation.GoalsOfResultPart1, List[com.histmersion.domain.automation.goals.FocusService.Implementation.GoalsOfResultPart2]] = db.read(FocusRepo.goalsOf(kind, refs))
    def lastStops(): Map[com.histmersion.domain.automation.goals.FocusService.Implementation.LastStopsResultPart1, com.histmersion.domain.automation.goals.FocusService.Implementation.LastStopsResultPart2] = db.read(FocusRepo.lastStops())
    def sourceCounts(): Map[com.histmersion.domain.automation.goals.FocusService.Implementation.SourceCountsResultPart1, com.histmersion.domain.automation.goals.FocusService.Implementation.SourceCountsResultPart2] = db.read(FocusRepo.sourceCounts())
    def joined(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.JoinWhy, now: Instant): Unit = db.transact(FocusRepo.joined(slug, why, now))
    def left(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy, now: Instant): Unit = db.transact(FocusRepo.left(slug, why, now))
    def pin(slug: com.histmersion.domain.automation.Goal.Slug, pinned: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned): Boolean = db.transact(FocusRepo.pin(slug, pinned))
    def priority(slug: com.histmersion.domain.automation.Goal.Slug, n: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority): Boolean = db.transact(FocusRepo.priority(slug, n))

  object Implementation:
    type GoalsOfResultPart1 = GoalsOfResultPart1.T
    object GoalsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfResultPart2 = GoalsOfResultPart2.T
    object GoalsOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfKind = GoalsOfKind.T
    object GoalsOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastStopsResultPart1 = LastStopsResultPart1.T
    object LastStopsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastStopsResultPart2 = LastStopsResultPart2.T
    object LastStopsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceCountsResultPart1 = SourceCountsResultPart1.T
    object SourceCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceCountsResultPart2 = SourceCountsResultPart2.T
    object SourceCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    def goalsOf(kind: com.histmersion.domain.automation.goals.FocusService.InMemory.GoalsOfKind, refs: List[com.histmersion.domain.automation.goals.BGoalLinkRow.Ref]): Map[com.histmersion.domain.automation.goals.FocusService.InMemory.GoalsOfResultPart1, List[com.histmersion.domain.automation.goals.FocusService.InMemory.GoalsOfResultPart2]] =
      if refs.isEmpty then Map.empty
      else
        state.get.links
          .filter(l => l.kind.wire == kind && refs.contains(l.ref))
          .groupBy(_.ref)
          .view
          .mapValues(_.map(_.goal).sorted.toList)
          .toMap
    def lastStops(): Map[com.histmersion.domain.automation.goals.FocusService.InMemory.LastStopsResultPart1, com.histmersion.domain.automation.goals.FocusService.InMemory.LastStopsResultPart2] =
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
    def sourceCounts(): Map[com.histmersion.domain.automation.goals.FocusService.InMemory.SourceCountsResultPart1, com.histmersion.domain.automation.goals.FocusService.InMemory.SourceCountsResultPart2] =
      state.get.links
        .filter(_.kind == com.histmersion.domain.automation.Goal.LinkKind.Source)
        .groupBy(_.goal)
        .view
        .mapValues(_.size)
        .toMap
    private def changeFocus(slug: com.histmersion.domain.automation.Goal.Slug, mustExist: com.histmersion.domain.automation.goals.FocusService.ChangeFocusMustExist)(f: FocusRecord => FocusRecord): Boolean =
      state
        .getAndUpdate(s =>
          if mustExist && !s.goals.contains(slug) then s
          else s.modify(_.focus).using(fs => fs.updated(slug, f(fs.getOrElse(slug, FocusRecord.none))))
        )
        .goals
        .contains(slug) || !mustExist
    def joined(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.JoinWhy, now: Instant): Unit =
      val _ = changeFocus(slug, mustExist = com.histmersion.domain.automation.goals.FocusService.ChangeFocusMustExist(false))(
        _.modify(_.active).setTo(true).modify(_.since).setTo(Some(now)).modify(_.why).setTo(Some(why))
      )
    def left(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy, now: Instant): Unit =
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
    def pin(slug: com.histmersion.domain.automation.Goal.Slug, pinned: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned): Boolean =
      changeFocus(slug, mustExist = com.histmersion.domain.automation.goals.FocusService.ChangeFocusMustExist(true))(_.modify(_.pinned).setTo(pinned))
    def priority(slug: com.histmersion.domain.automation.Goal.Slug, n: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority): Boolean = changeFocus(slug, mustExist = com.histmersion.domain.automation.goals.FocusService.ChangeFocusMustExist(true))(_.modify(_.priority).setTo(n))

  object InMemory:
    type GoalsOfResultPart1 = GoalsOfResultPart1.T
    object GoalsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfResultPart2 = GoalsOfResultPart2.T
    object GoalsOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfKind = GoalsOfKind.T
    object GoalsOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastStopsResultPart1 = LastStopsResultPart1.T
    object LastStopsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastStopsResultPart2 = LastStopsResultPart2.T
    object LastStopsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceCountsResultPart1 = SourceCountsResultPart1.T
    object SourceCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceCountsResultPart2 = SourceCountsResultPart2.T
    object SourceCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
