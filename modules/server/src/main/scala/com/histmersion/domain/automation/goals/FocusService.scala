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
  def goalsOf(kind: com.histmersion.domain.automation.Goal.LinkKind.Wire, refs: List[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref]): Map[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref, List[com.histmersion.domain.automation.Goal.Slug]]

  /** Per goal: the outcome of its last settled model-using item when it stopped or failed. */
  def lastStops(): Map[com.histmersion.domain.automation.Goal.Slug, FocusRepo.GoalRow.LeftWhy]

  /** Per goal: how many sources it links. */
  def sourceCounts(): Map[com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount]

  /** The goal became active at `now`, `why`. */
  def joined(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.JoinWhy, now: Instant): Unit

  /** The goal left the active set at `now`, `why`. */
  def left(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy, now: Instant): Unit

  /** A person's pin; false = no such goal. */
  def pin(slug: com.histmersion.domain.automation.Goal.Slug, pinned: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned): Boolean

  /** A person's priority; false = no such goal. */
  def priority(slug: com.histmersion.domain.automation.Goal.Slug, n: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority): Boolean

object FocusService:
  /** A goal as the focus reads it. */
  type Goal = FocusRepo.GoalRow
  def apply(db: Transactor): FocusService = Implementation(db)

  final private class Implementation(db: Transactor) extends FocusService:
    def goals(): List[FocusRepo.GoalRow] = db.read(FocusRepo.goals())
    def goalsOf(kind: com.histmersion.domain.automation.Goal.LinkKind.Wire, refs: List[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref]): Map[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref, List[com.histmersion.domain.automation.Goal.Slug]] = db.read(FocusRepo.goalsOf(kind, refs))
    def lastStops(): Map[com.histmersion.domain.automation.Goal.Slug, FocusRepo.GoalRow.LeftWhy] = db.read(FocusRepo.lastStops())
    def sourceCounts(): Map[com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount] = db.read(FocusRepo.sourceCounts())
    def joined(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.JoinWhy, now: Instant): Unit = db.transact(FocusRepo.joined(slug, why, now))
    def left(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy, now: Instant): Unit = db.transact(FocusRepo.left(slug, why, now))
    def pin(slug: com.histmersion.domain.automation.Goal.Slug, pinned: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned): Boolean = db.transact(FocusRepo.pin(slug, pinned))
    def priority(slug: com.histmersion.domain.automation.Goal.Slug, n: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority): Boolean = db.transact(FocusRepo.priority(slug, n))

  /** The focus in memory, over the shared state's goals, links and managers' items. */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends FocusService:
    private def t(at: Option[Instant]): Option[String] = at.map(i => AutomationState.text(i).value)
    def goals(): List[FocusRepo.GoalRow] =
      val s = state.get
      s.goals.values.toList.sortBy(_.goal.slug.value).map { g =>
        val f = s.focus.getOrElse(AutomationState.FocusKey(g.goal.slug.value), FocusRecord.none)
        FocusRepo.GoalRow(
          FocusRepo.GoalRow.Slug(g.goal.slug.value),
          FocusRepo.GoalRow.Title(g.goal.title.value),
          g.goal.parentSlug.map(p => FocusRepo.GoalRow.Parent(p.value)),
          FocusRepo.GoalRow.Status(g.goal.status.value),
          FocusRepo.GoalRow.CreatedAt(AutomationState.text(g.createdAt).value),
          FocusRepo.GoalRow.Active(f.active.value),
          FocusRepo.GoalRow.Pinned(f.pinned.value),
          FocusRepo.GoalRow.Priority(f.priority.value),
          t(f.since).map(FocusRepo.GoalRow.Since(_)),
          f.why.map(w => FocusRepo.GoalRow.Why(w.value)),
          t(f.leftAt).map(FocusRepo.GoalRow.LeftAt(_)),
          f.leftWhy.map(w => FocusRepo.GoalRow.LeftWhy(w.value))
        )
      }
    def goalsOf(kind: com.histmersion.domain.automation.Goal.LinkKind.Wire, refs: List[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref]): Map[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref, List[com.histmersion.domain.automation.Goal.Slug]] =
      if refs.isEmpty then Map.empty
      else
        val wanted = refs.map(_.value)
        state.get.links
          .filter(l => l.kind.wire == kind && wanted.contains(l.ref.value))
          .groupBy(_.ref.value)
          .view
          .map((ref, ls) =>
            com.histmersion.api.automation.Goal.Plans.GoalLink.Ref(ref) ->
              ls.map(_.goal.value).sorted.toList.map(com.histmersion.domain.automation.Goal.Slug.assume(_))
          )
          .toMap
    def lastStops(): Map[com.histmersion.domain.automation.Goal.Slug, FocusRepo.GoalRow.LeftWhy] =
      val s = state.get
      val goalOf = s.managers.flatMap(m => m.goal.map(g => m.id.value -> g.value)).toMap
      s.items
        .filter(i =>
          goalOf.contains(i.managerId.value) && i.kind.value != "oa-ingest" && i.kind.value != "currency-check" &&
            Set("done", "stopped", "failed")(i.status.value)
        )
        .groupBy(i => goalOf(i.managerId.value))
        .view
        .mapValues(_.maxBy(i => (i.updatedAt, i.id.value)))
        .collect {
          case (g, i) if i.status.value == "stopped" || i.status.value == "failed" =>
            com.histmersion.domain.automation.Goal.Slug.assume(g) ->
              FocusRepo.GoalRow.LeftWhy(i.outcome.fold(i.status.value)(_.value))
        }
        .toMap
    def sourceCounts(): Map[com.histmersion.domain.automation.Goal.Slug, com.histmersion.RowCount] =
      state.get.links
        .filter(_.kind == com.histmersion.domain.automation.Goal.LinkKind.Source)
        .groupBy(_.goal.value)
        .view
        .map((g, ls) => com.histmersion.domain.automation.Goal.Slug.assume(g) -> com.histmersion.RowCount(ls.size))
        .toMap
    private def changeFocus(slug: com.histmersion.domain.automation.Goal.Slug, mustExist: Boolean)(f: FocusRecord => FocusRecord): Boolean =
      val goalKey = AutomationState.GoalsKey(slug.value)
      val focusKey = AutomationState.FocusKey(slug.value)
      state
        .getAndUpdate(s =>
          if mustExist && !s.goals.contains(goalKey) then s
          else s.modify(_.focus).using(fs => fs.updated(focusKey, f(fs.getOrElse(focusKey, FocusRecord.none))))
        )
        .goals
        .contains(goalKey) || !mustExist
    def joined(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.JoinWhy, now: Instant): Unit =
      val _ = changeFocus(slug, mustExist = false)(
        _.modify(_.active).setTo(FocusRecord.Active(true))
          .modify(_.since).setTo(Some(now))
          .modify(_.why).setTo(Some(FocusRecord.Why(why.value)))
      )
    def left(slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy, now: Instant): Unit =
      val focusKey = AutomationState.FocusKey(slug.value)
      val _ = state.updateAndGet(
        _.modify(_.focus).using(fs =>
          fs.get(focusKey)
            .fold(fs)(f =>
              fs.updated(
                focusKey,
                f.modify(_.active).setTo(FocusRecord.Active(false))
                  .modify(_.leftAt).setTo(Some(now))
                  .modify(_.leftWhy).setTo(Some(FocusRecord.LeftWhy(why.value)))
              )
            )
        )
      )
    def pin(slug: com.histmersion.domain.automation.Goal.Slug, pinned: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned): Boolean =
      changeFocus(slug, mustExist = true)(_.modify(_.pinned).setTo(FocusRecord.Pinned(pinned.value)))
    def priority(slug: com.histmersion.domain.automation.Goal.Slug, n: com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority): Boolean =
      changeFocus(slug, mustExist = true)(_.modify(_.priority).setTo(FocusRecord.Priority(n.value)))
