package com.histmersion
package domain.automation.agents
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.nonempty.NonEmptyVector

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.approvals.ProposalEvidence
import com.histmersion.api.automation.{AgentJobKinds, ListFilters}

import com.augustnagro.magnum.*
import com.histmersion.api.automation.ApprovalDigest
import com.histmersion.domain.automation.agents.RunQuery
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.util.database.{Jsonb, Keyset, ListSql}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Managers
import com.histmersion.domain.automation.Run
import com.histmersion.util.jobs.Job
import com.histmersion.domain.CodexRetention
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Stopped
import com.histmersion.domain.automation.Approval
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator
import com.histmersion.api.automation.Creation

/** The store of embedded agent runs (migrations V035, V040, V041): a run is a `jobs` row with kind `agent` (its options
  * in `params`), its steps are `agent_steps` (one row per attempt; a `done` row is the checkpoint a resume replays),
  * its trace is `agent_messages`, and what it proposed for a person to decide is `approvals`. Token and cost totals are
  * sums over the steps, never a second copy.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the agent tables and their row codecs")
object AgentRepo:
  type ActiveOnly = ActiveOnly.T
  object ActiveOnly extends Newtype[Boolean, Pure]
  type OutputChars = OutputChars.T
  object OutputChars extends Newtype[Int, Pure]
  type PageCursor = PageCursor.T
  object PageCursor extends Newtype[String, Pure]

  /** `jobs.params`: `{}` (and anything a later version wrote that this one cannot read) is every option's default
    * ([[Jsonb]]).
    */

  private given DbCodec[RunParams] = Jsonb.codec(RunParams(), dropNulls = Jsonb.DropNulls(true))
  private given DbCodec[Creation] = Jsonb.codec(Creation(Creation.Unreadable))

  final private case class RunRow(
      id: RunInfo.Id,
      pipeline: Option[RunInfo.Pipeline],
      target: Option[RunInfo.Target],
      status: RunInfo.Status,
      progress: Option[RunInfo.Progress],
      error: Option[NonEmptyVector[Problem]],
      model: Option[RunInfo.Model],
      budgetUsd: Option[BigDecimal],
      budgetTokens: Option[RunInfo.BudgetTokens],
      warnings: List[RunInfo.Warnings],
      createdAt: RunInfo.CreatedAt,
      updatedAt: RunInfo.UpdatedAt,
      tin: Usage.Input,
      tout: Usage.Output,
      tcr: Usage.CacheRead,
      tcw: Usage.CacheWrite,
      cost: BigDecimal,
      targetKind: Option[RunInfo.TargetKind],
      params: RunParams,
      acquireItem: Option[Queue.QueueItem.Id],
      goal: Option[Goal.Slug],
      parent: Option[RunInfo.Id],
      origin: Option[RunInfo.Origin],
      resumeAt: Option[RunInfo.ResumeAt],
      dryRun: RunInfo.DryRun,
      managerId: Option[Managers.ManagerInfo.Id],
      managerItem: Option[Managers.ManagerItem.Id],
      heartbeatAt: Option[RunInfo.HeartbeatAt],
      creator: Option[Creation]
  ) derives DbCodec

  final private case class StepRow(
      seq: StepInfo.Seq,
      name: StepInfo.Name,
      kind: StepInfo.Kind,
      attempt: StepInfo.Attempt,
      status: StepInfo.Status,
      model: Option[StepInfo.Model],
      promptHash: Option[StepInfo.PromptHash],
      tin: Usage.Input,
      tout: Usage.Output,
      tcr: Usage.CacheRead,
      tcw: Usage.CacheWrite,
      cost: BigDecimal,
      error: Option[NonEmptyVector[Problem]],
      output: Option[StepInfo.Output],
      startedAt: StepInfo.StartedAt,
      finishedAt: StepInfo.FinishedAt,
      actualModel: Option[StepInfo.ActualModel],
      wouldWrite: Option[Jsonb.JsonText],
      firstCallTokens: Option[StepInfo.FirstCallTokens],
      turns: Option[StepInfo.Turns]
  ) derives DbCodec

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[MessageInfo] = DbCodec.derived

  private val ts = sql"""'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** ONE projection of a run with its usage totals, then (in [[runSelect]]) the runs among the jobs. */
  private val runFrom =
    sql"""SELECT j.id, j.pipeline, j.target_id, j.status, j.progress, j.error, j.model, j.budget_usd,
              j.budget_tokens, j.warnings,
              to_char(j.created_at AT TIME ZONE 'UTC', $ts), to_char(j.updated_at AT TIME ZONE 'UTC', $ts),
              coalesce(u.tin, 0), coalesce(u.tout, 0), coalesce(u.tcr, 0), coalesce(u.tcw, 0), coalesce(u.cost, 0),
              j.target_kind, j.params::text, j.acquire_item, j.goal_slug, j.parent_job, j.origin,
              to_char(j.resume_at AT TIME ZONE 'UTC', $ts), j.dry_run, j.manager_id, j.manager_item,
              to_char(j.heartbeat_at AT TIME ZONE 'UTC', $ts), j.creator::text
       FROM jobs j
       LEFT JOIN LATERAL (
         SELECT sum(tokens_in)::bigint AS tin, sum(tokens_out)::bigint AS tout,
                sum(tokens_cache_read)::bigint AS tcr, sum(tokens_cache_write)::bigint AS tcw,
                sum(cost_usd) AS cost
         FROM agent_steps s WHERE s.job_id = j.id) u ON true"""
  private val runsOnlySql = sql" WHERE j.kind = ${AgentJobKinds.Agent.kind}"
  private val runSelect = sql"$runFrom$runsOnlySql"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def toInfo(r: RunRow): RunInfo =
    RunInfo(
      r.id,
      r.pipeline.getOrElse(RunInfo.Pipeline("")),
      r.target.getOrElse(RunInfo.Target("")),
      r.status,
      r.progress,
      r.error.map(Problems.asOne),
      r.model,
      Usage(r.tin, r.tout, r.tcr, r.tcw),
      RunInfo.CostUsd(r.cost.toDouble),
      RunInfo.BudgetUsd(r.budgetUsd.map(_.toDouble).getOrElse(0.0)),
      r.budgetTokens.getOrElse(RunInfo.BudgetTokens(0L)),
      r.warnings,
      r.createdAt,
      r.updatedAt,
      r.targetKind.getOrElse(RunInfo.TargetKind("")),
      r.params,
      r.acquireItem,
      r.goal,
      r.parent,
      r.origin,
      r.resumeAt,
      r.dryRun,
      r.managerId,
      r.managerItem,
      r.heartbeatAt,
      pausedFor = Option.when(r.status == RunState.Paused.wire)(Stopped.pausedFor(r.error.map(Problems.asOne))).flatten,
      creator = r.creator
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(id: RunInfo.Id)(using DbCon): Option[RunInfo] =
    sql"$runSelect AND j.id = $id".query[RunRow].run().headOption.map(toInfo)

  def list(
      status: Option[Run.State.Wire],
      limit: PageSize,
      goal: Option[domain.automation.Goal.Slug] = None
  )(using DbCon): Vector[RunInfo] =
    sql"$runSelect${runStatus(status)}${runGoal(goal)}${Keyset.orderBy(sql"j.")} LIMIT $limit"
      .query[RunRow]
      .run()
      .map(toInfo)

  /** One page of the newest-first runs ([[com.histmersion.api.Paging]]), `status` and `goal` as in [[list]]; `before` /
    * `upTo` are run ids. Left: a cursor that names no job.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def page(
      status: Option[Run.State.Wire],
      limit: PageSize,
      goal: Option[domain.automation.Goal.Slug] = None,
      before: Option[PageCursor] = None,
      upTo: Option[PageCursor] = None
  )(using DbCon): Either[Problem, (Vector[RunInfo], Boolean)] =
    page(
      RunQuery(
        status = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).map(RunQuery.Status(_)).toList,
        goal = goal.map(_.value.trim).filter(_.nonEmpty).map(RunQuery.Goal(_))
      ),
      limit,
      before,
      upTo
    )

  /** A run that predates `jobs.origin` was started by a person. */
  private val originExpr = sql"coalesce(j.origin, ${Origin.Person.wire})"

  /** ` AND (j.model is one of them OR a step of the run used one of them)` — `expr` maps a model to what is matched
    * (itself, or its provider).
    */
  private def usedModel(vs: List[String], expr: Frag => Frag): Frag =
    if vs.isEmpty then sql""
    else
      sql" AND (${expr(sql"j.model")} = ANY($vs::text[]) OR EXISTS (SELECT 1 FROM agent_steps ms WHERE ms.job_id = j.id AND ${expr(sql"ms.model")} = ANY($vs::text[])))"

  private def provider(col: Frag): Frag = sql"split_part($col, ':', 1)"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def runConds(q: RunQuery): ListSql.Conds = ListSql.Conds(
    List(
      "status" -> ListSql.anyOf(sql"j.status", q.status.map(_.value)),
      "pipeline" -> ListSql.anyOf(sql"j.pipeline", q.pipelines.map(_.value)),
      "manager" -> ListSql.anyOf(sql"j.manager_id", q.managers.map(_.value)),
      "goal" -> ListSql.equal(sql"j.goal_slug", q.goal.map(_.value)),
      "model" -> usedModel(q.models.map(_.value), identity),
      "provider" -> usedModel(q.providers.map(_.value), provider),
      "origin" -> ListSql.anyOf(originExpr, q.origins.map(_.value)),
      "time" -> ListSql.between(sql"j.created_at", q.since, q.until),
      "q" -> q.q.fold(sql"")(t => sql" AND j.target_id ILIKE ${ListSql.contains(t.value)}")
    )
  )

  /** One page of the runs that match `q`, newest first. Left: a cursor that names no job. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def page(
      q: RunQuery,
      limit: PageSize,
      before: Option[PageCursor],
      upTo: Option[PageCursor]
  )(using DbCon): Either[Problem, (Vector[RunInfo], Boolean)] =
    (before.toList ++ upTo.toList).find(c => !Keyset.existsOn(sql"jobs", c)) match
      case Some(c) => Left(Problem.NotFound.Entity(Problem.TypeName.of(Run.Id), Problem.Raw.of(c)))
      case None    =>
        val (rows, more) = Keyset.pageOn[RunRow, PageCursor, RunInfo.Id](
          runFrom,
          sql"$runsOnlySql${runConds(q).all}",
          Keyset.Table(sql"jobs", sql"j", sql"j."),
          limit,
          before,
          upTo,
          _.id
        )
        Right((rows.map(toInfo), more))

  /** The counts that go with a filtered page of runs: per state, and the pipeline, origin, manager, model and provider
    * facets — each under every filter but its own. A run's models are the one it was started with and every one its
    * steps reported; the provider is a model's `provider:` part.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def runFacets(q: RunQuery)(using
      DbCon
  ): (List[(Run.State.Wire, RowCount)], List[(String, List[(String, RowCount)])]) =
    val c = runConds(q)
    val facetMax = PageSize.applyUnsafe(ListFilters.FacetMax)
    def from(except: String) = sql" FROM jobs j$runsOnlySql${c.except(except)}"
    def used(except: String): Frag =
      sql" FROM (SELECT DISTINCT j.id, x.m FROM jobs j CROSS JOIN LATERAL (SELECT j.model AS m UNION SELECT s.model FROM agent_steps s WHERE s.job_id = j.id) x$runsOnlySql${c.except(except)} AND x.m IS NOT NULL) u WHERE true"
    (
      Keyset.countsOn(from("status"), sql"j.status").map((s, n) => (Run.State.Wire(s.value), n)),
      List(
        "pipeline" -> ListSql.facetOn(from("pipeline"), sql"j.pipeline", facetMax),
        "origin" -> ListSql.facetOn(from("origin"), originExpr, facetMax),
        "manager" -> ListSql.facetOn(from("manager"), sql"j.manager_id", facetMax),
        "model" -> ListSql.facetOn(used("model"), sql"u.m", facetMax),
        "provider" -> ListSql.facetOn(used("provider"), provider(sql"u.m"), facetMax, sql"count(DISTINCT u.id)")
      )
    )

  /** How many runs are in each state — of one goal, when given. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def stateCounts(
      goal: Option[domain.automation.Goal.Slug] = None
  )(using DbCon): List[(Run.State.Wire, RowCount)] =
    Keyset
      .countsOn(sql" FROM jobs j$runsOnlySql${runGoal(goal)}", sql"j.status")
      .map((s, n) => (Run.State.Wire(s.value), n))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def runStatus(status: Option[Run.State.Wire]): Frag =
    status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).fold(sql"")(s => sql" AND j.status = $s")

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def runGoal(goal: Option[domain.automation.Goal.Slug]): Frag =
    goal.map(_.value.trim).filter(_.nonEmpty).fold(sql"")(g => sql" AND j.goal_slug = $g")

  /** Every run of `p` on `target`, newest first (what auto-enrich asks before queueing another). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def forTarget(p: Pipeline, target: RunInfo.Target)(using DbCon): Vector[RunInfo] =
    sql"$runSelect AND j.pipeline = ${p.wire} AND j.target_id = $target ORDER BY j.created_at DESC, j.id"
      .query[RunRow]
      .run()
      .map(toInfo)

  /** The runs a manager dispatched, newest first (`active` = only those not settled yet: queued, running, paused or
    * waiting).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def forManager(
      managerId: Managers.ManagerInfo.Id,
      active: ActiveOnly = ActiveOnly(false),
      limit: PageSize = PageSize(200)
  )(using DbCon): Vector[RunInfo] =
    val open = if active.value then sql" AND j.status IN ('queued', 'running', 'paused', 'waiting')" else sql""
    sql"$runSelect AND j.manager_id = $managerId$open ORDER BY j.created_at DESC, j.id DESC LIMIT $limit"
      .query[RunRow]
      .run()
      .map(toInfo)

  /** Every run on an article or source `target` that has not settled (any pipeline, any origin): a manager does not
    * start a second writer on it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def unsettledOn(target: RunInfo.Target)(using DbCon): Vector[RunInfo] =
    sql"$runSelect AND j.target_id = $target AND j.status IN ('queued', 'running', 'paused', 'waiting')"
      .query[RunRow]
      .run()
      .map(toInfo)

  // ── steps: checkpoints and attempts ────────────────────────────────────────────────────────────

  /** The checkpoint of step `seq`: its name and recorded output, if an attempt finished. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def checkpoint(jobId: Job.Id, seq: StepInfo.Seq)(using DbCon): Option[(StepInfo.Name, Jsonb.JsonText)] =
    sql"""SELECT name, output::text FROM agent_steps
          WHERE job_id = $jobId AND seq = $seq AND status = ${StepStatus.Done.wire}
          ORDER BY attempt DESC LIMIT 1"""
      .query[(StepInfo.Name, Option[Jsonb.JsonText])]
      .run()
      .headOption
      .map((n, o) => (n, o.getOrElse(Jsonb.JsonText("null"))))

  /** How many attempts step `seq` already has (failed ones included) — a resumed step continues the count rather than
    * restarting it, so `max-retries` holds across a restart.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def attempts(jobId: Job.Id, seq: StepInfo.Seq)(using DbCon): StepInfo.Attempt =
    StepInfo.Attempt(
      sql"SELECT coalesce(max(attempt), 0) FROM agent_steps WHERE job_id = $jobId AND seq = $seq"
        .query[Int]
        .run()
        .head
    )

  /** One attempt. `actualModel`: what the provider reported serving; `wouldWrite`: a dry run's skipped writes (JSON
    * array of [[WouldWrite]]); `firstCallTokens`: the input of the attempt's first model call; `turns`: the model turns
    * it took.
    */
  final case class StepRecord(
      seq: StepRecord.Seq,
      attempt: StepRecord.Attempt,
      name: StepRecord.Name,
      kind: StepKind,
      status: StepStatus,
      output: Option[StepRecord.Output],
      promptHash: Option[StepRecord.PromptHash],
      model: Option[StepRecord.Model],
      usage: Usage,
      costUsd: BigDecimal,
      error: Option[Problem],
      startedAt: java.time.Instant,
      actualModel: Option[StepRecord.ActualModel] = None,
      wouldWrite: List[WouldWrite] = Nil,
      firstCallTokens: Option[StepRecord.FirstCallTokens] = None,
      turns: Option[StepRecord.Turns] = None
  )

  object StepRecord:
    type Seq = Seq.T
    object Seq extends Newtype[Int, Pure]
    type Attempt = Attempt.T
    object Attempt extends Newtype[Int, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Output = Output.T
    object Output extends Newtype[String, Pure]
    type PromptHash = PromptHash.T
    object PromptHash extends Newtype[String, Pure]
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends Newtype[String, Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends Newtype[Long, Pure]
    type Turns = Turns.T
    object Turns extends Newtype[Int, Pure]

  final case class MessageRecord(
      n: MessageRecord.N,
      role: MessageRecord.Role,
      content: Option[MessageRecord.Content],
      toolName: Option[MessageRecord.ToolName],
      toolInput: Option[MessageRecord.ToolInput],
      toolResult: Option[MessageRecord.ToolOutput],
      latencyMs: Option[MessageRecord.LatencyMs]
  )

  object MessageRecord:
    type N = N.T
    object N extends Newtype[Int, Pure]
    type Role = Role.T
    object Role extends Newtype[String, Pure]
    type Content = Content.T
    object Content extends Newtype[String, Pure]
    type ToolName = ToolName.T
    object ToolName extends Newtype[String, Pure]
    type ToolInput = ToolInput.T
    object ToolInput extends Newtype[String, Pure]
    type ToolOutput = ToolOutput.T
    object ToolOutput extends Newtype[String, Pure]
    type LatencyMs = LatencyMs.T
    object LatencyMs extends Newtype[Int, Pure]

  /** One attempt and its trace, in ONE transaction (a checkpoint without its trace, or the reverse, is never visible).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def record(jobId: Job.Id, s: StepRecord, messages: List[MessageRecord])(using DbCon): Unit =
    val started = s.startedAt.toString
    val would = Option.when(s.wouldWrite.nonEmpty)(Jsonb.write(s.wouldWrite))
    val error = s.error.map(NonEmptyVector.of(_))
    sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, output, prompt_hash, model,
                                   tokens_in, tokens_out, tokens_cache_read, tokens_cache_write, cost_usd,
                                   error, started_at, finished_at, actual_model, would_write, first_call_tokens, turns)
          VALUES ($jobId, ${s.seq}, ${s.attempt}, ${s.name}, ${s.kind.wire}, ${s.status.wire}, ${s.output}::jsonb,
                  ${s.promptHash}, ${s.model}, ${s.usage.input}, ${s.usage.output}, ${s.usage.cacheRead},
                  ${s.usage.cacheWrite}, ${s.costUsd}, $error, $started::timestamptz, now(), ${s.actualModel},
                  $would::jsonb, ${s.firstCallTokens}, ${s.turns})""".update.run()
    messages.foreach { m =>
      sql"""INSERT INTO agent_messages (job_id, seq, attempt, n, role, content, tool_name, tool_input,
                                        tool_result, latency_ms)
            VALUES ($jobId, ${s.seq}, ${s.attempt}, ${m.n}, ${m.role}, ${m.content}, ${m.toolName},
                    ${m.toolInput}, ${m.toolResult}, ${m.latencyMs})""".update.run()
    }
    Unused(sql"UPDATE jobs SET updated_at = now() WHERE id = $jobId".update.run(), Unused.Why.Idempotent)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def steps(jobId: Job.Id, outputChars: OutputChars)(using DbCon): List[StepInfo] =
    sql"""SELECT seq, name, kind, attempt, status, model, prompt_hash, tokens_in, tokens_out, tokens_cache_read,
                 tokens_cache_write, cost_usd, error, left(output::text, $outputChars),
                 to_char(started_at AT TIME ZONE 'UTC', $ts),
                 to_char(finished_at AT TIME ZONE 'UTC', $ts), actual_model, would_write::text,
                 first_call_tokens, turns
          FROM agent_steps WHERE job_id = $jobId ORDER BY seq, attempt""".query[StepRow].run().toList.map { r =>
      StepInfo(
        r.seq,
        r.name,
        r.kind,
        r.attempt,
        r.status,
        r.model,
        r.promptHash,
        Usage(r.tin, r.tout, r.tcr, r.tcw),
        StepInfo.CostUsd(r.cost.toDouble),
        r.error.map(Problems.asOne),
        r.output,
        r.startedAt,
        r.finishedAt,
        r.actualModel,
        r.wouldWrite.flatMap(Jsonb.read[List[WouldWrite]]).getOrElse(Nil),
        r.firstCallTokens,
        r.turns
      )
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def messages(jobId: Job.Id)(using DbCon): List[MessageInfo] =
    sql"""SELECT seq, n, role, content, tool_name, tool_input, tool_result, latency_ms
          FROM agent_messages WHERE job_id = $jobId ORDER BY seq, attempt, n""".query[MessageInfo].run().toList

  // ── spend ──────────────────────────────────────────────────────────────────────────────────────

  /** What one run has spent (every attempt counts — a rejected answer was still paid for). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def runSpend(jobId: Job.Id)(using DbCon): (Usage, BigDecimal) =
    sql"""SELECT coalesce(sum(tokens_in), 0)::bigint, coalesce(sum(tokens_out), 0)::bigint,
                 coalesce(sum(tokens_cache_read), 0)::bigint, coalesce(sum(tokens_cache_write), 0)::bigint,
                 coalesce(sum(cost_usd), 0)
          FROM agent_steps WHERE job_id = $jobId""".query[(Long, Long, Long, Long, BigDecimal)].run().head match
      case (i, o, cr, cw, c) => (Usage(Usage.Input(i), Usage.Output(o), Usage.CacheRead(cr), Usage.CacheWrite(cw)), c)

  /** Cost of every run since the start of the current UTC day and month — the global caps. */
  def spendDayMonth()(using DbCon): (BigDecimal, BigDecimal) =
    sql"""SELECT coalesce(sum(cost_usd) FILTER (WHERE finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'), 0),
                 coalesce(sum(cost_usd) FILTER (WHERE finished_at >= date_trunc('month', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'), 0)
          FROM agent_steps
          WHERE finished_at >= date_trunc('month', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"""
      .query[(BigDecimal, BigDecimal)]
      .run()
      .head

  // ── trace retention ────────────────────────────────────────────────────────────────────────────

  /** Delete traced messages older than `days` (0 or less = keep everything). The steps — checkpoints, tokens, cost —
    * are never pruned: a resume and the budget sums need them. Returns how many rows went.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pruneMessages(days: CodexRetention.KeepDays)(using DbCon): RowCount =
    if days.value <= 0 then RowCount(0)
    else
      RowCount(
        sql"""DELETE FROM agent_messages WHERE created_at < now() - make_interval(days => $days)""".update.run()
      )

  // ── approvals: what runs proposed for a person to decide (`ApprovalService` opens the connection) ────

  type ApprovalJobId = ApprovalJobId.T
  object ApprovalJobId extends Newtype[String, Pure]

  final case class ApprovalRow(
      id: ApprovalInfo.Id,
      jobId: Option[ApprovalJobId],
      kind: ApprovalInfo.Kind,
      citekey: Option[Citekey],
      status: Option[ApprovalInfo.Status],
      current: Option[ApprovalInfo.Current],
      edgeKind: Option[ApprovalInfo.EdgeKind],
      target: Option[ApprovalInfo.Target],
      proposal: Jsonb.JsonText,
      state: ApprovalInfo.State,
      note: Option[ApprovalInfo.Note],
      result: Option[ApprovalInfo.Result],
      createdAt: ApprovalInfo.CreatedAt,
      decidedAt: Option[ApprovalInfo.DecidedAt],
      subject: Option[ApprovalInfo.Subject],
      seq: Option[StepInfo.Seq],
      creator: Option[Creation] = None
  ) derives DbCodec

  private val approvalSelect =
    sql"""SELECT id, job_id, kind, citekey, status, current_status, edge_kind, target, proposal::text, state, note,
              result, to_char(created_at AT TIME ZONE 'UTC', $ts), to_char(decided_at AT TIME ZONE 'UTC', $ts),
              subject, seq, creator::text
       FROM approvals"""

  /** The payload decides how a row reads: a source proposal, a goal's plan, or an article draft. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def info(r: ApprovalRow): ApprovalInfo =
    val base = ApprovalInfo(
      r.id,
      r.jobId.map(j => RunInfo.Id(j.value)),
      r.kind,
      r.citekey.getOrElse(Citekey("")),
      r.status,
      r.current,
      r.edgeKind,
      r.target,
      Agents.ApprovalInfo.Evidence(""),
      None,
      r.state,
      r.note,
      r.result,
      r.createdAt,
      r.decidedAt,
      r.subject,
      creator = r.creator
    )
    ApprovalKind.parse(r.kind) match
      case Some(ApprovalKind.GoalPlan) =>
        val p = Jsonb.read[Goal.Plans.Plan](r.proposal)
        base
          .modify(_.plan)
          .setTo(p)
          .modify(_.evidence)
          .setTo(ApprovalInfo.Evidence(p.map(_.rationale.value).getOrElse("")))
      case Some(ApprovalKind.ArticlePublish) =>
        val d = Jsonb.read[Goal.Plans.ArticleDraft](r.proposal)
        base
          .modify(_.article)
          .setTo(d)
          .modify(_.evidence)
          .setTo(ApprovalInfo.Evidence(d.flatMap(_.summary).map(_.value).getOrElse("")))
      case Some(ApprovalKind.HintEffect) =>
        val p = Jsonb.read[Managers.HintProposal](r.proposal)
        base
          .modify(_.hintEffect)
          .setTo(p)
          .modify(_.evidence)
          .setTo(
            ApprovalInfo.Evidence(
              p.map(x => Option(s"${x.why}").filter(_.nonEmpty).getOrElse(x.text.value)).getOrElse("")
            )
          )
      case _ =>
        base.withArticleProposal(Jsonb.read[Json](r.proposal)).getOrElse {
          val p = Jsonb.read[ProposalEvidence](r.proposal)
          base
            .modify(_.evidence)
            .setTo(ApprovalInfo.Evidence(p.flatMap(_.evidence).map(_.value).getOrElse("")))
            .modify(_.quote)
            .setTo(p.flatMap(_.quote).map(q => ApprovalInfo.Quote(q.value)))
        }

  /** The payload an approval was recorded with, as stored, and the creator that proposed it. */
  def payload(id: ApprovalInfo.Id)(using DbCon): Option[(Jsonb.JsonText, Jsonb.JsonText)] =
    sql"SELECT proposal::text, creator::text FROM approvals WHERE id = $id"
      .query[(Jsonb.JsonText, Jsonb.JsonText)]
      .run()
      .headOption

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def approval(id: ApprovalInfo.Id)(using DbCon): Option[ApprovalInfo] =

    sql"$approvalSelect WHERE id = $id"
      .query[ApprovalRow]
      .run()
      .headOption
      .map(info)

  /** Newest first; `state` narrows (none = every state). */
  def approvals(state: Option[ApprovalState], limit: PageSize)(using DbCon): List[ApprovalInfo] =
    val where = state.fold(sql"")(s => sql" WHERE state = ${s.wire}")
    sql"$approvalSelect$where ORDER BY created_at DESC, id DESC LIMIT $limit".query[ApprovalRow].run().toList.map(info)

  /** One page of the newest-first approvals ([[com.histmersion.api.Paging]]), `state` as in [[approvals]]; `before` /
    * `upTo` are approval ids. Left: a cursor that is not one.
    */
  def approvalPage(
      state: Option[ApprovalState],
      limit: PageSize,
      before: Option[PageCursor] = None,
      upTo: Option[PageCursor] = None
  )(using DbCon): Either[Problem, (List[ApprovalInfo], Boolean)] =
    approvalPage(ApprovalQuery(state), limit, before, upTo)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def approvalConds(q: ApprovalQuery): ListSql.Conds =
    val subject = q.subject.fold(sql"") { t =>
      val p = ListSql.contains(t.value)
      sql" AND (subject ILIKE $p OR citekey ILIKE $p OR target ILIKE $p OR EXISTS (SELECT 1 FROM jobs sj WHERE sj.id = approvals.job_id AND (sj.goal_slug ILIKE $p OR sj.manager_id ILIKE $p)))"
    }
    val goal = q.goal.fold(sql"")(g =>
      sql" AND (subject = $g OR EXISTS (SELECT 1 FROM jobs gj WHERE gj.id = approvals.job_id AND gj.goal_slug = $g))"
    )
    ListSql.Conds(
      List(
        "state" -> q.state.fold(sql"")(s => sql" AND state = ${s.wire}"),
        "kind" -> ListSql.anyOf(sql"kind", q.kinds.map(_.wire.value)),
        "subject" -> subject,
        "goal" -> goal,
        "decidedBy" -> ListSql.anyOf(ApprovalQuery.decidedByExpr, q.decidedBy.map(_.wire.value))
      )
    )

  /** One page of the approvals that match `q`, newest first ([[com.histmersion.api.Paging]]). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def approvalPage(
      q: ApprovalQuery,
      limit: PageSize,
      before: Option[PageCursor],
      upTo: Option[PageCursor]
  )(using DbCon): Either[Problem, (List[ApprovalInfo], Boolean)] =
    def cursor(c: PageCursor): Either[Problem, Long] =
      c.value.trim.toLongOption
        .filter(Keyset.existsOn(sql"approvals", _))
        .toRight(Problem.NotFound.Entity(Problem.TypeName.of(Approval.Id), Problem.Raw.of(c)))
    for
      b <- before.fold(Right(None))(cursor(_).map(Some(_)))
      u <- upTo.fold(Right(None))(cursor(_).map(Some(_)))
    yield
      val where = sql" WHERE true${approvalConds(q).all}"
      val (rows, more) = Keyset
        .pageOn[ApprovalRow, Long, ApprovalInfo.Id](
          approvalSelect,
          where,
          Keyset.Table(sql"approvals"),
          limit,
          b,
          u,
          _.id
        )
      (rows.toList.map(info), more)

  /** The counts that go with a filtered page: per state (under every filter but the state) and the `kind` and
    * `decidedBy` facets (each under every filter but its own).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def approvalFacets(q: ApprovalQuery)(using
      DbCon
  ): (List[(Approval.State.Wire, RowCount)], List[(String, List[(String, RowCount)])]) =
    val c = approvalConds(q)
    val facetMax = PageSize.applyUnsafe(ListFilters.FacetMax)
    def from(except: String) = sql" FROM approvals WHERE true${c.except(except)}"
    (
      Keyset.countsOn(from("state"), sql"state").map((s, n) => (Approval.State.Wire(s.value), n)),
      List(
        "kind" -> ListSql.facetOn(from("kind"), sql"kind", facetMax),
        "decidedBy" -> ListSql.facetOn(from("decidedBy"), ApprovalQuery.decidedByExpr, facetMax)
      )
    )

  /** How many approvals are in each state. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def approvalCounts()(using DbCon): List[(Approval.State.Wire, RowCount)] =
    Keyset
      .countsOn(sql" FROM approvals", sql"state")
      .map((s, n) => (Approval.State.Wire(s.value), n))

  /** The approval step `seq` of run `jobId` waits on, opened if it is not there yet (a resumed run finds the one it
    * opened before, never a second). `state` = approved with a `result` records one applied at once (an `auto` mode).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def openForStep(
      jobId: Job.Id,
      seq: StepInfo.Seq,
      kind: ApprovalKind,
      subject: ApprovalInfo.Subject,
      payload: Jsonb.JsonText,
      state: ApprovalState,
      result: Option[ApprovalInfo.Result],
      now: java.time.Instant
  )(using DbCon)(using creator: Creator.New): ApprovalInfo =
    val decided = Option.when(state != ApprovalState.Open)(now.toString)
    Unused(
      sql"""INSERT INTO approvals (job_id, seq, kind, subject, proposal, state, result, decided_at, creator)
          VALUES ($jobId, $seq, ${kind.wire}, $subject, $payload::jsonb, ${state.wire}, $result, $decided::timestamptz, ${StoredCreator
          .column(creator)}::jsonb)
          ON CONFLICT (job_id, seq) WHERE seq IS NOT NULL DO NOTHING""".update.run(),
      Unused.Why.Idempotent
    )
    forStep(jobId, seq).get

  /** A proposal about a goal or an article (`subject`) that the run does NOT wait for (an inferred filter, a plan of
    * articles): recorded `open` for a person, or `approved` with its `result` when it was applied at once (an `auto`
    * mode).
    */
  def openSubject(
      jobId: Option[Job.Id],
      kind: ApprovalKind,
      subject: ApprovalInfo.Subject,
      payload: Jsonb.JsonText,
      state: ApprovalState,
      result: Option[ApprovalInfo.Result],
      now: java.time.Instant
  )(using DbCon)(using creator: Creator.New): ApprovalInfo =
    val decided = Option.when(state != ApprovalState.Open)(now.toString)
    val id =
      sql"""INSERT INTO approvals (job_id, kind, subject, proposal, state, result, decided_at, creator)
            VALUES ($jobId, ${kind.wire}, $subject, $payload::jsonb, ${state.wire}, $result, $decided::timestamptz, ${StoredCreator
          .column(creator)}::jsonb)
            RETURNING id""".query[ApprovalInfo.Id].run().head
    approval(id).get

  /** The approvals run `jobId` opened (of `kind`, when given), oldest first. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def approvalsOf(jobId: Job.Id, kind: Option[ApprovalKind] = None)(using DbCon): List[ApprovalInfo] =
    sql"$approvalSelect WHERE job_id = $jobId${kind.fold(sql"")(k => sql" AND kind = ${k.wire}")} ORDER BY id"
      .query[ApprovalRow]
      .run()
      .toList
      .map(info)

  /** The approvals of `kind` the autopilot APPROVED since `since` (their note starts with `autopilot: approved`) — what
    * its daily caps count.
    */
  @tolerated(LintRule.TotalOrder, "approvals, read through a shared projection: id is their key")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def autoApprovedSince(kind: ApprovalKind, since: java.time.Instant)(using DbCon): List[ApprovalInfo] =
    val t = since.toString
    sql"$approvalSelect WHERE kind = ${kind.wire} AND note LIKE 'autopilot: approved%' AND decided_at >= $t::timestamptz ORDER BY id"
      .query[ApprovalRow]
      .run()
      .toList
      .map(info)

  /** The OPEN approvals of the given kinds, oldest first (what the supervisor's checks look at). */
  @tolerated(LintRule.TotalOrder, "approvals, read through a shared projection: id is their key")
  def openOfKinds(kinds: List[ApprovalKind], limit: PageSize)(using DbCon): List[ApprovalInfo] =
    sql"$approvalSelect WHERE state = 'open' AND kind = ANY(${kinds.map(_.wire)}::text[]) ORDER BY id LIMIT $limit"
      .query[ApprovalRow]
      .run()
      .toList
      .map(info)

  /** Every OPEN approval, light (id, kind, subject, created), oldest first — what the digest groups. */
  def openLight()(using DbCon): List[ApprovalDigest.Open] =
    sql"""SELECT id, kind, subject, to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
          FROM approvals WHERE state = 'open' ORDER BY id"""
      .query[(Long, String, Option[String], String)]
      .run()
      .toList
      .map((i, k, s, c) =>
        ApprovalDigest.Open(
          ApprovalDigest.Open.Id(i),
          ApprovalDigest.Open.Kind(k),
          s.map(ApprovalDigest.Open.Subject(_)),
          ApprovalDigest.Open.CreatedAt(c)
        )
      )

  /** How many approvals of `kind` are open (the backlog a scaled daily cap reads). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def openCount(kind: ApprovalKind)(using DbCon): RowCount =
    RowCount(
      sql"SELECT count(*)::int FROM approvals WHERE state = 'open' AND kind = ${kind.wire}".query[Int].run().head
    )

  /** Open approvals per subject (a goal or an article slug). */
  def openBySubject()(using DbCon): Map[ApprovalInfo.Subject, RowCount] =
    sql"""SELECT subject, count(*)::int FROM approvals WHERE state = 'open' AND subject IS NOT NULL GROUP BY subject"""
      .query[(String, Int)]
      .run()
      .map((s, n) => ApprovalInfo.Subject(s) -> RowCount(n))
      .toMap

  /** The approval step `seq` of run `jobId` opened, if it did. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def forStep(jobId: Job.Id, seq: StepInfo.Seq)(using DbCon): Option[ApprovalInfo] =

    sql"$approvalSelect WHERE job_id = $jobId AND seq = $seq"
      .query[ApprovalRow]
      .run()
      .headOption
      .map(info)

  /** A cancelled run's open approvals: rejected, so nobody decides for a run that is gone. The result is a line of
    * text, `why`'s sentence.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def closeForRun(jobId: Job.Id, why: Problem)(using DbCon): RowCount =
    val line = why.sentence
    RowCount(
      sql"""UPDATE approvals SET state = ${ApprovalState.Rejected.wire}, result = $line, decided_at = now()
          WHERE job_id = $jobId AND seq IS NOT NULL AND state = ${ApprovalState.Open.wire}""".update.run()
    )

  /** Compare-and-set an OPEN approval to its decision. `false` when it had already been decided. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def decide(
      id: ApprovalInfo.Id,
      to: ApprovalState,
      note: Option[ApprovalInfo.Note],
      result: Option[ApprovalInfo.Result]
  )(using DbCon): Boolean =
    sql"""UPDATE approvals SET state = ${to.wire}, note = $note, result = $result, decided_at = now()
          WHERE id = $id AND state = ${ApprovalState.Open.wire}""".update.run() > 0

  /** After [[decide]]: the final state and what applying the decision did. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def settle(id: ApprovalInfo.Id, to: ApprovalState, result: ApprovalInfo.Result)(using DbCon): Unit =
    // the row [[decide]] moved out of `open` (an approval is never deleted on its own)
    Unused(sql"UPDATE approvals SET state = ${to.wire}, result = $result WHERE id = $id".update.run(), Unused.Why.Held)
