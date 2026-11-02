package com.histmersion.domain.automation.goals

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.{ApprovalKind, ApprovalMode, Pipeline, Role}
import com.histmersion.api.GoalPlans.{LinkKind, Plan}
import com.histmersion.domain.PlanGoal.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.GoalRepo
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.domain.automation.worklists.{QueueScope, QueueService, WorkListControllers}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import javax.sql.DataSource
import com.histmersion.values.GoalStatus
import com.histmersion.api.VocabularyCodecs.given

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
  private def taken(s: String): Boolean =
    Transactor(ds).read(GoalRepo.exists(s)) || Transactor(ds).read(GoalRepo.renamedTo(s)).isDefined

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug = ctx.run.target
    for
      loaded <- ctx.code[Loaded]("load")(load(slug))
      proposed <- ctx.llm[PlanAnswer, Proposed](
        "propose",
        StepAgent.GoalPlanner,
        input(loaded, caps(ctx)),
        AnswerSchemas.plan
      )((a, last) =>
        PlanGoal
          .check(a, slug, taken, loaded.queries.toSet, caps(ctx), last)
          .map(Checked(_, _))
      )
      plan <- ctx.code[Plan]("verify")(Right(verify(ctx, proposed, slug)))
      line <-
        if plan.isEmpty then
          ctx.code[String]("close")(
            log(
              ctx,
              s"plan-goal $slug: nothing survived the checks " +
                s"(${plan.dropped.size} dropped); no approval was opened (agent run ${ctx.run.id.take(8)})"
            )
          )
        else
          for
            decided <- ctx.approval("approve", ApprovalKind.GoalPlan, slug, ApprovalMode.Manual)(
              Right(plan.asJson.noSpaces)
            )
            applied <- ctx.code[Applied]("apply")(apply(ctx, slug, plan, decided))
          yield applied.line
    yield line

  /** `agents.plan.*`, with the run's own sub-goal cap (the autopilot gives 0 at its depth limit). */
  private def caps(ctx: RunCtx): Caps =
    val c = cfg.plan.caps
    ctx.run.params.subGoals.fold(c)(n => c.copy(subGoals = math.max(0, math.min(n, c.subGoals))))

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(slug: String): Either[String, Loaded] =
    host.goal(slug).left.map(_.message).map { g =>
      val links = goals.links(slug)
      val vocab = (field: String) => host.vocabulary(field).take(VocabularySize)
      Loaded(
        g.slug,
        g.title,
        g.notes,
        g.tags,
        g.regions,
        g.periodStart,
        g.periodEnd,
        goals.children(slug).map(c => s"${c.slug} — ${c.title}"),
        links.filter(_.kind == LinkKind.SearchQuery).map(_.ref),
        links
          .filter(l => l.kind == LinkKind.Source || l.kind == LinkKind.Article)
          .map(l => s"${l.kind.wire} [[${l.ref}]]${l.title.fold("")(t => s" — $t")}"),
        vocab("topics"),
        vocab("tags")
      )
    }

  private def input(l: Loaded, caps: Caps): String =
    val period = (l.periodStart, l.periodEnd) match
      case (Some(a), Some(b)) => s"$a to $b (signed years, BCE negative)"
      case _                  => "not given"
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"- $x").mkString("\n", "\n", "")
    val goal = s"slug: ${l.slug}\ntitle: ${l.title}\nnotes: ${l.notes.getOrElse("none")}\n" +
      s"tags: ${l.tags.mkString(", ")}\nregions: ${l.regions.mkString(", ")}\nperiod: $period"
    s"Plan the research of this goal.\n\n${Prompt.data("goal", goal)}\n\n" +
      s"Its sub-goals: ${list(l.children)}\n\nIts queries: ${list(l.queries)}\n\nLinked to it: ${list(l.linked)}\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      (if caps.subGoals == 0 then "Propose NO sub-goals: this goal is split far enough; give queries and works. "
       else "") +
      s"Limits: at most ${caps.subGoals} sub-goals, ${caps.queries} queries, ${caps.works} works; " +
      s"a query is at most ${caps.queryChars} characters."

  /** Every DOI through the discover path (OpenAlex/Crossref). Offline, every work is dropped — a person never sees a
    * DOI code could not check.
    */
  private def verify(ctx: RunCtx, p: Proposed, slug: String): Plan =
    val plan = PlanGoal.verify(p, slug, host.workLookup)
    plan.dropped.foreach(d => ctx.warn(s"verify: $d"))
    plan

  private def log(ctx: RunCtx, line: String): Either[String, String] = Pipelines.log(workLists, ctx, line)

  /** The approved plan, applied: sub-goals (children of the goal), query links, works in the corpus linked, the others
    * queued for acquisition and linked by item. A rejected plan changes nothing. Idempotent (a re-run after a crash
    * finds what it made: goals are upserted, links are keyed, a DOI already in the queue is not added again).
    */
  private def apply(ctx: RunCtx, slug: String, plan: Plan, d: Decided): Either[String, Applied] =
    if !d.approved then
      log(ctx, PlanGoal.logLine(slug, plan, approved = false, 0, 0, 0, ctx.run.id)).map(Applied(_, 0, 0, 0))
    else
      ctx.write(
        s"apply the plan of goal $slug: ${plan.subGoals.size} sub-goal(s), ${plan.queries.size} query(ies), " +
          s"${plan.works.size} work(s)",
        plan.asJson
      )(applyApproved(ctx, slug, plan))(
        Right(
          Applied(
            s"plan-goal $slug: plan not applied (${AgentsMarkdown.DryRunNote}) (agent run ${ctx.run.id.take(8)})",
            0,
            0,
            0
          )
        )
      )

  private def applyApproved(ctx: RunCtx, slug: String, plan: Plan): Either[String, Applied] =
    val parent = host.goal(slug).toOption
    val created = plan.subGoals.filter { g =>
      if Transactor(ds).read(GoalRepo.exists(g.slug)) then {
        ctx.warn(s"apply: '${g.slug}' became a goal meanwhile; left as it is"); false
      } else
        host.putGoal(
          g.slug,
          GoalPut(
            g.title,
            GoalStatus.Planned,
            g.notes,
            g.tags,
            parent.toList.flatMap(_.regions),
            parent.flatMap(_.periodStart),
            parent.flatMap(_.periodEnd),
            Some(slug)
          )
        ) match
          case Right(_) => true
          case Left(e)  => ctx.warn(s"apply: sub-goal '${g.slug}' not created — ${e.message}"); false
    }
    plan.queries.foreach(q => goals.link(slug, LinkKind.SearchQuery, q, Some("from the approved plan")))
    val queued = workLists
      .kindOf("acquire")
      .toOption
      .toList
      .flatMap(queues.list(_, QueueScope.All))
      .flatMap(i => Scheduling.dois(i.text).map(_ -> i.id))
      .toMap
    val (inCorpus, outside) = plan.works.partition(w => w.inCorpus && w.citekey.isDefined)
    inCorpus.foreach(w => goals.link(slug, LinkKind.Source, w.citekey.get, Option(w.why).filter(_.nonEmpty)))
    val items = outside.flatMap { w =>
      queued
        .get(w.doi)
        .map(Right(_))
        .getOrElse(
          workLists
            .add(
              "acquire",
              QueueAdd(
                PlanGoal.acquireText(w, slug),
                topic = Some(s"goal $slug"),
                goal = Some(slug),
                job = Some(ctx.run.id)
              )
            )
            .map(_.id)
        ) match
        case Right(id) =>
          goals.link(slug, LinkKind.QueueItem, id.toString, Some(w.doi)); Some(id)
        case Left(e) => ctx.warn(s"apply: ${w.doi} not queued — ${e.message}"); None
    }
    goals.setPlan(slug, plan.asJson.noSpaces)
    log(ctx, PlanGoal.logLine(slug, plan, approved = true, created.size, inCorpus.size, items.size, ctx.run.id))
      .map(Applied(_, created.size, inCorpus.size, items.size))

object PlanGoalPipeline:
  /** How many of the most used topics and tags the planner is shown. */
  val VocabularySize = 40

  /** The goal as the planner sees it (checkpointed). `tags0` = the corpus's tags (`tags` are the goal's). */
  final case class Loaded(
      slug: String,
      title: String,
      notes: Option[String],
      tags: List[String],
      regions: List[String],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      children: List[String],
      queries: List[String],
      linked: List[String],
      topics: List[String],
      tags0: List[String]
  ) derives KindlingsCodecAsObject

  /** What applying the plan did. */
  final case class Applied(line: String, created: Int, linked: Int, queued: Int) derives KindlingsCodecAsObject
