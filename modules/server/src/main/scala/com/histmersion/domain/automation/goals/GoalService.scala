package com.histmersion.domain.automation.goals

import com.histmersion.api.{ApiError, Goal, GoalPut, Write, YearFmt}
import com.histmersion.api.GoalPlans.{GoalLink, GoalSchedule}
import com.histmersion.db.{GoalLinkRepo, GoalRepo}
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.values.{GoalLinkKind, ScheduleKind}
import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/** The goals as the engine's other features reach them: a goal and its sub-goals, what hangs off it (its links, its
  * approved plan and run cap) and the schedules not moved into a watcher yet.
  */
trait GoalService:
  def get(slug: String): Option[Goal]

  /** Every goal, by slug. */
  def all(): List[Goal]

  /** A goal's direct sub-goals, by slug. */
  def children(slug: String): List[Goal]

  /** Create or replace a goal; a period that starts after it ends is refused. */
  def put(slug: String, in: GoalPut): Either[ApiError, Unit]

  /** A goal's links with the title of what each points at: sources, articles, queries, queue items; oldest first. */
  def links(goal: String): List[GoalLink]

  /** Link (an existing link keeps its note unless a new one is given); false = no such goal. */
  def link(goal: String, kind: GoalLinkKind, ref: String, note: Option[String]): Boolean
  def unlink(goal: String, kind: GoalLinkKind, ref: String): Boolean

  /** The refs of a goal's links of one kind (a sweep's queries, a currency check's sources), oldest first. */
  def refs(goal: String, kind: GoalLinkKind): List[String]

  /** Every goal's links counted by kind; an article link counts only while the article exists. */
  def linkCounts(): Map[(String, String), Int]

  /** The goals that record an approved plan. */
  def planned(): Set[String]

  /** The approved plan (its JSON) and the run cap; None = no such goal. */
  def planAndBudget(goal: String): Option[(Option[String], Option[BigDecimal])]
  def setPlan(goal: String, planJson: String): Unit
  def setBudget(goal: String, usd: Option[BigDecimal]): Unit

  /** The schedules not moved into a watcher (one goal's, or every one), the next due first. */
  def schedules(goal: Option[String]): List[GoalSchedule]
  def setSchedule(
      goal: String,
      kind: ScheduleKind,
      cron: String,
      enabled: Boolean,
      budgetUsd: Option[BigDecimal],
      next: Option[Instant]
  ): Unit
  def removeSchedule(goal: String, kind: ScheduleKind): Boolean

  /** The goals that still have schedules here. */
  def scheduleGoals(): List[String]

  /** A goal's schedules taken out, in the caller's transaction (moved into its watcher). */
  def takeSchedulesIn(goal: String)(using DbCon): List[GoalSchedule]

object GoalService:
  def apply(db: Transactor): GoalService = Implementation(db)

  final private class Implementation(db: Transactor) extends GoalService:
    def get(slug: String): Option[Goal] = db.read(GoalRepo.get(slug))
    def all(): List[Goal] = db.read(GoalRepo.all())
    def children(slug: String): List[Goal] = db.read(GoalRepo.children(slug))
    def put(slug: String, in: GoalPut): Either[ApiError, Unit] = db.transact(GoalRepo.put(slug, in))
    def links(goal: String): List[GoalLink] = db.read(GoalLinkRepo.links(goal))
    def link(goal: String, kind: GoalLinkKind, ref: String, note: Option[String]): Boolean =
      db.transact(GoalLinkRepo.link(goal, kind, ref, note))
    def unlink(goal: String, kind: GoalLinkKind, ref: String): Boolean =
      db.transact(GoalLinkRepo.unlink(goal, kind, ref))
    def refs(goal: String, kind: GoalLinkKind): List[String] = db.read(GoalLinkRepo.refsIn(goal, kind))
    def linkCounts(): Map[(String, String), Int] = db.read(GoalLinkRepo.linkCounts())
    def planned(): Set[String] = db.read(GoalLinkRepo.planned())
    def planAndBudget(goal: String): Option[(Option[String], Option[BigDecimal])] =
      db.read(GoalLinkRepo.planAndBudget(goal))
    def setPlan(goal: String, planJson: String): Unit = db.transact(GoalLinkRepo.setPlan(goal, planJson))
    def setBudget(goal: String, usd: Option[BigDecimal]): Unit = db.transact(GoalLinkRepo.setBudget(goal, usd))
    def schedules(goal: Option[String]): List[GoalSchedule] = db.read(GoalLinkRepo.schedules(goal))
    def setSchedule(
        goal: String,
        kind: ScheduleKind,
        cron: String,
        enabled: Boolean,
        budgetUsd: Option[BigDecimal],
        next: Option[Instant]
    ): Unit = db.transact(GoalLinkRepo.setSchedule(goal, kind, cron, enabled, budgetUsd, next))
    def removeSchedule(goal: String, kind: ScheduleKind): Boolean =
      db.transact(GoalLinkRepo.removeSchedule(goal, kind))
    def scheduleGoals(): List[String] = db.read(GoalLinkRepo.scheduleGoals())
    def takeSchedulesIn(goal: String)(using DbCon): List[GoalSchedule] = GoalLinkRepo.takeSchedulesIn(goal)

  /** The goals in memory; `clock` stands in for the database's `now()`, `targets` for the other domains' rows a link
    * names.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock, targets: GoalLinkTargets)
      extends GoalService:
    private def now: Instant = Instant.now(clock)
    private def goals: Map[String, GoalRecord] = state.get.goals
    private val Updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** `updated_at::text` in a UTC session: seconds, the fraction without its trailing zeros, the offset. */
    private def updatedText(at: Instant): String =
      val micros = at.getNano / 1000
      val fraction = if micros == 0 then "" else "." + f"$micros%06d".reverse.dropWhile(_ == '0').reverse
      s"${Updated.format(at)}$fraction+00"
    private def shown(r: GoalRecord): Goal = r.goal.modify(_.updatedAt).setTo(Some(updatedText(r.updatedAt)))
    private def kindOrder(k: GoalLinkKind): Int = k match
      case GoalLinkKind.Source      => 0
      case GoalLinkKind.Article     => 1
      case GoalLinkKind.SearchQuery => 2
      case _                        => 3

    def get(slug: String): Option[Goal] = goals.get(slug).map(shown)
    def all(): List[Goal] = goals.values.toList.sortBy(_.goal.slug).map(shown)
    def children(slug: String): List[Goal] = all().filter(_.parentSlug.contains(slug))
    def put(slug: String, in: GoalPut): Either[ApiError, Unit] =
      (in.periodStart, in.periodEnd) match
        case (Some(a), Some(b)) if a > b =>
          Left(
            ApiError(
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
    def links(goal: String): List[GoalLink] =
      state.get.links
        .filter(_.goal == goal)
        .sortBy(l => (kindOrder(l.kind), l.createdAt, l.ref))
        .toList
        .map(l =>
          GoalLink(
            l.kind,
            l.ref,
            l.note,
            targets.title(l.kind, l.ref),
            AutomationState.text(l.createdAt),
            Option.when(l.kind == GoalLinkKind.QueueItem)(targets.queueList(l.ref)).flatten
          )
        )
    def link(goal: String, kind: GoalLinkKind, ref: String, note: Option[String]): Boolean =
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
    def refs(goal: String, kind: GoalLinkKind): List[String] =
      state.get.links.filter(l => l.goal == goal && l.kind == kind).sortBy(l => (l.createdAt, l.ref)).map(_.ref).toList

    def unlink(goal: String, kind: GoalLinkKind, ref: String): Boolean =
      val before =
        state.getAndUpdate(_.modify(_.links).using(_.filterNot(l => l.goal == goal && l.kind == kind && l.ref == ref)))
      before.links.exists(l => l.goal == goal && l.kind == kind && l.ref == ref)
    def linkCounts(): Map[(String, String), Int] =
      state.get.links
        .filter(l => l.kind != GoalLinkKind.Article || targets.title(GoalLinkKind.Article, l.ref).isDefined)
        .groupBy(l => (l.goal, l.kind.wire))
        .view
        .mapValues(_.size)
        .toMap
    def planned(): Set[String] = goals.values.filter(_.plan.isDefined).map(_.goal.slug).toSet
    def planAndBudget(goal: String): Option[(Option[String], Option[BigDecimal])] =
      goals.get(goal).map(r => (r.plan, r.budgetUsd))
    private def changeGoal(goal: String)(f: GoalRecord => GoalRecord): Unit =
      val _ = state.updateAndGet(_.modify(_.goals).using(gs => gs.get(goal).fold(gs)(r => gs.updated(goal, f(r)))))
    def setPlan(goal: String, planJson: String): Unit =
      val at = now
      changeGoal(goal)(
        _.modify(_.plan).setTo(Some(planJson)).modify(_.planApprovedAt).setTo(Some(at)).modify(_.updatedAt).setTo(at)
      )
    def setBudget(goal: String, usd: Option[BigDecimal]): Unit =
      val at = now
      changeGoal(goal)(_.modify(_.budgetUsd).setTo(usd).modify(_.updatedAt).setTo(at))
    private def view(r: GoalScheduleRecord): GoalSchedule =
      GoalSchedule(
        r.goal,
        r.kind,
        r.cron,
        r.enabled,
        r.budgetUsd.map(_.toDouble),
        r.nextRunAt.map(AutomationState.text),
        None,
        None,
        None
      )
    def schedules(goal: Option[String]): List[GoalSchedule] =
      state.get.schedules
        .filter(r => goal.forall(_ == r.goal))
        .sortBy(r => (r.nextRunAt.isEmpty, r.nextRunAt.getOrElse(Instant.EPOCH), r.goal, r.kind.wire))
        .toList
        .map(view)
    def setSchedule(
        goal: String,
        kind: ScheduleKind,
        cron: String,
        enabled: Boolean,
        budgetUsd: Option[BigDecimal],
        next: Option[Instant]
    ): Unit =
      val row = GoalScheduleRecord(goal, kind, cron, enabled, budgetUsd, next)
      val _ = state.updateAndGet(_.modify(_.schedules).using { rs =>
        rs.indexWhere(r => r.goal == goal && r.kind == kind) match
          case -1 => rs :+ row
          case i  => rs.updated(i, row)
      })
    def removeSchedule(goal: String, kind: ScheduleKind): Boolean =
      state
        .getAndUpdate(_.modify(_.schedules).using(_.filterNot(r => r.goal == goal && r.kind == kind)))
        .schedules
        .exists(r => r.goal == goal && r.kind == kind)
    def scheduleGoals(): List[String] = state.get.schedules.map(_.goal).distinct.sorted.toList
    def takeSchedulesIn(goal: String)(using DbCon): List[GoalSchedule] =
      state
        .getAndUpdate(_.modify(_.schedules).using(_.filterNot(_.goal == goal)))
        .schedules
        .filter(_.goal == goal)
        .toList
        .map(view)
