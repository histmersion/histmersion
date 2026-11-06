package com.histmersion.domain.automation.goals

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{Write, YearFmt}
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Goal.Plans.{GoalLink, GoalSchedule}
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.domain.automation.ScheduleKind
import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api

/** The goals as the engine's other features reach them: a goal and its sub-goals, what hangs off it (its links, its
  * approved plan and run cap) and the schedules not moved into a watcher yet.
  */
trait GoalService:
  def get(slug: com.histmersion.domain.automation.goals.GoalService.GetSlug): Option[Goal]

  /** Every goal, by slug. */
  def all(): List[Goal]

  /** A goal's direct sub-goals, by slug. */
  def children(slug: com.histmersion.domain.automation.goals.GoalService.ChildrenSlug): List[Goal]

  /** Create or replace a goal; a period that starts after it ends is refused. */
  def put(slug: com.histmersion.domain.automation.goals.GoalService.PutSlug, in: Goal.Put): Either[Api.Error, Unit]

  /** One page of the goals (of one status, when given), by slug, with the total. */
  def page(status: Option[com.histmersion.domain.automation.goals.GoalService.PageStatus], limit: com.histmersion.domain.automation.goals.GoalService.PageLimit, offset: com.histmersion.domain.automation.goals.GoalService.PageOffset): Goal.List

  /** A goal's fields changed as given (a cap of 0 or less clears it; a bound given alone replaces that bound); no such
    * goal is a not-found error.
    */
  def patch(slug: com.histmersion.domain.automation.goals.GoalService.PatchSlug, p: Goal.Patch): Either[Api.Error, Unit]

  /** A goal removed; false when there was none. */
  def delete(slug: com.histmersion.domain.automation.goals.GoalService.DeleteSlug): com.histmersion.domain.automation.goals.GoalService.DeleteResult

  /** A new slug, the old one kept as an alias (V053): refused when there is no such goal, or the new slug is another
    * goal's or another goal's old one.
    */
  def rename(from: com.histmersion.domain.automation.goals.GoalService.RenameFrom, to: com.histmersion.domain.automation.goals.GoalService.RenameTo): Either[Api.Error, Unit]

  /** The goals that link each of these work-list items: (item id, goal slug, goal title), an item's goals by title. */
  def goalsOfItems(items: List[com.histmersion.domain.automation.goals.GoalService.GoalsOfItemsItems]): List[(com.histmersion.domain.automation.goals.GoalService.GoalsOfItemsResultPart1, com.histmersion.domain.automation.goals.GoalService.GoalsOfItemsResultPart2, com.histmersion.domain.automation.goals.GoalService.GoalsOfItemsResultPart3)]

  /** A goal's links with the title of what each points at: sources, articles, queries, queue items; oldest first. */
  def links(goal: com.histmersion.domain.automation.goals.GoalService.LinksGoal): List[GoalLink]

  /** Link (an existing link keeps its note unless a new one is given); false = no such goal. */
  def link(
      goal: com.histmersion.domain.automation.goals.GoalService.LinkGoal,
      kind: com.histmersion.domain.automation.Goal.LinkKind,
      ref: com.histmersion.domain.automation.goals.GoalService.LinkRef,
      note: Option[com.histmersion.domain.automation.goals.GoalService.LinkNote]
  ): com.histmersion.domain.automation.goals.GoalService.LinkResult
  def unlink(goal: com.histmersion.domain.automation.goals.GoalService.UnlinkGoal, kind: com.histmersion.domain.automation.Goal.LinkKind, ref: com.histmersion.domain.automation.goals.GoalService.UnlinkRef): com.histmersion.domain.automation.goals.GoalService.UnlinkResult

  /** The refs of a goal's links of one kind (a sweep's queries, a currency check's sources), oldest first. */
  def refs(goal: com.histmersion.domain.automation.goals.GoalService.RefsGoal, kind: com.histmersion.domain.automation.Goal.LinkKind): List[com.histmersion.domain.automation.goals.GoalService.RefsResult]

  /** Every goal's links counted by kind; an article link counts only while the article exists. */
  def linkCounts(): Map[(com.histmersion.domain.automation.goals.GoalService.LinkCountsResultPart1Part1, com.histmersion.domain.automation.goals.GoalService.LinkCountsResultPart1Part2), com.histmersion.domain.automation.goals.GoalService.LinkCountsResultPart2]

  /** The goals that record an approved plan. */
  def planned(): Set[com.histmersion.domain.automation.goals.GoalService.PlannedResult]

  /** The approved plan (its JSON) and the run cap; None = no such goal. */
  def planAndBudget(goal: com.histmersion.domain.automation.goals.GoalService.PlanAndBudgetGoal): Option[(Option[com.histmersion.domain.automation.goals.GoalService.PlanAndBudgetResultPart1], Option[BigDecimal])]
  def setPlan(goal: com.histmersion.domain.automation.goals.GoalService.SetPlanGoal, planJson: com.histmersion.domain.automation.goals.GoalService.SetPlanPlanJson): Unit
  def setBudget(goal: com.histmersion.domain.automation.goals.GoalService.SetBudgetGoal, usd: Option[BigDecimal]): Unit

  /** The schedules not moved into a watcher (one goal's, or every one), the next due first. */
  def schedules(goal: Option[com.histmersion.domain.automation.goals.GoalService.SchedulesGoal]): List[GoalSchedule]
  def setSchedule(
      goal: com.histmersion.domain.automation.goals.GoalService.SetScheduleGoal,
      kind: ScheduleKind,
      cron: com.histmersion.domain.automation.goals.GoalService.SetScheduleCron,
      enabled: com.histmersion.domain.automation.goals.GoalService.SetScheduleEnabled,
      budgetUsd: Option[BigDecimal],
      next: Option[Instant]
  ): Unit
  def removeSchedule(goal: com.histmersion.domain.automation.goals.GoalService.RemoveScheduleGoal, kind: ScheduleKind): com.histmersion.domain.automation.goals.GoalService.RemoveScheduleResult

  /** The goals that still have schedules here. */
  def scheduleGoals(): List[com.histmersion.domain.automation.goals.GoalService.ScheduleGoalsResult]

  /** A goal's schedules taken out, in the caller's transaction (moved into its watcher). */
  def takeSchedulesIn(goal: com.histmersion.domain.automation.goals.GoalService.TakeSchedulesInGoal)(using DbCon): List[GoalSchedule]

object GoalService:
  type GetSlug = GetSlug.T
  object GetSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChildrenSlug = ChildrenSlug.T
  object ChildrenSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSlug = PutSlug.T
  object PutSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageStatus = PageStatus.T
  object PageStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageLimit = PageLimit.T
  object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOffset = PageOffset.T
  object PageOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSlug = PatchSlug.T
  object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSlug = DeleteSlug.T
  object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameFrom = RenameFrom.T
  object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameTo = RenameTo.T
  object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsResultPart1 = GoalsOfItemsResultPart1.T
  object GoalsOfItemsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsResultPart2 = GoalsOfItemsResultPart2.T
  object GoalsOfItemsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsResultPart3 = GoalsOfItemsResultPart3.T
  object GoalsOfItemsResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsOfItemsItems = GoalsOfItemsItems.T
  object GoalsOfItemsItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksGoal = LinksGoal.T
  object LinksGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoal = LinkGoal.T
  object LinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRef = LinkRef.T
  object LinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkNote = LinkNote.T
  object LinkNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkResult = UnlinkResult.T
  object UnlinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkGoal = UnlinkGoal.T
  object UnlinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlinkRef = UnlinkRef.T
  object UnlinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResult = RefsResult.T
  object RefsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsGoal = RefsGoal.T
  object RefsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCountsResultPart1Part1 = LinkCountsResultPart1Part1.T
  object LinkCountsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCountsResultPart1Part2 = LinkCountsResultPart1Part2.T
  object LinkCountsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkCountsResultPart2 = LinkCountsResultPart2.T
  object LinkCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlannedResult = PlannedResult.T
  object PlannedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanAndBudgetResultPart1 = PlanAndBudgetResultPart1.T
  object PlanAndBudgetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanAndBudgetGoal = PlanAndBudgetGoal.T
  object PlanAndBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPlanGoal = SetPlanGoal.T
  object SetPlanGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPlanPlanJson = SetPlanPlanJson.T
  object SetPlanPlanJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetBudgetGoal = SetBudgetGoal.T
  object SetBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchedulesGoal = SchedulesGoal.T
  object SchedulesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleGoal = SetScheduleGoal.T
  object SetScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleCron = SetScheduleCron.T
  object SetScheduleCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetScheduleEnabled = SetScheduleEnabled.T
  object SetScheduleEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveScheduleResult = RemoveScheduleResult.T
  object RemoveScheduleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveScheduleGoal = RemoveScheduleGoal.T
  object RemoveScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleGoalsResult = ScheduleGoalsResult.T
  object ScheduleGoalsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TakeSchedulesInGoal = TakeSchedulesInGoal.T
  object TakeSchedulesInGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): GoalService = Implementation(db)

  final private class Implementation(db: Transactor) extends GoalService:
    def get(slug: com.histmersion.domain.automation.goals.GoalService.Implementation.GetSlug): Option[Goal] = db.read(GoalRepo.get(slug))
    def all(): List[Goal] = db.read(GoalRepo.all())
    def children(slug: com.histmersion.domain.automation.goals.GoalService.Implementation.ChildrenSlug): List[Goal] = db.read(GoalRepo.children(slug))
    def put(slug: com.histmersion.domain.automation.goals.GoalService.Implementation.PutSlug, in: Goal.Put): Either[Api.Error, Unit] = db.transact(GoalRepo.put(slug, in))
    def page(status: Option[com.histmersion.domain.automation.goals.GoalService.Implementation.PageStatus], limit: com.histmersion.domain.automation.goals.GoalService.Implementation.PageLimit, offset: com.histmersion.domain.automation.goals.GoalService.Implementation.PageOffset): Goal.List =
      db.read(GoalRepo.listIn(status, limit, offset))
    def patch(slug: com.histmersion.domain.automation.goals.GoalService.Implementation.PatchSlug, p: Goal.Patch): Either[Api.Error, Unit] = db.transact(GoalRepo.patchIn(slug, p))
    def delete(slug: com.histmersion.domain.automation.goals.GoalService.Implementation.DeleteSlug): com.histmersion.domain.automation.goals.GoalService.Implementation.DeleteResult = db.transact(GoalRepo.deleteIn(slug))
    def rename(from: com.histmersion.domain.automation.goals.GoalService.Implementation.RenameFrom, to: com.histmersion.domain.automation.goals.GoalService.Implementation.RenameTo): Either[Api.Error, Unit] = db.transact(GoalRepo.renameIn(from.trim, to.trim))
    def goalsOfItems(items: List[com.histmersion.domain.automation.goals.GoalService.Implementation.GoalsOfItemsItems]): List[(com.histmersion.domain.automation.goals.GoalService.Implementation.GoalsOfItemsResultPart1, com.histmersion.domain.automation.goals.GoalService.Implementation.GoalsOfItemsResultPart2, com.histmersion.domain.automation.goals.GoalService.Implementation.GoalsOfItemsResultPart3)] = db.read(GoalLinkRepo.goalsOfItemsIn(items))
    def links(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.LinksGoal): List[GoalLink] = db.read(GoalLinkRepo.links(goal))
    def link(
        goal: com.histmersion.domain.automation.goals.GoalService.Implementation.LinkGoal,
        kind: com.histmersion.domain.automation.Goal.LinkKind,
        ref: com.histmersion.domain.automation.goals.GoalService.Implementation.LinkRef,
        note: Option[com.histmersion.domain.automation.goals.GoalService.Implementation.LinkNote]
    ): com.histmersion.domain.automation.goals.GoalService.Implementation.LinkResult =
      db.transact(
        GoalLinkRepo.link(goal, kind.transformInto[com.histmersion.api.automation.Goal.Plans.LinkKind], ref, note)
      )
    def unlink(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.UnlinkGoal, kind: com.histmersion.domain.automation.Goal.LinkKind, ref: com.histmersion.domain.automation.goals.GoalService.Implementation.UnlinkRef): com.histmersion.domain.automation.goals.GoalService.Implementation.UnlinkResult =
      db.transact(
        GoalLinkRepo.unlink(goal, kind.transformInto[com.histmersion.api.automation.Goal.Plans.LinkKind], ref)
      )
    def refs(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.RefsGoal, kind: com.histmersion.domain.automation.Goal.LinkKind): List[com.histmersion.domain.automation.goals.GoalService.Implementation.RefsResult] =
      db.read(GoalLinkRepo.refsIn(goal, kind.transformInto[com.histmersion.api.automation.Goal.Plans.LinkKind]))
    def linkCounts(): Map[(com.histmersion.domain.automation.goals.GoalService.Implementation.LinkCountsResultPart1Part1, com.histmersion.domain.automation.goals.GoalService.Implementation.LinkCountsResultPart1Part2), com.histmersion.domain.automation.goals.GoalService.Implementation.LinkCountsResultPart2] = db.read(GoalLinkRepo.linkCounts())
    def planned(): Set[com.histmersion.domain.automation.goals.GoalService.Implementation.PlannedResult] = db.read(GoalLinkRepo.planned())
    def planAndBudget(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.PlanAndBudgetGoal): Option[(Option[com.histmersion.domain.automation.goals.GoalService.Implementation.PlanAndBudgetResultPart1], Option[BigDecimal])] =
      db.read(GoalLinkRepo.planAndBudget(goal))
    def setPlan(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.SetPlanGoal, planJson: com.histmersion.domain.automation.goals.GoalService.Implementation.SetPlanPlanJson): Unit = db.transact(GoalLinkRepo.setPlan(goal, planJson))
    def setBudget(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.SetBudgetGoal, usd: Option[BigDecimal]): Unit = db.transact(GoalLinkRepo.setBudget(goal, usd))
    def schedules(goal: Option[com.histmersion.domain.automation.goals.GoalService.Implementation.SchedulesGoal]): List[GoalSchedule] = db.read(GoalLinkRepo.schedules(goal))
    def setSchedule(
        goal: com.histmersion.domain.automation.goals.GoalService.Implementation.SetScheduleGoal,
        kind: ScheduleKind,
        cron: com.histmersion.domain.automation.goals.GoalService.Implementation.SetScheduleCron,
        enabled: com.histmersion.domain.automation.goals.GoalService.Implementation.SetScheduleEnabled,
        budgetUsd: Option[BigDecimal],
        next: Option[Instant]
    ): Unit = db.transact(
      GoalLinkRepo.setSchedule(
        goal,
        kind.transformInto[com.histmersion.api.automation.Goal.Plans.ScheduleKind],
        cron,
        enabled,
        budgetUsd,
        next
      )
    )
    def removeSchedule(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.RemoveScheduleGoal, kind: ScheduleKind): com.histmersion.domain.automation.goals.GoalService.Implementation.RemoveScheduleResult =
      db.transact(
        GoalLinkRepo.removeSchedule(goal, kind.transformInto[com.histmersion.api.automation.Goal.Plans.ScheduleKind])
      )
    def scheduleGoals(): List[com.histmersion.domain.automation.goals.GoalService.Implementation.ScheduleGoalsResult] = db.read(GoalLinkRepo.scheduleGoals())
    def takeSchedulesIn(goal: com.histmersion.domain.automation.goals.GoalService.Implementation.TakeSchedulesInGoal)(using DbCon): List[GoalSchedule] = GoalLinkRepo.takeSchedulesIn(goal)

  object Implementation:
    type GetSlug = GetSlug.T
    object GetSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChildrenSlug = ChildrenSlug.T
    object ChildrenSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutSlug = PutSlug.T
    object PutSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageStatus = PageStatus.T
    object PageStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageLimit = PageLimit.T
    object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageOffset = PageOffset.T
    object PageOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchSlug = PatchSlug.T
    object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSlug = DeleteSlug.T
    object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsResultPart1 = GoalsOfItemsResultPart1.T
    object GoalsOfItemsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsResultPart2 = GoalsOfItemsResultPart2.T
    object GoalsOfItemsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsResultPart3 = GoalsOfItemsResultPart3.T
    object GoalsOfItemsResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsItems = GoalsOfItemsItems.T
    object GoalsOfItemsItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksGoal = LinksGoal.T
    object LinksGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkResult = LinkResult.T
    object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkGoal = LinkGoal.T
    object LinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkRef = LinkRef.T
    object LinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkNote = LinkNote.T
    object LinkNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlinkResult = UnlinkResult.T
    object UnlinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlinkGoal = UnlinkGoal.T
    object UnlinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlinkRef = UnlinkRef.T
    object UnlinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsResult = RefsResult.T
    object RefsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsGoal = RefsGoal.T
    object RefsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkCountsResultPart1Part1 = LinkCountsResultPart1Part1.T
    object LinkCountsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkCountsResultPart1Part2 = LinkCountsResultPart1Part2.T
    object LinkCountsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkCountsResultPart2 = LinkCountsResultPart2.T
    object LinkCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlannedResult = PlannedResult.T
    object PlannedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanAndBudgetResultPart1 = PlanAndBudgetResultPart1.T
    object PlanAndBudgetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanAndBudgetGoal = PlanAndBudgetGoal.T
    object PlanAndBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetPlanGoal = SetPlanGoal.T
    object SetPlanGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetPlanPlanJson = SetPlanPlanJson.T
    object SetPlanPlanJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetBudgetGoal = SetBudgetGoal.T
    object SetBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SchedulesGoal = SchedulesGoal.T
    object SchedulesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetScheduleGoal = SetScheduleGoal.T
    object SetScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetScheduleCron = SetScheduleCron.T
    object SetScheduleCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetScheduleEnabled = SetScheduleEnabled.T
    object SetScheduleEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RemoveScheduleResult = RemoveScheduleResult.T
    object RemoveScheduleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RemoveScheduleGoal = RemoveScheduleGoal.T
    object RemoveScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ScheduleGoalsResult = ScheduleGoalsResult.T
    object ScheduleGoalsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TakeSchedulesInGoal = TakeSchedulesInGoal.T
    object TakeSchedulesInGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The goals in memory; `clock` stands in for the database's `now()`, `targets` for the other domains' rows a link
    * names.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock, targets: GoalLinkTargets)
      extends GoalService:
    private def now: Instant = Instant.now(clock)
    private def goals: Map[com.histmersion.domain.automation.goals.GoalService.InMemory.GoalsResultPart1, GoalRecord] = state.get.goals
    private val Updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** `updated_at::text` in a UTC session: seconds, the fraction without its trailing zeros, the offset. */
    private def updatedText(at: Instant): com.histmersion.domain.automation.goals.GoalService.InMemory.UpdatedTextResult =
      val micros = at.getNano / 1000
      val fraction = if micros == 0 then "" else "." + f"$micros%06d".reverse.dropWhile(_ == '0').reverse
      s"${Updated.format(at)}$fraction+00"
    private def shown(r: GoalRecord): Goal = r.goal.modify(_.updatedAt).setTo(Some(updatedText(r.updatedAt)))
    private def kindOrder(k: com.histmersion.domain.automation.Goal.LinkKind): com.histmersion.domain.automation.goals.GoalService.InMemory.KindOrderResult = k match
      case com.histmersion.domain.automation.Goal.LinkKind.Source      => 0
      case com.histmersion.domain.automation.Goal.LinkKind.Article     => 1
      case com.histmersion.domain.automation.Goal.LinkKind.SearchQuery => 2
      case _                                                           => 3

    def get(slug: com.histmersion.domain.automation.goals.GoalService.InMemory.GetSlug): Option[Goal] = goals.get(slug).map(shown)
    def all(): List[Goal] = goals.values.toList.sortBy(_.goal.slug).map(shown)
    def children(slug: com.histmersion.domain.automation.goals.GoalService.InMemory.ChildrenSlug): List[Goal] = all().filter(_.parentSlug.contains(slug))
    def put(slug: com.histmersion.domain.automation.goals.GoalService.InMemory.PutSlug, in: Goal.Put): Either[Api.Error, Unit] =
      (in.periodStart, in.periodEnd) match
        case (Some(a), Some(b)) if a > b =>
          Left(
            Api.Error(
              s"goal '$slug': the period would start (${YearFmt(a)}) after it ends (${YearFmt(b)})",
              Some(Write.CodeInvalid)
            )
          )
        case (start, end) =>
          val at = now
          val goal = Goal(slug, in.title, in.status, in.notes, in.tags, in.regions, start, end, in.parentSlug, None)
          val _ = state.updateAndGet(
            _.modify(_.goals).using(gs =>
              gs.updated(
                slug,
                gs.get(slug)
                  .fold(GoalRecord(goal, None, None, None, at, at))(
                    _.modify(_.goal).setTo(goal).modify(_.updatedAt).setTo(at)
                  )
              )
            )
          )
          Right(())
    def page(status: Option[com.histmersion.domain.automation.goals.GoalService.InMemory.PageStatus], limit: com.histmersion.domain.automation.goals.GoalService.InMemory.PageLimit, offset: com.histmersion.domain.automation.goals.GoalService.InMemory.PageOffset): Goal.List =
      val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
      val listed = all().filter(g => st.forall(_ == g.status))
      Goal.List.of(listed.size, listed.slice(offset.max(0), offset.max(0) + limit.max(1)), offset.max(0))

    private def notFound(slug: com.histmersion.domain.automation.goals.GoalService.InMemory.NotFoundSlug) = Api.Error(s"No goal '$slug'", Some(Write.CodeNotFound))

    def patch(slug: com.histmersion.domain.automation.goals.GoalService.InMemory.PatchSlug, p: Goal.Patch): Either[Api.Error, Unit] =
      goals.get(slug).toRight(notFound(slug)).flatMap { r =>
        val g = r.goal
        val start = p.periodStart.orElse(g.periodStart)
        val end = p.periodEnd.orElse(g.periodEnd)
        (start, end) match
          case (Some(a), Some(b)) if a > b =>
            Left(
              Api.Error(
                s"goal '$slug': the period would start (${YearFmt(a)}) after it ends (${YearFmt(b)})",
                Some(Write.CodeInvalid)
              )
            )
          case _ =>
            val changed = g
              .modify(_.title)
              .using(p.title.getOrElse(_))
              .modify(_.status)
              .using(p.status.getOrElse(_))
              .modify(_.notes)
              .using(p.notes.map(Some(_)).getOrElse(_))
              .modify(_.tags)
              .using(p.tags.getOrElse(_))
              .modify(_.regions)
              .using(p.regions.getOrElse(_))
              .modify(_.parentSlug)
              .using(p.parentSlug.map(Some(_)).getOrElse(_))
              .modify(_.periodStart)
              .setTo(start)
              .modify(_.periodEnd)
              .setTo(end)
            val budget = p.budgetUsd.fold(r.budgetUsd)(v => Option.when(v > 0)(BigDecimal(v)))
            val touched = List(p.title, p.status, p.notes, p.tags, p.regions, p.parentSlug, p.budgetUsd).exists(
              _.isDefined
            ) || (start, end) != (g.periodStart, g.periodEnd)
            val at = now
            changeGoal(slug)(x =>
              val y = x.modify(_.goal).setTo(changed).modify(_.budgetUsd).setTo(budget)
              if touched then y.modify(_.updatedAt).setTo(at) else y
            )
            Right(())
      }

    def delete(slug: com.histmersion.domain.automation.goals.GoalService.InMemory.DeleteSlug): com.histmersion.domain.automation.goals.GoalService.InMemory.DeleteResult =
      val before = state.getAndUpdate(
        _.modify(_.goals)
          .using(_ - slug)
          .modify(_.links)
          .using(_.filterNot(_.goal == slug))
          .modify(_.schedules)
          .using(_.filterNot(_.goal == slug))
      )
      before.goals.contains(slug)

    def rename(from: com.histmersion.domain.automation.goals.GoalService.InMemory.RenameFrom, to: com.histmersion.domain.automation.goals.GoalService.InMemory.RenameTo): Either[Api.Error, Unit] =
      val (old, now) = (from.trim, to.trim)
      val aliasOf = state.get.goalAliases.get(now)
      if !goals.contains(old) then Left(notFound(old))
      else if goals.contains(now) then
        Left(Api.Error(s"'$now' is already the slug of another goal", Some(Write.CodeInvalid)))
      else if aliasOf.exists(_ != old) then
        Left(
          Api.Error(
            s"'$now' is an old slug of '${aliasOf.get}' and still resolves to it — pick another",
            Some(Write.CodeInvalid)
          )
        )
      else
        val _ = state.updateAndGet { st =>
          val r = st.goals(old)
          def moved(s: com.histmersion.domain.automation.goals.GoalService.InMemory.RenameMovedS) = if s == old then now else s
          st.modify(_.goals)
            .using(gs =>
              (gs - old)
                .updated(now, r.modify(_.goal.slug).setTo(now))
                .view
                .mapValues(_.modify(_.goal.parentSlug).using(_.map(moved)))
                .toMap
            )
            .modify(_.links)
            .using(_.map(_.modify(_.goal).using(moved)))
            .modify(_.schedules)
            .using(_.map(_.modify(_.goal).using(moved)))
            .modify(_.goalAliases)
            .using(as => (as - now).view.mapValues(moved).toMap.updated(old, now))
        }
        Right(())

    def goalsOfItems(items: List[com.histmersion.domain.automation.goals.GoalService.InMemory.GoalsOfItemsItems]): List[(com.histmersion.domain.automation.goals.GoalService.InMemory.GoalsOfItemsResultPart1, com.histmersion.domain.automation.goals.GoalService.InMemory.GoalsOfItemsResultPart2, com.histmersion.domain.automation.goals.GoalService.InMemory.GoalsOfItemsResultPart3)] =
      val refs = items.map(_.toString).toSet
      state.get.links
        .filter(l => l.kind == com.histmersion.domain.automation.Goal.LinkKind.QueueItem && refs(l.ref))
        .flatMap(l => goals.get(l.goal).flatMap(g => l.ref.toIntOption.map((_, g.goal.slug, g.goal.title))))
        .toList
        .sortBy((_, slug, title) => (title, slug))

    def links(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.LinksGoal): List[GoalLink] =
      state.get.links
        .filter(_.goal == goal)
        .sortBy(l => (kindOrder(l.kind), l.createdAt, l.ref))
        .toList
        .map(l =>
          GoalLink(
            l.kind.transformInto[com.histmersion.api.automation.Goal.Plans.LinkKind],
            l.ref,
            l.note,
            targets.title(l.kind, l.ref),
            AutomationState.text(l.createdAt),
            Option
              .when(l.kind == com.histmersion.domain.automation.Goal.LinkKind.QueueItem)(targets.queueList(l.ref))
              .flatten
          )
        )
    def link(
        goal: com.histmersion.domain.automation.goals.GoalService.InMemory.LinkGoal,
        kind: com.histmersion.domain.automation.Goal.LinkKind,
        ref: com.histmersion.domain.automation.goals.GoalService.InMemory.LinkRef,
        note: Option[com.histmersion.domain.automation.goals.GoalService.InMemory.LinkNote]
    ): com.histmersion.domain.automation.goals.GoalService.InMemory.LinkResult =
      goals.contains(goal) && {
        val at = now
        val _ = state.updateAndGet(
          _.modify(_.links).using { ls =>
            ls.indexWhere(l => l.goal == goal && l.kind == kind && l.ref == ref) match
              case -1 => ls :+ GoalLinkRecord(goal, kind, ref, note, at)
              case i  => ls.updated(i, ls(i).modify(_.note).using(note.orElse(_)))
          }
        )
        true
      }
    def refs(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.RefsGoal, kind: com.histmersion.domain.automation.Goal.LinkKind): List[com.histmersion.domain.automation.goals.GoalService.InMemory.RefsResult] =
      state.get.links.filter(l => l.goal == goal && l.kind == kind).sortBy(l => (l.createdAt, l.ref)).map(_.ref).toList

    def unlink(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.UnlinkGoal, kind: com.histmersion.domain.automation.Goal.LinkKind, ref: com.histmersion.domain.automation.goals.GoalService.InMemory.UnlinkRef): com.histmersion.domain.automation.goals.GoalService.InMemory.UnlinkResult =
      val before =
        state.getAndUpdate(_.modify(_.links).using(_.filterNot(l => l.goal == goal && l.kind == kind && l.ref == ref)))
      before.links.exists(l => l.goal == goal && l.kind == kind && l.ref == ref)
    def linkCounts(): Map[(com.histmersion.domain.automation.goals.GoalService.InMemory.LinkCountsResultPart1Part1, com.histmersion.domain.automation.goals.GoalService.InMemory.LinkCountsResultPart1Part2), com.histmersion.domain.automation.goals.GoalService.InMemory.LinkCountsResultPart2] =
      state.get.links
        .filter(l =>
          l.kind != com.histmersion.domain.automation.Goal.LinkKind.Article || targets
            .title(com.histmersion.domain.automation.Goal.LinkKind.Article, l.ref)
            .isDefined
        )
        .groupBy(l => (l.goal, l.kind.wire))
        .view
        .mapValues(_.size)
        .toMap
    def planned(): Set[com.histmersion.domain.automation.goals.GoalService.InMemory.PlannedResult] = goals.values.filter(_.plan.isDefined).map(_.goal.slug).toSet
    def planAndBudget(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.PlanAndBudgetGoal): Option[(Option[com.histmersion.domain.automation.goals.GoalService.InMemory.PlanAndBudgetResultPart1], Option[BigDecimal])] =
      goals.get(goal).map(r => (r.plan, r.budgetUsd))
    private def changeGoal(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.ChangeGoalGoal)(f: GoalRecord => GoalRecord): Unit =
      val _ = state.updateAndGet(_.modify(_.goals).using(gs => gs.get(goal).fold(gs)(r => gs.updated(goal, f(r)))))
    def setPlan(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.SetPlanGoal, planJson: com.histmersion.domain.automation.goals.GoalService.InMemory.SetPlanPlanJson): Unit =
      val at = now
      changeGoal(goal)(
        _.modify(_.plan).setTo(Some(planJson)).modify(_.planApprovedAt).setTo(Some(at)).modify(_.updatedAt).setTo(at)
      )
    def setBudget(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.SetBudgetGoal, usd: Option[BigDecimal]): Unit =
      val at = now
      changeGoal(goal)(_.modify(_.budgetUsd).setTo(usd).modify(_.updatedAt).setTo(at))
    private def view(r: GoalScheduleRecord): GoalSchedule =
      GoalSchedule(
        r.goal,
        r.kind.transformInto[com.histmersion.api.automation.Goal.Plans.ScheduleKind],
        r.cron,
        r.enabled,
        r.budgetUsd.map(_.toDouble),
        r.nextRunAt.map(AutomationState.text),
        None,
        None,
        None
      )
    def schedules(goal: Option[com.histmersion.domain.automation.goals.GoalService.InMemory.SchedulesGoal]): List[GoalSchedule] =
      state.get.schedules
        .filter(r => goal.forall(_ == r.goal))
        .sortBy(r => (r.nextRunAt.isEmpty, r.nextRunAt.getOrElse(Instant.EPOCH), r.goal, r.kind.wire))
        .toList
        .map(view)
    def setSchedule(
        goal: com.histmersion.domain.automation.goals.GoalService.InMemory.SetScheduleGoal,
        kind: ScheduleKind,
        cron: com.histmersion.domain.automation.goals.GoalService.InMemory.SetScheduleCron,
        enabled: com.histmersion.domain.automation.goals.GoalService.InMemory.SetScheduleEnabled,
        budgetUsd: Option[BigDecimal],
        next: Option[Instant]
    ): Unit =
      val row = GoalScheduleRecord(goal, kind, cron, enabled, budgetUsd, next)
      val _ = state.updateAndGet(_.modify(_.schedules).using { rs =>
        rs.indexWhere(r => r.goal == goal && r.kind == kind) match
          case -1 => rs :+ row
          case i  => rs.updated(i, row)
      })
    def removeSchedule(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.RemoveScheduleGoal, kind: ScheduleKind): com.histmersion.domain.automation.goals.GoalService.InMemory.RemoveScheduleResult =
      state
        .getAndUpdate(_.modify(_.schedules).using(_.filterNot(r => r.goal == goal && r.kind == kind)))
        .schedules
        .exists(r => r.goal == goal && r.kind == kind)
    def scheduleGoals(): List[com.histmersion.domain.automation.goals.GoalService.InMemory.ScheduleGoalsResult] = state.get.schedules.map(_.goal).distinct.sorted.toList
    def takeSchedulesIn(goal: com.histmersion.domain.automation.goals.GoalService.InMemory.TakeSchedulesInGoal)(using DbCon): List[GoalSchedule] =
      state
        .getAndUpdate(_.modify(_.schedules).using(_.filterNot(_.goal == goal)))
        .schedules
        .filter(_.goal == goal)
        .toList
        .map(view)

  object InMemory:
    type RenameMovedS = RenameMovedS.T
    object RenameMovedS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type GoalsResultPart1 = GoalsResultPart1.T
    object GoalsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedTextResult = UpdatedTextResult.T
    object UpdatedTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KindOrderResult = KindOrderResult.T
    object KindOrderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetSlug = GetSlug.T
    object GetSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChildrenSlug = ChildrenSlug.T
    object ChildrenSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutSlug = PutSlug.T
    object PutSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageStatus = PageStatus.T
    object PageStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageLimit = PageLimit.T
    object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PageOffset = PageOffset.T
    object PageOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotFoundSlug = NotFoundSlug.T
    object NotFoundSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchSlug = PatchSlug.T
    object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSlug = DeleteSlug.T
    object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsResultPart1 = GoalsOfItemsResultPart1.T
    object GoalsOfItemsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsResultPart2 = GoalsOfItemsResultPart2.T
    object GoalsOfItemsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsResultPart3 = GoalsOfItemsResultPart3.T
    object GoalsOfItemsResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalsOfItemsItems = GoalsOfItemsItems.T
    object GoalsOfItemsItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinksGoal = LinksGoal.T
    object LinksGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkResult = LinkResult.T
    object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkGoal = _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Goal
    val LinkGoal: _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Goal.type = _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Goal
    type LinkRef = _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Ref
    val LinkRef: _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Ref.type = _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Ref
    type LinkNote = _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Note
    val LinkNote: _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Note.type = _root_.com.histmersion.domain.automation.goals.GoalLinkRecord.Note
    type RefsResult = RefsResult.T
    object RefsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsGoal = RefsGoal.T
    object RefsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlinkResult = UnlinkResult.T
    object UnlinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlinkGoal = UnlinkGoal.T
    object UnlinkGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlinkRef = UnlinkRef.T
    object UnlinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkCountsResultPart1Part1 = LinkCountsResultPart1Part1.T
    object LinkCountsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkCountsResultPart1Part2 = LinkCountsResultPart1Part2.T
    object LinkCountsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkCountsResultPart2 = LinkCountsResultPart2.T
    object LinkCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlannedResult = PlannedResult.T
    object PlannedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanAndBudgetResultPart1 = PlanAndBudgetResultPart1.T
    object PlanAndBudgetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanAndBudgetGoal = PlanAndBudgetGoal.T
    object PlanAndBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeGoalGoal = ChangeGoalGoal.T
    object ChangeGoalGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetPlanGoal = SetPlanGoal.T
    object SetPlanGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetPlanPlanJson = SetPlanPlanJson.T
    object SetPlanPlanJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetBudgetGoal = SetBudgetGoal.T
    object SetBudgetGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SchedulesGoal = SchedulesGoal.T
    object SchedulesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetScheduleGoal = _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Goal
    val SetScheduleGoal: _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Goal.type = _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Goal
    type SetScheduleCron = _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Cron
    val SetScheduleCron: _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Cron.type = _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Cron
    type SetScheduleEnabled = _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Enabled
    val SetScheduleEnabled: _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Enabled.type = _root_.com.histmersion.domain.automation.goals.GoalScheduleRecord.Enabled
    type RemoveScheduleResult = RemoveScheduleResult.T
    object RemoveScheduleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RemoveScheduleGoal = RemoveScheduleGoal.T
    object RemoveScheduleGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ScheduleGoalsResult = ScheduleGoalsResult.T
    object ScheduleGoalsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TakeSchedulesInGoal = TakeSchedulesInGoal.T
    object TakeSchedulesInGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
