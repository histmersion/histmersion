package com.histmersion
package domain.automation.goals
import com.histmersion.domain.DeepTime.SignedYear
import io.github.iltotore.iron.RefinedType
import com.histmersion.util.problem.Problem

import io.scalaland.chimney.dsl.*

import com.histmersion.api.{Write, YearFmt}
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Goal as DomainGoal
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
import com.histmersion.api.personal.UnreadPage
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.lint.{tolerated, LintRule}

/** The goals as the engine's other features reach them: a goal and its sub-goals, what hangs off it (its links, its
  * approved plan and run cap) and the schedules not moved into a watcher yet.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait GoalService:
  def get(slug: domain.automation.Goal.Slug): Option[Goal]

  /** Every goal, by slug. */
  def all(): List[Goal]

  /** A goal's direct sub-goals, by slug. */
  def children(slug: domain.automation.Goal.Slug): List[Goal]

  /** Create or replace a goal; a period that starts after it ends is refused. */
  def put(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, Unit]

  /** One page of the goals (of one status, when given), by slug, with the total. */
  def page(
      status: Option[domain.automation.Goal.Status.Wire],
      limit: PageSize,
      offset: Offset
  ): Goal.List

  /** A goal's fields changed as given (a cap of 0 or less clears it; a bound given alone replaces that bound); no such
    * goal is a not-found error.
    */
  def patch(slug: domain.automation.Goal.Slug, p: Goal.Patch)(using WritePermit): Either[Api.Error, Unit]

  /** A goal removed; false when there was none. */
  def delete(slug: domain.automation.Goal.Slug)(using WritePermit): Boolean

  /** A new slug, the old one kept as an alias (V053): refused when there is no such goal, or the new slug is another
    * goal's or another goal's old one.
    */
  def rename(
      from: domain.automation.Goal.Slug,
      to: domain.automation.Goal.Slug
  )(using WritePermit): Either[Api.Error, Unit]

  /** The goals that link each of these work-list items: (item id, goal slug, goal title), an item's goals by title. */
  def goalsOfItems(
      items: List[GoalLinkRepo.ItemId]
  ): List[(GoalLinkRepo.ItemId, domain.automation.Goal.Slug, BGoalRow.Title)]

  /** A goal's links with the title of what each points at: sources, articles, queries, queue items; oldest first. */
  def links(goal: domain.automation.Goal.Slug): List[GoalLink]

  /** Link (an existing link keeps its note unless a new one is given); false = no such goal. */
  def link(
      goal: domain.automation.Goal.Slug,
      kind: domain.automation.Goal.LinkKind,
      ref: GoalLink.Ref,
      note: Option[GoalLink.Note]
  )(using WritePermit)(using Creator.New): Boolean
  def unlink(
      goal: domain.automation.Goal.Slug,
      kind: domain.automation.Goal.LinkKind,
      ref: GoalLink.Ref
  )(using WritePermit): Boolean

  /** The refs of a goal's links of one kind (a sweep's queries, a currency check's sources), oldest first. */
  def refs(
      goal: domain.automation.Goal.Slug,
      kind: domain.automation.Goal.LinkKind
  ): List[GoalLink.Ref]

  /** Every goal's links counted by kind; an article link counts only while the article exists. */
  def linkCounts(): Map[
    (domain.automation.Goal.Slug, domain.automation.Goal.LinkKind.Wire),
    RowCount
  ]

  /** The goals that record an approved plan. */
  def planned(): Set[domain.automation.Goal.Slug]

  /** The approved plan (its JSON) and the run cap; None = no such goal. */
  def planAndBudget(
      goal: domain.automation.Goal.Slug
  ): Option[(Option[BGoalRow.Plan], Option[BigDecimal])]
  def setPlan(goal: domain.automation.Goal.Slug, planJson: BGoalRow.Plan)(using WritePermit): Unit
  def setBudget(goal: domain.automation.Goal.Slug, usd: Option[BigDecimal])(using WritePermit): Unit

  /** The schedules not moved into a watcher (one goal's, or every one), the next due first. */
  def schedules(goal: Option[domain.automation.Goal.Slug]): List[GoalSchedule]
  def setSchedule(
      goal: domain.automation.Goal.Slug,
      kind: ScheduleKind,
      cron: GoalSchedule.Cron,
      enabled: GoalSchedule.Enabled,
      budgetUsd: Option[BigDecimal],
      next: Option[Instant]
  )(using WritePermit)(using Creator.New): Unit
  def removeSchedule(goal: domain.automation.Goal.Slug, kind: ScheduleKind)(using WritePermit): Boolean

  /** The goals that still have schedules here. */
  def scheduleGoals(): List[domain.automation.Goal.Slug]

  /** A goal's schedules taken out, in the caller's transaction (moved into its watcher). */
  def takeSchedulesIn(goal: domain.automation.Goal.Slug)(using DbCon): List[GoalSchedule]

  /** A goal created, never replaced, in the caller's transaction (a new research area): a slug in use, or an old slug
    * of another goal, is a conflict and nothing is written.
    */
  def createIn(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
      DbCon,
      WritePermit
  )(using Creator.New): Either[Api.Error, Unit]

object GoalService:

  def apply(db: Transactor, clock: Clock): GoalService = Implementation(db, clock)

  extension (goals: GoalService)
    /** [[GoalService.link]] by the server itself (a sweep, a plan): a goal deleted meanwhile is said in the log, the
      * link not written.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def linkIfThere(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind,
        ref: GoalLink.Ref,
        note: Option[GoalLink.Note]
    )(using WritePermit)(using Creator.New): Unit =
      if !goals.link(goal, kind, ref, note) then scribe.warn(s"goal $goal is gone: a link to it was not written")

  final private class Implementation(db: Transactor, clock: Clock) extends GoalService:
    def get(slug: domain.automation.Goal.Slug): Option[Goal] = db.read(GoalRepo.get(slug))
    def all(): List[Goal] = db.read(GoalRepo.all())
    def children(slug: domain.automation.Goal.Slug): List[Goal] = db.read(GoalRepo.children(slug))
    def put(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
        WritePermit
    )(using Creator.New): Either[Api.Error, Unit] =
      db.write:
        given CreatedAt = CreatedAt.now(clock)
        GoalRepo.put(slug, in)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page(
        status: Option[domain.automation.Goal.Status.Wire],
        limit: PageSize,
        offset: Offset
    ): Goal.List =
      db.read(GoalRepo.listIn(status.map(s => GoalRow.Status(s.value)), limit, offset))
    def patch(slug: domain.automation.Goal.Slug, p: Goal.Patch)(using WritePermit): Either[Api.Error, Unit] =
      db.write(GoalRepo.patchIn(slug, p))
    def delete(slug: domain.automation.Goal.Slug)(using WritePermit): Boolean = db.write(GoalRepo.deleteIn(slug))
    def rename(
        from: domain.automation.Goal.Slug,
        to: domain.automation.Goal.Slug
    )(using WritePermit): Either[Api.Error, Unit] = db.write(GoalRepo.renameIn(from.trimmed, to.trimmed))
    def goalsOfItems(
        items: List[GoalLinkRepo.ItemId]
    ): List[(GoalLinkRepo.ItemId, domain.automation.Goal.Slug, BGoalRow.Title)] =
      db.read(GoalLinkRepo.goalsOfItemsIn(items))
    def links(goal: domain.automation.Goal.Slug): List[GoalLink] = db.read(GoalLinkRepo.links(goal))
    def link(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind,
        ref: GoalLink.Ref,
        note: Option[GoalLink.Note]
    )(using WritePermit)(using Creator.New): Boolean =
      db.write(
        GoalLinkRepo.link(goal, kind.transformInto[Goal.Plans.LinkKind], ref, note)
      )
    def unlink(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind,
        ref: GoalLink.Ref
    )(using WritePermit): Boolean =
      db.write(
        GoalLinkRepo.unlink(goal, kind.transformInto[Goal.Plans.LinkKind], ref)
      )
    def refs(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind
    ): List[GoalLink.Ref] =
      db.read(GoalLinkRepo.refsIn(goal, kind.transformInto[Goal.Plans.LinkKind]))
    def linkCounts(): Map[
      (domain.automation.Goal.Slug, domain.automation.Goal.LinkKind.Wire),
      RowCount
    ] = db.read(GoalLinkRepo.linkCounts())
    def planned(): Set[domain.automation.Goal.Slug] = db.read(GoalLinkRepo.planned())
    def planAndBudget(
        goal: domain.automation.Goal.Slug
    ): Option[(Option[BGoalRow.Plan], Option[BigDecimal])] =
      db.read(GoalLinkRepo.planAndBudget(goal))
    def setPlan(goal: domain.automation.Goal.Slug, planJson: BGoalRow.Plan)(using WritePermit): Unit =
      db.write(GoalLinkRepo.setPlan(goal, planJson))
    def setBudget(goal: domain.automation.Goal.Slug, usd: Option[BigDecimal])(using WritePermit): Unit =
      db.write(GoalLinkRepo.setBudget(goal, usd))
    def schedules(goal: Option[domain.automation.Goal.Slug]): List[GoalSchedule] =
      db.read(GoalLinkRepo.schedules(goal))
    def setSchedule(
        goal: domain.automation.Goal.Slug,
        kind: ScheduleKind,
        cron: GoalSchedule.Cron,
        enabled: GoalSchedule.Enabled,
        budgetUsd: Option[BigDecimal],
        next: Option[Instant]
    )(using WritePermit)(using Creator.New): Unit = db.write(
      GoalLinkRepo.setSchedule(
        goal,
        kind.transformInto[Goal.Plans.ScheduleKind],
        cron,
        enabled,
        budgetUsd,
        next
      )
    )
    def removeSchedule(goal: domain.automation.Goal.Slug, kind: ScheduleKind)(using WritePermit): Boolean =
      db.write(
        GoalLinkRepo.removeSchedule(goal, kind.transformInto[Goal.Plans.ScheduleKind])
      )
    def scheduleGoals(): List[domain.automation.Goal.Slug] = db.read(GoalLinkRepo.scheduleGoals())
    def takeSchedulesIn(goal: domain.automation.Goal.Slug)(using DbCon): List[GoalSchedule] =
      GoalLinkRepo.takeSchedulesIn(goal)
    def createIn(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
        DbCon,
        WritePermit
    )(using Creator.New): Either[Api.Error, Unit] =
      given CreatedAt = CreatedAt.now(clock)
      GoalRepo.createIn(slug, in)

  /** The goals in memory; `clock` stands in for the database's `now()`, `targets` for the other domains' rows a link
    * names.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock, targets: GoalLinkTargets)
      extends GoalService:
    private def now: Instant = Instant.now(clock)
    private def goals: Map[AutomationState.GoalsKey, GoalRecord] = state.get.goals
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def key(slug: DomainGoal.Slug): AutomationState.GoalsKey = AutomationState.GoalsKey(slug.value)
    private val Updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** `updated_at::text` in a UTC session: seconds, the fraction without its trailing zeros, the offset. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def updatedText(at: Instant): Goal.UpdatedAt =
      val micros = at.getNano / 1000
      val fraction = if micros == 0 then "" else "." + f"$micros%06d".reverse.dropWhile(_ == '0').reverse
      Goal.UpdatedAt(s"${Updated.format(at)}$fraction+00")
    private def shown(r: GoalRecord): Goal = r.goal.modify(_.updatedAt).setTo(Some(updatedText(r.updatedAt)))
    private def kindOrder(k: domain.automation.Goal.LinkKind): Int = k match
      case domain.automation.Goal.LinkKind.Source      => 0
      case domain.automation.Goal.LinkKind.Article     => 1
      case domain.automation.Goal.LinkKind.SearchQuery => 2
      case _                                           => 3
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def refused(problem: Problem, code: String): Api.Error = Api.Error(problem, Some(Api.Error.Code(code)))

    /** As [[RangeSql.merge]] refuses it: the start, above the end. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def inverted[Y](slug: DomainGoal.Slug, a: Y, b: Y)(using
        RefinedType.Mirror[Y] { type BaseType <: Long | Int }
    ): Problem =
      Problem.Invalid.Inverted(
        Problem.Invalid.Year.of(a),
        Problem.Invalid.Year.of(b),
        Vector(Problem.Field(s"goal '${slug.value}'"))
      )

    def get(slug: domain.automation.Goal.Slug): Option[Goal] = goals.get(key(slug)).map(shown)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def all(): List[Goal] = goals.values.toList.sortBy(_.goal.slug.value).map(shown)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def children(slug: domain.automation.Goal.Slug): List[Goal] =
      all().filter(_.parentSlug.exists(_.value == slug.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def put(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
        WritePermit
    )(using Creator.New): Either[Api.Error, Unit] =
      (in.periodStart, in.periodEnd) match
        case (Some(a), Some(b)) if a > b =>
          Left(
            refused(inverted(slug, a, b), Write.CodeInvalid)
          )
        case (start, end) =>
          val at = now
          val goal =
            Goal(
              Goal.Slug(slug.value),
              in.title,
              in.status,
              in.notes,
              in.tags,
              in.regions,
              start,
              end,
              in.parentSlug,
              None
            )
          val _ = state.updateAndGet(
            _.modify(_.goals).using(gs =>
              gs.updated(
                key(slug),
                gs.get(key(slug))
                  .fold(GoalRecord(goal, None, None, None, at, at))(
                    _.modify(_.goal).setTo(goal).modify(_.updatedAt).setTo(at)
                  )
              )
            )
          )
          Right(())
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def page(
        status: Option[domain.automation.Goal.Status.Wire],
        limit: PageSize,
        offset: Offset
    ): Goal.List =
      val st = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
      val listed = all().filter(g => st.forall(_ == g.status.value))
      val from = offset.value.max(0)
      Goal.List.of(
        com.histmersion.api.RowCount(listed.size),
        listed.slice(from, from + limit.value.max(1)),
        UnreadPage.Offset(from)
      )

    private def notFound(slug: domain.automation.Goal.Slug) =
      refused(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(slug)), Write.CodeNotFound)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def patch(slug: domain.automation.Goal.Slug, p: Goal.Patch)(using WritePermit): Either[Api.Error, Unit] =
      goals.get(key(slug)).toRight(notFound(slug)).flatMap { r =>
        val g = r.goal
        val start = p.periodStart.orElse(g.periodStart)
        val end = p.periodEnd.orElse(g.periodEnd)
        (start, end) match
          case (Some(a), Some(b)) if a > b =>
            Left(
              refused(inverted(slug, a, b), Write.CodeInvalid)
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
            val budget = p.budgetUsd.fold(r.budgetUsd)(v => Option.when(v.value > 0)(BigDecimal(v.value)))
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

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def delete(slug: domain.automation.Goal.Slug)(using WritePermit): Boolean =
      val before = state.getAndUpdate(
        _.modify(_.goals)
          .using(_ - key(slug))
          .modify(_.links)
          .using(_.filterNot(_.goal.value == slug.value))
          .modify(_.schedules)
          .using(_.filterNot(_.goal.value == slug.value))
      )
      before.goals.contains(key(slug))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def rename(
        from: domain.automation.Goal.Slug,
        to: domain.automation.Goal.Slug
    )(using WritePermit): Either[Api.Error, Unit] =
      val (oldSlug, newSlug) = (from.trimmed, to.trimmed)
      val (old, now) = (oldSlug.value, newSlug.value)
      val aliasOf = state.get.goalAliases.get(AutomationState.GoalAliasesKey(now)).map(_.value)
      if !goals.contains(key(oldSlug)) then Left(notFound(oldSlug))
      else if goals.contains(key(newSlug)) then
        Left(
          refused(
            Problem.Conflict.AlreadyExists(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(newSlug)),
            Write.CodeInvalid
          )
        )
      else if aliasOf.exists(_ != old) then
        Left(
          refused(
            Problem.Conflict
              .Aliased(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(newSlug), Problem.Raw(s"${aliasOf.get}")),
            Write.CodeInvalid
          )
        )
      else
        val _ = state.updateAndGet { st =>
          val r = st.goals(AutomationState.GoalsKey(old))
          def moved(s: String) = if s == old then now else s
          st.modify(_.goals)
            .using(gs =>
              (gs - AutomationState.GoalsKey(old))
                .updated(AutomationState.GoalsKey(now), r.modify(_.goal.slug).setTo(Goal.Slug(now)))
                .view
                .mapValues(_.modify(_.goal.parentSlug).using(_.map(p => Goal.Slug(moved(p.value)))))
                .toMap
            )
            .modify(_.links)
            .using(_.map(_.modify(_.goal).using(g => GoalLinkRecord.Goal(moved(g.value)))))
            .modify(_.schedules)
            .using(_.map(_.modify(_.goal).using(g => GoalScheduleRecord.Goal(moved(g.value)))))
            .modify(_.goalAliases)
            .using(as =>
              (as - AutomationState.GoalAliasesKey(now)).view
                .mapValues(v => AutomationState.GoalAliasesValue(moved(v.value)))
                .toMap
                .updated(AutomationState.GoalAliasesKey(old), AutomationState.GoalAliasesValue(now))
            )
        }
        Right(())

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def goalsOfItems(
        items: List[GoalLinkRepo.ItemId]
    ): List[(GoalLinkRepo.ItemId, domain.automation.Goal.Slug, BGoalRow.Title)] =
      val refs = items.map(_.value.toString).toSet
      state.get.links
        .filter(l => l.kind == domain.automation.Goal.LinkKind.QueueItem && refs(l.ref.value))
        .flatMap(l =>
          goals
            .get(AutomationState.GoalsKey(l.goal.value))
            .flatMap(g =>
              l.ref.value.toIntOption.map(n =>
                (GoalLinkRepo.ItemId(n), DomainGoal.Slug.assume(g.goal.slug.value), BGoalRow.Title(g.goal.title.value))
              )
            )
        )
        .toList
        .sortBy(t => (t._3.value, t._2.value))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def links(goal: domain.automation.Goal.Slug): List[GoalLink] =
      state.get.links
        .filter(_.goal.value == goal.value)
        .sortBy(l => (kindOrder(l.kind), l.createdAt, l.ref.value, l.kind.wire))
        .toList
        .map(l =>
          GoalLink(
            l.kind.transformInto[Goal.Plans.LinkKind],
            GoalLink.Ref(l.ref.value),
            l.note.map(n => GoalLink.Note(n.value)),
            targets.title(l.kind, BGoalLinkRow.Ref(l.ref.value)).map(t => GoalLink.Title(t.value)),
            GoalLink.CreatedAt(AutomationState.text(l.createdAt).value),
            Option
              .when(l.kind == domain.automation.Goal.LinkKind.QueueItem)(
                targets.queueList(BGoalLinkRow.Ref(l.ref.value))
              )
              .flatten
              .map(q => GoalLink.Queue(q.value))
          )
        )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def link(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind,
        ref: GoalLink.Ref,
        note: Option[GoalLink.Note]
    )(using WritePermit)(using Creator.New): Boolean =
      goals.contains(key(goal)) && {
        val at = now
        val newNote = note.map(n => GoalLinkRecord.Note(n.value))
        val _ = state.updateAndGet(
          _.modify(_.links).using { ls =>
            ls.indexWhere(l => l.goal.value == goal.value && l.kind == kind && l.ref.value == ref.value) match
              case -1 =>
                ls :+ GoalLinkRecord(GoalLinkRecord.Goal(goal.value), kind, GoalLinkRecord.Ref(ref.value), newNote, at)
              case i => ls.updated(i, ls(i).modify(_.note).using(newNote.orElse(_)))
          }
        )
        true
      }
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def refs(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind
    ): List[GoalLink.Ref] =
      state.get.links
        .filter(l => l.goal.value == goal.value && l.kind == kind)
        .sortBy(l => (l.createdAt, l.ref.value))
        .map(l => GoalLink.Ref(l.ref.value))
        .toList

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def unlink(
        goal: domain.automation.Goal.Slug,
        kind: domain.automation.Goal.LinkKind,
        ref: GoalLink.Ref
    )(using WritePermit): Boolean =
      val before =
        state.getAndUpdate(
          _.modify(_.links)
            .using(_.filterNot(l => l.goal.value == goal.value && l.kind == kind && l.ref.value == ref.value))
        )
      before.links.exists(l => l.goal.value == goal.value && l.kind == kind && l.ref.value == ref.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def linkCounts(): Map[
      (domain.automation.Goal.Slug, domain.automation.Goal.LinkKind.Wire),
      RowCount
    ] =
      state.get.links
        .filter(l =>
          l.kind != domain.automation.Goal.LinkKind.Article || targets
            .title(domain.automation.Goal.LinkKind.Article, BGoalLinkRow.Ref(l.ref.value))
            .isDefined
        )
        .groupBy(l => (l.goal.value, l.kind.wire))
        .view
        .map((k, ls) => (DomainGoal.Slug.assume(k._1), k._2) -> RowCount(ls.size))
        .toMap
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def planned(): Set[domain.automation.Goal.Slug] =
      goals.values.filter(_.plan.isDefined).map(r => DomainGoal.Slug.assume(r.goal.slug.value)).toSet
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def planAndBudget(
        goal: domain.automation.Goal.Slug
    ): Option[(Option[BGoalRow.Plan], Option[BigDecimal])] =
      goals.get(key(goal)).map(r => (r.plan.map(p => BGoalRow.Plan(p.value)), r.budgetUsd))
    private def changeGoal(goal: domain.automation.Goal.Slug)(f: GoalRecord => GoalRecord): Unit =
      val _ =
        state.updateAndGet(_.modify(_.goals).using(gs => gs.get(key(goal)).fold(gs)(r => gs.updated(key(goal), f(r)))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def setPlan(goal: domain.automation.Goal.Slug, planJson: BGoalRow.Plan)(using WritePermit): Unit =
      val at = now
      changeGoal(goal)(
        _.modify(_.plan)
          .setTo(Some(GoalRecord.Plan(planJson.value)))
          .modify(_.planApprovedAt)
          .setTo(Some(at))
          .modify(_.updatedAt)
          .setTo(at)
      )
    def setBudget(goal: domain.automation.Goal.Slug, usd: Option[BigDecimal])(using WritePermit): Unit =
      val at = now
      changeGoal(goal)(_.modify(_.budgetUsd).setTo(usd).modify(_.updatedAt).setTo(at))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def view(r: GoalScheduleRecord): GoalSchedule =
      GoalSchedule(
        Goal.Slug(r.goal.value),
        r.kind.transformInto[Goal.Plans.ScheduleKind],
        GoalSchedule.Cron(r.cron.value),
        GoalSchedule.Enabled(r.enabled.value),
        r.budgetUsd.map(b => GoalSchedule.BudgetUsd(b.toDouble)),
        r.nextRunAt.map(t => GoalSchedule.NextRunAt(AutomationState.text(t).value)),
        None,
        None,
        None
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def schedules(goal: Option[domain.automation.Goal.Slug]): List[GoalSchedule] =
      state.get.schedules
        .filter(r => goal.forall(_.value == r.goal.value))
        .sortBy(r => (r.nextRunAt.isEmpty, r.nextRunAt.getOrElse(Instant.EPOCH), r.goal.value, r.kind.wire.value))
        .toList
        .map(view)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def setSchedule(
        goal: domain.automation.Goal.Slug,
        kind: ScheduleKind,
        cron: GoalSchedule.Cron,
        enabled: GoalSchedule.Enabled,
        budgetUsd: Option[BigDecimal],
        next: Option[Instant]
    )(using WritePermit)(using Creator.New): Unit =
      val row = GoalScheduleRecord(
        GoalScheduleRecord.Goal(goal.value),
        kind,
        GoalScheduleRecord.Cron(cron.value),
        GoalScheduleRecord.Enabled(enabled.value),
        budgetUsd,
        next
      )
      val _ = state.updateAndGet(_.modify(_.schedules).using { rs =>
        rs.indexWhere(r => r.goal.value == goal.value && r.kind == kind) match
          case -1 => rs :+ row
          case i  => rs.updated(i, row)
      })
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def removeSchedule(goal: domain.automation.Goal.Slug, kind: ScheduleKind)(using WritePermit): Boolean =
      state
        .getAndUpdate(_.modify(_.schedules).using(_.filterNot(r => r.goal.value == goal.value && r.kind == kind)))
        .schedules
        .exists(r => r.goal.value == goal.value && r.kind == kind)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def scheduleGoals(): List[domain.automation.Goal.Slug] =
      state.get.schedules.map(_.goal.value).distinct.sorted.toList.map(DomainGoal.Slug.assume(_))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def takeSchedulesIn(goal: domain.automation.Goal.Slug)(using DbCon): List[GoalSchedule] =
      state
        .getAndUpdate(_.modify(_.schedules).using(_.filterNot(_.goal.value == goal.value)))
        .schedules
        .filter(_.goal.value == goal.value)
        .toList
        .map(view)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def createIn(slug: domain.automation.Goal.Slug, in: Goal.Put)(using
        DbCon,
        WritePermit
    )(using Creator.New): Either[Api.Error, Unit] =
      val aliasOf = state.get.goalAliases.get(AutomationState.GoalAliasesKey(slug.value)).map(_.value)
      if goals.contains(key(slug)) then
        Left(Api.Error(Problem.Conflict.AlreadyExists(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(slug))))
      else
        aliasOf match
          case Some(to) =>
            Left(
              Api.Error(Problem.Conflict.Aliased(Problem.TypeName.of(Goal.Slug), Problem.Raw.of(slug), Problem.Raw(to)))
            )
          case None => put(slug, in)
