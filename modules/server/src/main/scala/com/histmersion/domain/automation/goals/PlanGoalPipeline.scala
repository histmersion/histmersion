package com.histmersion
package domain.automation.goals
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.api.json.NewtypeCodecs.given
import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.domain.automation.workflows.Pipelines

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalMode, Pipeline, Role}
import com.histmersion.api.automation.Goal.Plans.{LinkKind, Plan}
import com.histmersion.domain.PlanGoal.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.domain.automation.worklists.QueueScope
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.util.json.JsonCodec

import javax.sql.DataSource
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Goal.Plans.GoalLink
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.Title
import com.histmersion.domain.automation.Run
import com.histmersion.api.automation.Queue
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.PlanGoal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator

/** `plan-goal <goal>` (docs/research-embedded-agents.md §7 P3, §14): the planner proposes sub-goals, search queries and
  * candidate works for one goal, as a typed answer. Code checks the vocabulary (slugs, DOIs, caps), looks every DOI up
  * upstream through the discover path, and then the run WAITS for a person: nothing is created before the plan is
  * approved. On approval the run creates the sub-goals, links the queries and the works already in the corpus, and
  * queues the others for acquisition (never twice: a DOI already in the queue is linked, not added).
  *
  * Steps: load · propose (LLM: `search`, `facets`, `get_source`, read only) · verify · approve (blocking) · apply. A
  * DRY run opens no approval (the plan is treated as approved) and `apply` records the plan it would apply instead of
  * applying it; the log line likewise.
  */
final class PlanGoalPipeline(
    host: AutomationHost,
    queues: QueueService,
    workLists: WorkListControllers,
    goals: GoalService,
    ds: DataSource,
    cfg: => AgentsConfig
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.PlanGoal
  import PlanGoalPipeline.*

  /** A sub-goal slug is taken by a goal OR by an old slug of one: a put to an old slug follows the alias (#1799) and
    * would replace the renamed goal.
    */
  private def taken(s: domain.automation.Goal.Slug): Boolean =
    Transactor(ds).read(GoalRepo.exists(s)) || Transactor(ds).read(GoalRepo.renamedTo(s)).isDefined

  def run(ctx: RunCtx)(using WritePermit)(using Creator.New): Either[Halt, PipelineDef.Summary] =
    DomainGoal.Slug.parse(ctx.run.target.value).left.map(Halt.failed).flatMap(runFor(ctx, _))

  private def runFor(ctx: RunCtx, slug: DomainGoal.Slug)(using
      WritePermit
  )(using Creator.New): Either[Halt, PipelineDef.Summary] =
    for
      loaded <- ctx.code[Loaded](AgentRepo.StepRecord.Name("load"))(load(slug))
      proposed <- ctx.llm[PlanAnswer, Proposed](
        AgentRepo.StepRecord.Name("propose"),
        StepAgent.GoalPlanner,
        Prompt.UserText(input(loaded, caps(ctx))),
        AnswerSchemas.plan
      )((a, last) =>
        PlanGoal
          .check(a, slug, taken, loaded.queries.toSet, caps(ctx), PlanGoal.LastRound(last.value))
          .map((p, warnings) => Checked(p, warnings.map(w => Checked.Warnings(w.sentence))))
      )
      plan <- ctx.code[Plan](AgentRepo.StepRecord.Name("verify"))(Right(verify(ctx, proposed, slug)))
      line <-
        if plan.isEmpty then
          ctx.code[String](AgentRepo.StepRecord.Name("close"))(
            log(
              ctx,
              s"plan-goal ${slug.value}: nothing survived the checks " +
                s"(${plan.dropped.size} dropped); no approval was opened (agent run ${ctx.run.id.value.take(8)})"
            )
          )
        else
          for
            decided <- ctx.byAnswer(AgentRepo.StepRecord.Name("propose"))(
              ctx.approval(
                AgentRepo.StepRecord.Name("approve"),
                ApprovalKind.GoalPlan,
                ApprovalQuery.Subject(slug.value),
                ApprovalMode.Manual
              )(
                Right(plan.asJson.noSpaces)
              )
            )
            applied <- ctx.code[Applied](AgentRepo.StepRecord.Name("apply"))(
              ctx.byAnswer(AgentRepo.StepRecord.Name("propose"))(apply(ctx, slug, plan, decided))
            )
          yield applied.line.value
    yield PipelineDef.Summary(line)

  /** `agents.plan.*`, with the run's own sub-goal cap (the autopilot gives 0 at its depth limit). */
  private def caps(ctx: RunCtx): Caps =
    val c = cfg.plan.caps
    ctx.run.params.subGoals.fold(c)(n =>
      c.copy(subGoals = Caps.SubGoals(math.max(0, math.min(n.value, c.subGoals.value))))
    )

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(slug: DomainGoal.Slug): Either[Problems, Loaded] =
    host.goal(slug).left.map(_.problems).map { g =>
      val links = goals.links(slug)
      val vocab = (field: String) => host.vocabulary(AutomationHost.VocabularyField(field)).take(VocabularySize)
      Loaded(
        DomainGoal.Slug.assume(g.slug.value),
        Title(g.title.value),
        g.notes.map(n => Loaded.Notes(n.value)),
        g.tags.map(t => Loaded.Tags(t.value)),
        g.regions.map(r => Loaded.Regions(r.value)),
        g.periodStart,
        g.periodEnd,
        goals.children(slug).map(c => Loaded.Children(s"${c.slug.value} — ${c.title.value}")),
        links.filter(_.kind == LinkKind.SearchQuery).map(l => PlanGoal.Query(l.ref.value)),
        links
          .filter(l => l.kind == LinkKind.Source || l.kind == LinkKind.Article)
          .map(l =>
            Loaded.Linked(s"${l.kind.wire.value} [[${l.ref.value}]]${l.title.fold("")(t => s" — ${t.value}")}")
          ),
        vocab("topics").map(v => Loaded.Topics(v.value)),
        vocab("tags").map(v => Loaded.Tags0(v.value))
      )
    }

  private def input(l: Loaded, caps: Caps): String =
    val period = (l.periodStart, l.periodEnd) match
      case (Some(a), Some(b)) => s"${a.value} to ${b.value} (signed years, BCE negative)"
      case _                  => "not given"
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"- $x").mkString("\n", "\n", "")
    val goal = s"slug: ${l.slug.value}\ntitle: ${l.title.value}\nnotes: ${l.notes.fold("none")(_.value)}\n" +
      s"tags: ${l.tags.map(_.value).mkString(", ")}\nregions: ${l.regions.map(_.value).mkString(", ")}\nperiod: $period"
    s"Plan the research of this goal.\n\n${Prompt.data("goal", goal)}\n\n" +
      s"Its sub-goals: ${list(l.children.map(_.value))}\n\nIts queries: ${list(l.queries.map(_.value))}\n\nLinked to it: ${list(l.linked.map(_.value))}\n\n" +
      s"The corpus's most used topics: ${l.topics.map(_.value).mkString(", ")}\nIts most used tags: ${l.tags0.map(_.value).mkString(", ")}\n\n" +
      (if caps.subGoals.value == 0 then "Propose NO sub-goals: this goal is split far enough; give queries and works. "
       else "") +
      s"Limits: at most ${caps.subGoals.value} sub-goals, ${caps.queries.value} queries, ${caps.works.value} works; " +
      s"a query is at most ${caps.queryChars.value} characters."

  /** Every DOI through the discover path (OpenAlex/Crossref). Offline, every work is dropped — a person never sees a
    * DOI code could not check.
    */
  private def verify(ctx: RunCtx, p: Proposed, slug: DomainGoal.Slug)(using WritePermit): Plan =
    val plan = PlanGoal.verify(p, slug, host.workLookup)
    plan.dropped.foreach(d => ctx.warn(s"verify: ${d.value}"))
    plan

  private def log(ctx: RunCtx, line: String): Either[Problems, String] =
    Pipelines.log(workLists, ctx, Pipelines.LogLine(line)).map(_.value)

  private def runId(ctx: RunCtx): Run.Id =
    Run.Id.assume(ctx.run.id.value)

  /** The approved plan, applied: sub-goals (children of the goal), query links, works in the corpus linked, the others
    * queued for acquisition and linked by item. A rejected plan changes nothing. Idempotent (a re-run after a crash
    * finds what it made: goals are upserted, links are keyed, a DOI already in the queue is not added again).
    */
  private def apply(ctx: RunCtx, slug: DomainGoal.Slug, plan: Plan, d: Decided)(using
      WritePermit
  )(using Creator.New): Either[Problems, Applied] =
    if !d.approved.value then
      log(
        ctx,
        PlanGoal
          .logLine(
            slug,
            plan,
            approved = PlanGoal.Approved(false),
            PlanGoal.CreatedCount(0),
            PlanGoal.LinkedCount(0),
            PlanGoal.QueuedCount(0),
            runId(ctx)
          )
          .value
      ).map(l =>
        Applied(PlanGoal.LogLine(l), PlanGoal.CreatedCount(0), PlanGoal.LinkedCount(0), PlanGoal.QueuedCount(0))
      )
    else
      ctx.write(
        s"apply the plan of goal ${slug.value}: ${plan.subGoals.size} sub-goal(s), ${plan.queries.size} query(ies), " +
          s"${plan.works.size} work(s)",
        plan.asJson
      )(applyApproved(ctx, slug, plan))(
        Right(
          Applied(
            PlanGoal.LogLine(
              s"plan-goal ${slug.value}: plan not applied (${AgentsMarkdown.DryRunNote}) (agent run ${ctx.run.id.value.take(8)})"
            ),
            PlanGoal.CreatedCount(0),
            PlanGoal.LinkedCount(0),
            PlanGoal.QueuedCount(0)
          )
        )
      )

  private def applyApproved(ctx: RunCtx, slug: DomainGoal.Slug, plan: Plan)(using
      WritePermit
  )(using Creator.New): Either[Problems, Applied] =
    val parent = host.goal(slug).toOption
    val created = plan.subGoals.filter { g =>
      DomainGoal.Slug.either(g.slug.value) match
        case Left(_) =>
          ctx.warn(s"apply: sub-goal '${g.slug.value}' is not a goal slug; not created"); false
        case Right(sub) =>
          if Transactor(ds).read(GoalRepo.exists(sub)) then {
            ctx.warn(s"apply: '${g.slug.value}' became a goal meanwhile; left as it is"); false
          } else
            host.putGoal(
              sub,
              Goal.Put(
                g.title,
                Goal.StatusValue(Goal.Status.Planned),
                g.notes,
                g.tags,
                parent.toList.flatMap(_.regions),
                parent.flatMap(_.periodStart),
                parent.flatMap(_.periodEnd),
                Some(Goal.Slug(slug.value))
              )
            ) match
              case Right(_) => true
              case Left(e)  =>
                ctx.warn(s"apply: sub-goal '${g.slug.value}' not created — ${e.problems.sentences.mkString("; ")}");
                false
    }
    plan.queries.foreach(q =>
      Unused(
        goals.link(
          slug,
          DomainGoal.LinkKind.SearchQuery,
          GoalLink.Ref(q.value),
          Some(GoalLink.Note("from the approved plan"))
        ),
        Unused.Why.Idempotent
      )
    )
    val queued = workLists
      .kindOf(WorkListControllers.KindOfList("acquire"))
      .toOption
      .toList
      .flatMap(queues.list(_, QueueScope.All))
      .flatMap(i => Scheduling.dois(i.text.value).map(d => (d.value: String) -> i.id))
      .toMap
    val (inCorpus, outside) = plan.works.partition(w => w.inCorpus.value && w.citekey.isDefined)
    inCorpus.foreach(w =>
      Unused(
        goals.link(
          slug,
          DomainGoal.LinkKind.Source,
          GoalLink.Ref(w.citekey.get.value),
          Option(w.why).filter(w => s"$w".nonEmpty).map(y => GoalLink.Note(s"$y"))
        ),
        Unused.Why.Idempotent
      )
    )
    val items = outside.flatMap { w =>
      queued
        .get(w.doi.value)
        .map(Right(_))
        .getOrElse(
          workLists
            .record(
              WorkListControllers.ListName("acquire"),
              QueueAdd(
                Queue.QueueItem.Text(PlanGoal.acquireText(w, slug).value),
                topic = Some(Queue.QueueItem.Topic(s"goal ${slug.value}")),
                goal = Some(Goal.Slug(slug.value)),
                job = Some(SpatialHierarchyResult.Job.Id(ctx.run.id.value))
              ),
              ctx.creator
            )
            .map(_.id)
        ) match
        case Right(id) =>
          goals.linkIfThere(
            slug,
            DomainGoal.LinkKind.QueueItem,
            GoalLink.Ref(id.value.toString),
            Some(GoalLink.Note(w.doi.value))
          );
          Some(id)
        case Left(e) => ctx.warn(s"apply: ${w.doi.value} not queued — ${e.problems.sentences.mkString("; ")}"); None
    }
    goals.setPlan(slug, BGoalRow.Plan(plan.asJson.noSpaces))
    log(
      ctx,
      PlanGoal
        .logLine(
          slug,
          plan,
          approved = PlanGoal.Approved(true),
          PlanGoal.CreatedCount(created.size),
          PlanGoal.LinkedCount(inCorpus.size),
          PlanGoal.QueuedCount(items.size),
          runId(ctx)
        )
        .value
    ).map(l =>
      Applied(
        PlanGoal.LogLine(l),
        PlanGoal.CreatedCount(created.size),
        PlanGoal.LinkedCount(inCorpus.size),
        PlanGoal.QueuedCount(items.size)
      )
    )

object PlanGoalPipeline:
  /** How many of the most used topics and tags the planner is shown. */
  val VocabularySize = 40

  /** The goal as the planner sees it (checkpointed). `tags0` = the corpus's tags (`tags` are the goal's). */
  final case class Loaded(
      slug: domain.automation.Goal.Slug,
      title: Title,
      notes: Option[Loaded.Notes],
      tags: List[Loaded.Tags],
      regions: List[Loaded.Regions],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      children: List[Loaded.Children],
      queries: List[PlanGoal.Query],
      linked: List[Loaded.Linked],
      topics: List[Loaded.Topics],
      tags0: List[Loaded.Tags0]
  ) derives JsonCodec

  object Loaded:
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Regions = Regions.T
    object Regions extends Newtype[String, Pure]
    type Children = Children.T
    object Children extends Newtype[String, Pure]
    type Linked = Linked.T
    object Linked extends Newtype[String, Pure]
    type Topics = Topics.T
    object Topics extends Newtype[String, Pure]
    type Tags0 = Tags0.T
    object Tags0 extends Newtype[String, Pure]

  /** What applying the plan did. */
  final case class Applied(
      line: PlanGoal.LogLine,
      created: PlanGoal.CreatedCount,
      linked: PlanGoal.LinkedCount,
      queued: PlanGoal.QueuedCount
  ) derives JsonCodec
