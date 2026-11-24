package com.histmersion
package domain.automation.goals
import com.histmersion.domain.GoalFocus

import com.histmersion.domain.automation.goals.FocusRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{LintRule, tolerated}

/** The focus's reads of every goal at once, as the engine's other features reach them — each goal with what the focus
  * recorded about it, the goals linked to sources and articles, each goal's last stop and its sources — and what the
  * focus records: a goal joining and leaving the active set, a person's pin and priority.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait FocusService:
  /** Every goal with what the focus recorded about it, by slug. */
  def goals(): List[FocusRepo.GoalRow]

  /** The goals linked to each of `refs` by a link of `kind`. */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def goalsOf(
      kind: domain.automation.Goal.LinkKind.Wire,
      refs: List[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref]
  ): Map[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref, List[domain.automation.Goal.Slug]]

  /** Per goal: the outcome of its last settled model-using item when it stopped or failed. */
  def lastStops(): Map[domain.automation.Goal.Slug, GoalFocus.Rationale]

  /** Per goal: how many sources it links. */
  def sourceCounts(): Map[domain.automation.Goal.Slug, RowCount]

  /** The goal became active at `now`, `why`. */
  def joined(slug: domain.automation.Goal.Slug, why: GoalFocus.Rationale, now: Instant)(using WritePermit): Unit

  /** The goal left the active set at `now`, `why`. */
  def left(slug: domain.automation.Goal.Slug, why: GoalFocus.Rationale, now: Instant)(using WritePermit): Unit

  /** A person's pin; false = no such goal. */
  def pin(slug: domain.automation.Goal.Slug, pinned: FocusRepo.GoalRow.Pinned)(using WritePermit): Boolean

  /** A person's priority; false = no such goal. */
  def priority(slug: domain.automation.Goal.Slug, n: FocusRepo.GoalRow.Priority)(using WritePermit): Boolean

object FocusService:
  /** A goal as the focus reads it. */
  type Goal = FocusRepo.GoalRow
  def apply(db: Transactor): FocusService = Implementation(db)

  final private class Implementation(db: Transactor) extends FocusService:
    def goals(): List[FocusRepo.GoalRow] = db.read(FocusRepo.goals())
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def goalsOf(
        kind: domain.automation.Goal.LinkKind.Wire,
        refs: List[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref]
    ): Map[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref, List[domain.automation.Goal.Slug]] =
      db.read(FocusRepo.goalsOf(kind, refs))
    def lastStops(): Map[domain.automation.Goal.Slug, GoalFocus.Rationale] =
      db.read(FocusRepo.lastStops())
    def sourceCounts(): Map[domain.automation.Goal.Slug, RowCount] = db.read(FocusRepo.sourceCounts())
    def joined(slug: domain.automation.Goal.Slug, why: GoalFocus.Rationale, now: Instant)(using WritePermit): Unit =
      db.write(FocusRepo.joined(slug, why, now))
    def left(slug: domain.automation.Goal.Slug, why: GoalFocus.Rationale, now: Instant)(using WritePermit): Unit =
      db.write(FocusRepo.left(slug, why, now))
    def pin(slug: domain.automation.Goal.Slug, pinned: FocusRepo.GoalRow.Pinned)(using WritePermit): Boolean =
      db.write(FocusRepo.pin(slug, pinned))
    def priority(slug: domain.automation.Goal.Slug, n: FocusRepo.GoalRow.Priority)(using WritePermit): Boolean =
      db.write(FocusRepo.priority(slug, n))

  /** The focus in memory, over the shared state's goals, links and managers' items. */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends FocusService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def t(at: Option[Instant]): Option[String] = at.map(i => AutomationState.text(i).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
          f.why,
          t(f.leftAt).map(FocusRepo.GoalRow.LeftAt(_)),
          f.leftWhy
        )
      }
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def goalsOf(
        kind: domain.automation.Goal.LinkKind.Wire,
        refs: List[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref]
    ): Map[com.histmersion.api.automation.Goal.Plans.GoalLink.Ref, List[domain.automation.Goal.Slug]] =
      if refs.isEmpty then Map.empty
      else
        val wanted = refs.map(_.value)
        state.get.links
          .filter(l => l.kind.wire == kind && wanted.contains(l.ref.value))
          .groupBy(_.ref.value)
          .view
          .map((ref, ls) =>
            com.histmersion.api.automation.Goal.Plans.GoalLink.Ref(ref) ->
              ls.map(_.goal.value).sorted.toList.map(domain.automation.Goal.Slug.assume(_))
          )
          .toMap
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def lastStops(): Map[domain.automation.Goal.Slug, GoalFocus.Rationale] =
      val s = state.get
      val goalOf = s.managers.flatMap(m => m.goal.map(g => m.id.value -> g.value)).toMap
      s.items
        .filter(i =>
          goalOf.contains(i.managerId.value) && i.kind.value != "oa-ingest" && i.kind.value != "currency-check" &&
            Set("done", "stopped", "failed")(i.status.value)
        )
        .groupBy(i => goalOf(i.managerId.value))
        .view
        .mapValues(_.maxBy[(java.time.Instant, Long)](i => (i.updatedAt, i.id.value)))
        .collect {
          case (g, i) if i.status.value == "stopped" || i.status.value == "failed" =>
            domain.automation.Goal.Slug.assume(g) ->
              GoalFocus.Rationale(i.outcome.fold(i.status.value)(_.value))
        }
        .toMap
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def sourceCounts(): Map[domain.automation.Goal.Slug, RowCount] =
      state.get.links
        .filter(_.kind == domain.automation.Goal.LinkKind.Source)
        .groupBy(_.goal.value)
        .view
        .map((g, ls) => domain.automation.Goal.Slug.assume(g) -> RowCount(ls.size))
        .toMap
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def changeFocus(slug: domain.automation.Goal.Slug, mustExist: Boolean)(
        f: FocusRecord => FocusRecord
    ): Boolean =
      val goalKey = AutomationState.GoalsKey(slug.value)
      val focusKey = AutomationState.FocusKey(slug.value)
      state
        .getAndUpdate(s =>
          if mustExist && !s.goals.contains(goalKey) then s
          else s.modify(_.focus).using(fs => fs.updated(focusKey, f(fs.getOrElse(focusKey, FocusRecord.none))))
        )
        .goals
        .contains(goalKey) || !mustExist
    def joined(slug: domain.automation.Goal.Slug, why: GoalFocus.Rationale, now: Instant)(using WritePermit): Unit =
      val _ = changeFocus(slug, mustExist = false)(
        _.modify(_.active)
          .setTo(FocusRecord.Active(true))
          .modify(_.since)
          .setTo(Some(now))
          .modify(_.why)
          .setTo(Some(why))
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def left(slug: domain.automation.Goal.Slug, why: GoalFocus.Rationale, now: Instant)(using WritePermit): Unit =
      val focusKey = AutomationState.FocusKey(slug.value)
      val _ = state.updateAndGet(
        _.modify(_.focus).using(fs =>
          fs.get(focusKey)
            .fold(fs)(f =>
              fs.updated(
                focusKey,
                f.modify(_.active)
                  .setTo(FocusRecord.Active(false))
                  .modify(_.leftAt)
                  .setTo(Some(now))
                  .modify(_.leftWhy)
                  .setTo(Some(why))
              )
            )
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pin(slug: domain.automation.Goal.Slug, pinned: FocusRepo.GoalRow.Pinned)(using WritePermit): Boolean =
      changeFocus(slug, mustExist = true)(_.modify(_.pinned).setTo(FocusRecord.Pinned(pinned.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def priority(slug: domain.automation.Goal.Slug, n: FocusRepo.GoalRow.Priority)(using WritePermit): Boolean =
      changeFocus(slug, mustExist = true)(_.modify(_.priority).setTo(FocusRecord.Priority(n.value)))
