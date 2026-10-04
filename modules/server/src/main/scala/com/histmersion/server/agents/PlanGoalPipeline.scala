package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{ApprovalKind, ApprovalMode, Pipeline, Role}
import com.histmersion.core.GoalPlans.{LinkKind, Plan}
import com.histmersion.core.PlanGoal.*
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.db.{GoalLinkRepo, GoalRepo}
import com.histmersion.server.Handlers
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*
import sttp.ai.core.agent.ResponseSchema

import javax.sql.DataSource

/** `plan-goal <goal>` (docs/research-embedded-agents.md §7 P3, §14): the planner proposes sub-goals,
  * search queries and candidate works for one goal, as a typed answer. Code checks the vocabulary
  * (slugs, DOIs, caps), looks every DOI up upstream through the discover path, and then the run WAITS
  * for a person: nothing is created before the plan is approved. On approval the run creates the
  * sub-goals, links the queries and the works already in the corpus, and queues the others for
  * acquisition (never twice: a DOI already in the queue is linked, not added).
  *
  * Steps: load · propose (LLM: `search`, `facets`, `get_source`, read only) · verify · approve
  * (blocking) · apply. */
final class PlanGoalPipeline(h: Handlers, ds: DataSource, cfg: AgentsConfig, prompts: Prompts,
    upstream: () => AgentUpstream) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.PlanGoal
  import PlanGoalPipeline.*

  private val Tools = List(ToolName.Search, ToolName.Facets, ToolName.GetSource)

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug = ctx.run.target
    for
      loaded   <- ctx.code[Loaded]("load")(load(slug))
      system   <- prompts.step(pipeline.wire, "plan").left.map(Halt.Fail(_))
      proposed <- ctx.llm[PlanAnswer, Proposed]("propose", Role.Planner, Prompt(system, input(loaded)), Tools,
                    ResponseSchema.derived[PlanAnswer]())((a, last) =>
                    PlanGoal.check(a, slug, GoalRepo.exists(ds, _), loaded.queries.toSet, cfg.plan.caps, last)
                      .map(Checked(_, _)))
      plan     <- ctx.code[Plan]("verify")(Right(verify(ctx, proposed, slug)))
      line     <-
        if plan.isEmpty then
          ctx.code[String]("close")(log(s"plan-goal $slug: nothing survived the checks " +
            s"(${plan.dropped.size} dropped); no approval was opened (agent run ${ctx.run.id.take(8)})"))
        else
          for
            decided <- ctx.approval("approve", ApprovalKind.GoalPlan, slug, ApprovalMode.Manual)(Right(plan.asJson.noSpaces))
            applied <- ctx.code[Applied]("apply")(apply(ctx, slug, plan, decided))
          yield applied.line
    yield line

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(slug: String): Either[String, Loaded] =
    h.getGoal(slug).left.map(_.message).map { g =>
      val links = GoalLinkRepo.links(ds, slug)
      val vocab = (field: String) =>
        h.facets(field, None, Filter()).toOption.toList.flatten.sortBy(-_.count).take(VocabularySize).map(_.value)
      Loaded(g.slug, g.title, g.notes, g.tags, g.regions, g.periodStart, g.periodEnd,
        GoalRepo.children(ds, slug).map(c => s"${c.slug} — ${c.title}"),
        links.filter(_.kind == LinkKind.Query).map(_.ref),
        links.filter(l => l.kind == LinkKind.Source || l.kind == LinkKind.Article)
          .map(l => s"${l.kind.wire} [[${l.ref}]]${l.title.fold("")(t => s" — $t")}"),
        vocab("topics"), vocab("tags"))
    }

  private def input(l: Loaded): String =
    val period = (l.periodStart, l.periodEnd) match
      case (Some(a), Some(b)) => s"$a to $b (signed years, BCE negative)"
      case _                  => "not given"
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"- $x").mkString("\n", "\n", "")
    val goal = s"slug: ${l.slug}\ntitle: ${l.title}\nnotes: ${l.notes.getOrElse("none")}\n" +
      s"tags: ${l.tags.mkString(", ")}\nregions: ${l.regions.mkString(", ")}\nperiod: $period"
    s"Plan the research of this goal.\n\n${Prompt.data("goal", goal)}\n\n" +
      s"Its sub-goals: ${list(l.children)}\n\nIts queries: ${list(l.queries)}\n\nLinked to it: ${list(l.linked)}\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      s"Limits: at most ${cfg.plan.maxSubGoals} sub-goals, ${cfg.plan.maxQueries} queries, ${cfg.plan.maxWorks} works; " +
      s"a query is at most ${cfg.plan.queryChars} characters."

  /** Every DOI through the discover path (OpenAlex/Crossref). Offline, every work is dropped — a
    * person never sees a DOI code could not check. */
  private def verify(ctx: RunCtx, p: Proposed, slug: String): Plan =
    val plan = PlanGoal.verify(p, slug, doi => upstream().meta(doi))
    plan.dropped.foreach(d => ctx.warn(s"verify: $d"))
    plan

  private def log(line: String): Either[String, String] =
    h.ops.queueAdd("log", QueueAdd(line)).left.map(_.message).map(_ => line)

  /** The approved plan, applied: sub-goals (children of the goal), query links, works in the corpus
    * linked, the others queued for acquisition and linked by item. A rejected plan changes nothing.
    * Idempotent (a re-run after a crash finds what it made: goals are upserted, links are keyed, a
    * DOI already in the queue is not added again). */
  private def apply(ctx: RunCtx, slug: String, plan: Plan, d: Decided): Either[String, Applied] =
    if !d.approved then log(PlanGoal.logLine(slug, plan, approved = false, 0, 0, 0, ctx.run.id)).map(Applied(_, 0, 0, 0))
    else
      val parent = h.getGoal(slug).toOption
      val created = plan.subGoals.filter { g =>
        if GoalRepo.exists(ds, g.slug) then { ctx.warn(s"apply: '${g.slug}' became a goal meanwhile; left as it is"); false }
        else
          h.putGoal(g.slug, GoalPut(g.title, GoalStatus.Planned, g.notes, g.tags, parent.toList.flatMap(_.regions),
            parent.flatMap(_.periodStart), parent.flatMap(_.periodEnd), Some(slug))) match
            case Right(_) => true
            case Left(e)  => ctx.warn(s"apply: sub-goal '${g.slug}' not created — ${e.message}"); false
      }
      plan.queries.foreach(q => GoalLinkRepo.link(ds, slug, LinkKind.Query, q, Some("from the approved plan")))
      val queued = h.ops.queueList("acquire", all = true).toOption.toList.flatMap(_.items)
        .flatMap(i => Scheduling.dois(i.text).map(_ -> i.id)).toMap
      val (inCorpus, outside) = plan.works.partition(w => w.inCorpus && w.citekey.isDefined)
      inCorpus.foreach(w => GoalLinkRepo.link(ds, slug, LinkKind.Source, w.citekey.get, Option(w.why).filter(_.nonEmpty)))
      val items = outside.flatMap { w =>
        queued.get(w.doi).map(Right(_)).getOrElse(
          h.ops.queueAdd("acquire", QueueAdd(PlanGoal.acquireText(w, slug), topic = Some(s"goal $slug"))).map(_.id)) match
          case Right(id) =>
            GoalLinkRepo.link(ds, slug, LinkKind.QueueItem, id.toString, Some(w.doi)); Some(id)
          case Left(e) => ctx.warn(s"apply: ${w.doi} not queued — ${e.message}"); None
      }
      GoalLinkRepo.setPlan(ds, slug, plan.asJson.noSpaces)
      log(PlanGoal.logLine(slug, plan, approved = true, created.size, inCorpus.size, items.size, ctx.run.id))
        .map(Applied(_, created.size, inCorpus.size, items.size))

object PlanGoalPipeline:
  /** How many of the most used topics and tags the planner is shown. */
  val VocabularySize = 40

  /** The goal as the planner sees it (checkpointed). `tags0` = the corpus's tags (`tags` are the goal's). */
  final case class Loaded(slug: String, title: String, notes: Option[String], tags: List[String], regions: List[String],
      periodStart: Option[Long], periodEnd: Option[Long], children: List[String], queries: List[String],
      linked: List[String], topics: List[String], tags0: List[String]) derives KindlingsCodecAsObject

  /** What applying the plan did. */
  final case class Applied(line: String, created: Int, linked: Int, queued: Int) derives KindlingsCodecAsObject
