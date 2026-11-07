package com.histmersion.domain.automation.agents
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.approvals.ProposalEvidence
import com.histmersion.api.automation.ListFilters

import com.augustnagro.magnum.*
import com.histmersion.api.automation.ApprovalDigest
import com.histmersion.domain.automation.agents.RunQuery
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.util.database.{Jsonb, Keyset, ListSql, Param, Sql}
import com.histmersion.util.database.given


import com.histmersion.api.automation.Goal

/** The store of embedded agent runs (migrations V035, V040, V041): a run is a `jobs` row with kind `agent` (its options
  * in `params`), its steps are `agent_steps` (one row per attempt; a `done` row is the checkpoint a resume replays),
  * its trace is `agent_messages`, and what it proposed for a person to decide is `approvals`. Token and cost totals are
  * sums over the steps, never a second copy.
  */
object AgentRepo:
  type ActiveOnly = ActiveOnly.T
  object ActiveOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CloseReason = CloseReason.T
  object CloseReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutputChars = OutputChars.T
  object OutputChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageCursor = PageCursor.T
  object PageCursor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `jobs.params`: `{}` (and anything a later version wrote that this one cannot read) is every option's default
    * ([[Jsonb]]).
    */

  type OpenForStepResult = OpenForStepResult.T
  object OpenForStepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSubjectResult = OpenSubjectResult.T
  object OpenSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleResult = SettleResult.T
  object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private given DbCodec[RunParams] = Jsonb.codec(RunParams(), dropNulls = true)

  final private case class RunRow(
      id: RunRow.Id,
      pipeline: Option[RunRow.Pipeline],
      target: Option[RunRow.Target],
      status: RunRow.Status,
      progress: Option[RunRow.Progress],
      error: Option[RunRow.Error],
      model: Option[RunRow.Model],
      budgetUsd: Option[BigDecimal],
      budgetTokens: Option[RunRow.BudgetTokens],
      warnings: List[RunRow.Warnings],
      createdAt: RunRow.CreatedAt,
      updatedAt: RunRow.UpdatedAt,
      tin: RunRow.Tin,
      tout: RunRow.Tout,
      tcr: RunRow.Tcr,
      tcw: RunRow.Tcw,
      cost: BigDecimal,
      targetKind: Option[RunRow.TargetKind],
      params: RunParams,
      acquireItem: Option[RunRow.AcquireItem],
      goal: Option[RunRow.Goal],
      parent: Option[RunRow.Parent],
      origin: Option[RunRow.Origin],
      resumeAt: Option[RunRow.ResumeAt],
      dryRun: RunRow.DryRun,
      managerId: Option[RunRow.ManagerId],
      managerItem: Option[RunRow.ManagerItem],
      heartbeatAt: Option[RunRow.HeartbeatAt]
  ) derives DbCodec

  object RunRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Progress = Progress.T
    object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Warnings = Warnings.T
    object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tin = Tin.T
    object Tin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tout = Tout.T
    object Tout extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tcr = Tcr.T
    object Tcr extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tcw = Tcw.T
    object Tcw extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetKind = TargetKind.T
    object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AcquireItem = AcquireItem.T
    object AcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Parent = Parent.T
    object Parent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Origin = Origin.T
    object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAt = ResumeAt.T
    object ResumeAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerItem = ManagerItem.T
    object ManagerItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeartbeatAt = HeartbeatAt.T
    object HeartbeatAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private case class StepRow(
      seq: StepRow.Seq,
      name: StepRow.Name,
      kind: StepRow.Kind,
      attempt: StepRow.Attempt,
      status: StepRow.Status,
      model: Option[StepRow.Model],
      promptHash: Option[StepRow.PromptHash],
      tin: StepRow.Tin,
      tout: StepRow.Tout,
      tcr: StepRow.Tcr,
      tcw: StepRow.Tcw,
      cost: BigDecimal,
      error: Option[StepRow.Error],
      output: Option[StepRow.Output],
      startedAt: StepRow.StartedAt,
      finishedAt: StepRow.FinishedAt,
      actualModel: Option[StepRow.ActualModel],
      wouldWrite: Option[StepRow.WouldWrite],
      firstCallTokens: Option[StepRow.FirstCallTokens],
      turns: Option[StepRow.Turns]
  ) derives DbCodec

  object StepRow:
    type Seq = Seq.T
    object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempt = Attempt.T
    object Attempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PromptHash = PromptHash.T
    object PromptHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tin = Tin.T
    object Tin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tout = Tout.T
    object Tout extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tcr = Tcr.T
    object Tcr extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tcw = Tcw.T
    object Tcw extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartedAt = StartedAt.T
    object StartedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishedAt = FinishedAt.T
    object FinishedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WouldWrite = WouldWrite.T
    object WouldWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private given DbCodec[MessageInfo] = DbCodec.derived

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** ONE projection of a run with its usage totals, then (in [[runSelect]]) the runs among the jobs. */
  private val runFrom =
    Sql.raw(s"""SELECT j.id, j.pipeline, j.target_id, j.status, j.progress, j.error, j.model, j.budget_usd,
              j.budget_tokens, j.warnings,
              to_char(j.created_at AT TIME ZONE 'UTC', $ts), to_char(j.updated_at AT TIME ZONE 'UTC', $ts),
              coalesce(u.tin, 0), coalesce(u.tout, 0), coalesce(u.tcr, 0), coalesce(u.tcw, 0), coalesce(u.cost, 0),
              j.target_kind, j.params::text, j.acquire_item, j.goal_slug, j.parent_job, j.origin,
              to_char(j.resume_at AT TIME ZONE 'UTC', $ts), j.dry_run, j.manager_id, j.manager_item,
              to_char(j.heartbeat_at AT TIME ZONE 'UTC', $ts)
       FROM jobs j
       LEFT JOIN LATERAL (
         SELECT sum(tokens_in)::bigint AS tin, sum(tokens_out)::bigint AS tout,
                sum(tokens_cache_read)::bigint AS tcr, sum(tokens_cache_write)::bigint AS tcw,
                sum(cost_usd) AS cost
         FROM agent_steps s WHERE s.job_id = j.id) u ON true""")
  private val runsOnly = s" WHERE j.kind = '$JobKind'"
  private val runSelect = runFrom ++ runsOnly

  private def toInfo(r: RunRow): RunInfo =
    RunInfo(
      r.id,
      r.pipeline.getOrElse(""),
      r.target.getOrElse(""),
      r.status,
      r.progress,
      r.error,
      r.model,
      Usage(r.tin, r.tout, r.tcr, r.tcw),
      r.cost.toDouble,
      r.budgetUsd.map(_.toDouble).getOrElse(0),
      r.budgetTokens.getOrElse(0L),
      r.warnings,
      r.createdAt,
      r.updatedAt,
      r.targetKind.getOrElse(""),
      r.params,
      r.acquireItem,
      r.goal,
      r.parent,
      r.origin,
      r.resumeAt,
      r.dryRun,
      r.managerId,
      r.managerItem,
      r.heartbeatAt
    )

  def get(id: com.histmersion.domain.automation.agents.AgentRepo.RunRow.Id)(using DbCon): Option[RunInfo] =
    (runSelect ++ Sql.of(" AND j.id = ?", Param.S(id))).frag.query[RunRow].run().headOption.map(toInfo)

  def list(status: Option[com.histmersion.domain.automation.Run.State.Wire], limit: com.histmersion.PageSize, goal: Option[com.histmersion.domain.automation.Goal.Slug] = None)(using DbCon): Vector[RunInfo] =
    (runSelect ++ runStatus(status) ++ runGoal(goal) ++ Keyset.orderBy("j.") ++ Sql.of(
      " LIMIT ?",
      Param.I(math.max(1, limit))
    )).frag.query[RunRow].run().map(toInfo)

  /** One page of the newest-first runs ([[com.histmersion.api.Paging]]), `status` and `goal` as in [[list]]; `before` /
    * `upTo` are run ids. Left: a cursor that names no job.
    */
  def page(
      status: Option[com.histmersion.domain.automation.Run.State.Wire],
      limit: com.histmersion.PageSize,
      goal: Option[com.histmersion.domain.automation.Goal.Slug] = None,
      before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor] = None,
      upTo: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor] = None
  )(using DbCon): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)] =
    page(
      RunQuery(
        status = status.map(_.trim.toLowerCase).filter(_.nonEmpty).toList,
        goal = goal.map(_.trim).filter(_.nonEmpty)
      ),
      limit,
      before,
      upTo
    )

  /** A run that predates `jobs.origin` was started by a person. */
  private val originExpr = s"coalesce(j.origin, '${Origin.Person.wire}')"

  /** ` AND (j.model is one of them OR a step of the run used one of them)` — `expr` maps a model to what is matched
    * (itself, or its provider).
    */
  private def usedModel(vs: List[String], expr: String => String): Sql =
    if vs.isEmpty then Sql.empty
    else
      Sql.of(
        s" AND (${expr("j.model")} = ANY(?::text[]) OR EXISTS (SELECT 1 FROM agent_steps ms " +
          s"WHERE ms.job_id = j.id AND ${expr("ms.model")} = ANY(?::text[])))",
        Param.A(vs),
        Param.A(vs)
      )

  private def provider(col: String): String = s"split_part($col, ':', 1)"

  private def runConds(q: RunQuery): ListSql.Conds = ListSql.Conds(
    List(
      "status" -> ListSql.anyOf("j.status", q.status),
      "pipeline" -> ListSql.anyOf("j.pipeline", q.pipelines),
      "manager" -> ListSql.anyOf("j.manager_id", q.managers),
      "goal" -> ListSql.equal("j.goal_slug", q.goal),
      "model" -> usedModel(q.models, identity),
      "provider" -> usedModel(q.providers, provider),
      "origin" -> ListSql.anyOf(originExpr, q.origins),
      "time" -> ListSql.between("j.created_at", q.since, q.until),
      "q" -> q.q.fold(Sql.empty)(t => Sql.of(" AND j.target_id ILIKE ?", Param.S(ListSql.contains(t))))
    )
  )

  /** One page of the runs that match `q`, newest first. Left: a cursor that names no job. */
  def page(
      q: RunQuery,
      limit: com.histmersion.PageSize,
      before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
      upTo: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor]
  )(using DbCon): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)] =
    (before.toList ++ upTo.toList).find(c => !Keyset.existsOn("jobs", Param.S(c))) match
      case Some(c) => Left(s"No agent run '$c' to page from")
      case None    =>
        val (rows, more) = Keyset.pageOn[RunRow](
          runFrom,
          Sql.raw(runsOnly) ++ runConds(q).all,
          "j.",
          "jobs",
          math.max(1, limit),
          before.map(Param.S(_)),
          upTo.map(Param.S(_)),
          r => Param.S(r.id)
        )
        Right((rows.map(toInfo), more))

  /** The counts that go with a filtered page of runs: per state, and the pipeline, origin, manager, model and provider
    * facets — each under every filter but its own. A run's models are the one it was started with and every one its
    * steps reported; the provider is a model's `provider:` part.
    */
  def runFacets(q: RunQuery)(using DbCon): (List[(com.histmersion.domain.automation.Run.State.Wire, com.histmersion.RowCount)], List[(com.histmersion.domain.automation.Pipeline.Wire, List[(com.histmersion.domain.automation.agents.AgentRepo.RunRow.Model, com.histmersion.RowCount)])]) =
    val c = runConds(q)
    def from(except: String) = Sql.raw(" FROM jobs j") ++ Sql.raw(runsOnly) ++ c.except(except)
    def used(except: String): Sql =
      Sql.raw(
        " FROM (SELECT DISTINCT j.id, x.m FROM jobs j CROSS JOIN LATERAL (SELECT j.model AS m UNION " +
          "SELECT s.model FROM agent_steps s WHERE s.job_id = j.id) x"
      ) ++ Sql.raw(runsOnly) ++ c.except(except) ++
        Sql.raw(" AND x.m IS NOT NULL) u WHERE true")
    (
      Keyset.countsOn(from("status"), "j.status"),
      List(
        "pipeline" -> ListSql.facetOn(from("pipeline"), "j.pipeline", ListFilters.FacetMax),
        "origin" -> ListSql.facetOn(from("origin"), originExpr, ListFilters.FacetMax),
        "manager" -> ListSql.facetOn(from("manager"), "j.manager_id", ListFilters.FacetMax),
        "model" -> ListSql.facetOn(used("model"), "u.m", ListFilters.FacetMax),
        "provider" -> ListSql.facetOn(used("provider"), provider("u.m"), ListFilters.FacetMax, "count(DISTINCT u.id)")
      )
    )

  /** How many runs are in each state — of one goal, when given. */
  def stateCounts(goal: Option[com.histmersion.domain.automation.Goal.Slug] = None)(using DbCon): List[(com.histmersion.domain.automation.Approval.State.Wire, com.histmersion.RowCount)] =
    Keyset.countsOn(Sql.raw(" FROM jobs j") ++ Sql.raw(runsOnly) ++ runGoal(goal), "j.status")

  private def runStatus(status: Option[com.histmersion.domain.automation.Run.State.Wire]): Sql =
    status.map(_.trim.toLowerCase).filter(_.nonEmpty).fold(Sql.empty)(s => Sql.of(" AND j.status = ?", Param.S(s)))

  private def runGoal(goal: Option[com.histmersion.domain.automation.Goal.Slug]): Sql =
    goal.map(_.trim).filter(_.nonEmpty).fold(Sql.empty)(g => Sql.of(" AND j.goal_slug = ?", Param.S(g)))

  /** Every run of `p` on `target`, newest first (what auto-enrich asks before queueing another). */
  def forTarget(p: Pipeline, target: com.histmersion.domain.automation.agents.AgentRepo.RunRow.Target)(using DbCon): Vector[RunInfo] =
    (runSelect ++ Sql.of(
      " AND j.pipeline = ? AND j.target_id = ? ORDER BY j.created_at DESC, j.id",
      Param.S(p.wire),
      Param.S(target)
    )).frag.query[RunRow].run().map(toInfo)

  /** The runs a manager dispatched, newest first (`active` = only those not settled yet: queued, running, paused or
    * waiting).
    */
  def forManager(managerId: com.histmersion.domain.automation.agents.AgentRepo.RunRow.ManagerId, active: com.histmersion.domain.automation.agents.AgentRepo.ActiveOnly = false, limit: com.histmersion.PageSize = 200)(using DbCon): Vector[RunInfo] =
    val open = if active then Sql.of(" AND j.status IN ('queued', 'running', 'paused', 'waiting')") else Sql.empty
    (runSelect ++ Sql.of(" AND j.manager_id = ?", Param.S(managerId)) ++ open ++
      Sql.of(" ORDER BY j.created_at DESC, j.id DESC LIMIT ?", Param.I(math.max(1, limit)))).frag
      .query[RunRow]
      .run()
      .map(toInfo)

  /** Every run on an article or source `target` that has not settled (any pipeline, any origin): a manager does not
    * start a second writer on it.
    */
  def unsettledOn(target: com.histmersion.domain.automation.agents.AgentRepo.RunRow.Target)(using DbCon): Vector[RunInfo] =
    (runSelect ++ Sql.of(
      " AND j.target_id = ? AND j.status IN ('queued', 'running', 'paused', 'waiting')",
      Param.S(target)
    )).frag.query[RunRow].run().map(toInfo)

  // ── steps: checkpoints and attempts ────────────────────────────────────────────────────────────

  /** The checkpoint of step `seq`: its name and recorded output, if an attempt finished. */
  def checkpoint(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRow.Seq)(using DbCon): Option[(com.histmersion.domain.automation.agents.AgentRepo.StepRow.Output, com.histmersion.domain.automation.agents.AgentRepo.RunRow.Progress)] =
    sql"""SELECT name, output::text FROM agent_steps
          WHERE job_id = $jobId AND seq = $seq AND status = ${StepStatus.Done.wire}
          ORDER BY attempt DESC LIMIT 1"""
      .query[(String, Option[String])]
      .run()
      .headOption
      .map((n, o) => (n, o.getOrElse("null")))

  /** How many attempts step `seq` already has (failed ones included) — a resumed step continues the count rather than
    * restarting it, so `max-retries` holds across a restart.
    */
  def attempts(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRow.Seq)(using DbCon): com.histmersion.RowCount =

    sql"SELECT coalesce(max(attempt), 0) FROM agent_steps WHERE job_id = $jobId AND seq = $seq"
      .query[Int]
      .run()
      .head

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
      error: Option[StepRecord.Error],
      startedAt: java.time.Instant,
      actualModel: Option[StepRecord.ActualModel] = None,
      wouldWrite: List[WouldWrite] = Nil,
      firstCallTokens: Option[StepRecord.FirstCallTokens] = None,
      turns: Option[StepRecord.Turns] = None
  )

  object StepRecord:
    type Seq = Seq.T
    object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempt = Attempt.T
    object Attempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PromptHash = PromptHash.T
    object PromptHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class MessageRecord(
      n: MessageRecord.N,
      role: MessageRecord.Role,
      content: Option[MessageRecord.Content],
      toolName: Option[MessageRecord.ToolName],
      toolInput: Option[MessageRecord.ToolInput],
      toolResult: Option[MessageRecord.ToolResult],
      latencyMs: Option[MessageRecord.LatencyMs]
  )

  object MessageRecord:
    type N = N.T
    object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Content = Content.T
    object Content extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolName = ToolName.T
    object ToolName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolInput = ToolInput.T
    object ToolInput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolResult = ToolResult.T
    object ToolResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatencyMs = LatencyMs.T
    object LatencyMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One attempt and its trace, in ONE transaction (a checkpoint without its trace, or the reverse, is never visible).
    */
  def record(jobId: com.histmersion.util.jobs.Job.Id, s: StepRecord, messages: List[MessageRecord])(using DbCon): Unit =
    val started = s.startedAt.toString
    val would = Option.when(s.wouldWrite.nonEmpty)(Jsonb.write(s.wouldWrite))
    sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, output, prompt_hash, model,
                                   tokens_in, tokens_out, tokens_cache_read, tokens_cache_write, cost_usd,
                                   error, started_at, finished_at, actual_model, would_write, first_call_tokens, turns)
          VALUES ($jobId, ${s.seq}, ${s.attempt}, ${s.name}, ${s.kind.wire}, ${s.status.wire}, ${s.output}::jsonb,
                  ${s.promptHash}, ${s.model}, ${s.usage.input}, ${s.usage.output}, ${s.usage.cacheRead},
                  ${s.usage.cacheWrite}, ${s.costUsd}, ${s.error}, $started::timestamptz, now(), ${s.actualModel},
                  $would::jsonb, ${s.firstCallTokens}, ${s.turns})""".update.run()
    messages.foreach { m =>
      sql"""INSERT INTO agent_messages (job_id, seq, attempt, n, role, content, tool_name, tool_input,
                                        tool_result, latency_ms)
            VALUES ($jobId, ${s.seq}, ${s.attempt}, ${m.n}, ${m.role}, ${m.content}, ${m.toolName},
                    ${m.toolInput}, ${m.toolResult}, ${m.latencyMs})""".update.run()
    }
    sql"UPDATE jobs SET updated_at = now() WHERE id = $jobId".update.run()

  def steps(jobId: com.histmersion.util.jobs.Job.Id, outputChars: com.histmersion.domain.automation.agents.AgentRepo.OutputChars)(using DbCon): List[StepInfo] =
    sql"""SELECT seq, name, kind, attempt, status, model, prompt_hash, tokens_in, tokens_out, tokens_cache_read,
                 tokens_cache_write, cost_usd, error, left(output::text, $outputChars),
                 to_char(started_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)}),
                 to_char(finished_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)}), actual_model, would_write::text,
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
        r.cost.toDouble,
        r.error,
        r.output,
        r.startedAt,
        r.finishedAt,
        r.actualModel,
        r.wouldWrite.flatMap(Jsonb.read[List[WouldWrite]]).getOrElse(Nil),
        r.firstCallTokens,
        r.turns
      )
    }

  def messages(jobId: com.histmersion.util.jobs.Job.Id)(using DbCon): List[MessageInfo] =
    sql"""SELECT seq, n, role, content, tool_name, tool_input, tool_result, latency_ms
          FROM agent_messages WHERE job_id = $jobId ORDER BY seq, attempt, n""".query[MessageInfo].run().toList

  // ── spend ──────────────────────────────────────────────────────────────────────────────────────

  /** What one run has spent (every attempt counts — a rejected answer was still paid for). */
  def runSpend(jobId: com.histmersion.util.jobs.Job.Id)(using DbCon): (Usage, BigDecimal) =
    sql"""SELECT coalesce(sum(tokens_in), 0)::bigint, coalesce(sum(tokens_out), 0)::bigint,
                 coalesce(sum(tokens_cache_read), 0)::bigint, coalesce(sum(tokens_cache_write), 0)::bigint,
                 coalesce(sum(cost_usd), 0)
          FROM agent_steps WHERE job_id = $jobId""".query[(Long, Long, Long, Long, BigDecimal)].run().head match
      case (i, o, cr, cw, c) => (Usage(i, o, cr, cw), c)

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
  def pruneMessages(days: com.histmersion.domain.CodexRetention.KeepDays)(using DbCon): com.histmersion.RowCount =
    if days <= 0 then 0
    else sql"""DELETE FROM agent_messages WHERE created_at < now() - make_interval(days => $days)""".update.run()

  // ── approvals: what runs proposed for a person to decide (`ApprovalService` opens the connection) ────

  final case class ApprovalRow(
      id: ApprovalRow.Id,
      jobId: Option[com.histmersion.util.jobs.Job.Id],
      kind: ApprovalRow.Kind,
      citekey: Option[com.histmersion.domain.library.Citekey],
      status: Option[ApprovalRow.Status],
      current: Option[ApprovalRow.Current],
      edgeKind: Option[ApprovalRow.EdgeKind],
      target: Option[ApprovalRow.Target],
      proposal: ApprovalRow.Proposal,
      state: ApprovalRow.State,
      note: Option[ApprovalRow.Note],
      result: Option[ApprovalRow.Result],
      createdAt: ApprovalRow.CreatedAt,
      decidedAt: Option[ApprovalRow.DecidedAt],
      subject: Option[ApprovalRow.Subject],
      seq: Option[ApprovalRow.Seq]
  ) derives DbCodec

  object ApprovalRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Current = Current.T
    object Current extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeKind = EdgeKind.T
    object EdgeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Proposal = Proposal.T
    object Proposal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Result = Result.T
    object Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecidedAt = DecidedAt.T
    object DecidedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Seq = Seq.T
    object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val approvalSelect = Sql.raw(
    s"""SELECT id, job_id, kind, citekey, status, current_status, edge_kind, target, proposal::text, state, note,
              result, to_char(created_at AT TIME ZONE 'UTC', $ts), to_char(decided_at AT TIME ZONE 'UTC', $ts),
              subject, seq
       FROM approvals"""
  )

  /** The payload decides how a row reads: a source proposal, a goal's plan, or an article draft. */
  def info(r: ApprovalRow): ApprovalInfo =
    val base = ApprovalInfo(
      r.id,
      r.jobId,
      r.kind,
      r.citekey.getOrElse(""),
      r.status,
      r.current,
      r.edgeKind,
      r.target,
      "",
      None,
      r.state,
      r.note,
      r.result,
      r.createdAt,
      r.decidedAt,
      r.subject
    )
    ApprovalKind.parse(r.kind) match
      case Some(ApprovalKind.GoalPlan) =>
        val p = Jsonb.read[Goal.Plans.Plan](r.proposal)
        base.modify(_.plan).setTo(p).modify(_.evidence).setTo(p.map(_.rationale).getOrElse(""))
      case Some(ApprovalKind.ArticlePublish) =>
        val d = Jsonb.read[Goal.Plans.ArticleDraft](r.proposal)
        base.modify(_.article).setTo(d).modify(_.evidence).setTo(d.flatMap(_.summary).getOrElse(""))
      case Some(ApprovalKind.HintEffect) =>
        val p = Jsonb.read[com.histmersion.api.automation.Managers.HintProposal](r.proposal)
        base.modify(
          _.hintEffect).setTo(p).modify(_.evidence).setTo(p.map(x => Option(x.why).filter(_.nonEmpty).getOrElse(x.text)).getOrElse("")
        )
      case _ =>
        base.withArticleProposal(Jsonb.read[io.circe.Json](r.proposal)).getOrElse {
          val p = Jsonb.read[ProposalEvidence](r.proposal)
          base.modify(_.evidence).setTo(p.flatMap(_.evidence).getOrElse("")).modify(_.quote).setTo(p.flatMap(_.quote))
        }




  /** The payload an approval was recorded with, as stored. */
  def payload(id: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Id)(using DbCon): Option[com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Proposal] =
    sql"SELECT proposal::text FROM approvals WHERE id = $id".query[String].run().headOption




  def approval(id: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Id)(using DbCon): Option[ApprovalInfo] =

    (approvalSelect ++ Sql.of(" WHERE id = ?", Param.L(id))).frag
      .query[ApprovalRow]
      .run()
      .headOption
      .map(info)

  /** Newest first; `state` narrows (none = every state). */
  def approvals(state: Option[ApprovalState], limit: com.histmersion.PageSize)(using DbCon): List[ApprovalInfo] =
    val where = state.fold(Sql.empty)(s => Sql.of(" WHERE state = ?", Param.S(s.wire)))
    (approvalSelect ++ where ++ Sql.of(
      " ORDER BY created_at DESC, id DESC LIMIT ?",
      Param.I(math.max(1, limit))
    )).frag.query[ApprovalRow].run().toList.map(info)

  /** One page of the newest-first approvals ([[com.histmersion.api.Paging]]), `state` as in [[approvals]]; `before` /
    * `upTo` are approval ids. Left: a cursor that is not one.
    */
  def approvalPage(
      state: Option[ApprovalState],
      limit: com.histmersion.PageSize,
      before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor] = None,
      upTo: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor] = None
  )(using DbCon): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (List[ApprovalInfo], Boolean)] =
    approvalPage(ApprovalQuery(state), limit, before, upTo)

  private def approvalConds(q: ApprovalQuery): ListSql.Conds =
    val subject = q.subject.fold(Sql.empty) { t =>
      val p = Param.S(ListSql.contains(t))
      Sql.of(
        " AND (subject ILIKE ? OR citekey ILIKE ? OR target ILIKE ? OR EXISTS (SELECT 1 FROM jobs sj " +
          "WHERE sj.id = approvals.job_id AND (sj.goal_slug ILIKE ? OR sj.manager_id ILIKE ?)))",
        p,
        p,
        p,
        p,
        p
      )
    }
    val goal = q.goal.fold(Sql.empty)(g =>
      Sql.of(
        " AND (subject = ? OR EXISTS (SELECT 1 FROM jobs gj " +
          "WHERE gj.id = approvals.job_id AND gj.goal_slug = ?))",
        Param.S(g),
        Param.S(g)
      )
    )
    ListSql.Conds(
      List(
        "state" -> q.state.fold(Sql.empty)(s => Sql.of(" AND state = ?", Param.S(s.wire))),
        "kind" -> ListSql.anyOf("kind", q.kinds.map(_.wire)),
        "subject" -> subject,
        "goal" -> goal,
        "decidedBy" -> ListSql.anyOf(ApprovalQuery.decidedByExpr, q.decidedBy.map(_.wire))
      )
    )

  /** One page of the approvals that match `q`, newest first ([[com.histmersion.api.Paging]]). */
  def approvalPage(
      q: ApprovalQuery,
      limit: com.histmersion.PageSize,
      before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
      upTo: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor]
  )(using DbCon): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (List[ApprovalInfo], Boolean)] =
    def cursor(c: com.histmersion.domain.automation.agents.AgentRepo.PageCursor): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, Param] =
      c.trim.toLongOption
        .map(Param.L(_))
        .filter(Keyset.existsOn("approvals", _))
        .toRight(s"No approval '$c' to page from")
    for
      b <- before.fold(Right(None))(cursor(_).map(Some(_)))
      u <- upTo.fold(Right(None))(cursor(_).map(Some(_)))
    yield
      val where = Sql.raw(" WHERE true") ++ approvalConds(q).all
      val (rows, more) = Keyset
        .pageOn[ApprovalRow](approvalSelect, where, "", "approvals", math.max(1, limit), b, u, r => Param.L(r.id))
      (rows.toList.map(info), more)

  /** The counts that go with a filtered page: per state (under every filter but the state) and the `kind` and
    * `decidedBy` facets (each under every filter but its own).
    */
  def approvalFacets(q: ApprovalQuery)(using DbCon): (List[(com.histmersion.domain.automation.Approval.Kind.Wire, com.histmersion.RowCount)], List[(com.histmersion.domain.automation.Approval.State.Wire, List[(com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Subject, com.histmersion.RowCount)])]) =
    val c = approvalConds(q)
    def from(except: String) = Sql.raw(" FROM approvals WHERE true") ++ c.except(except)
    (
      Keyset.countsOn(from("state"), "state"),
      List(
        "kind" -> ListSql.facetOn(from("kind"), "kind", ListFilters.FacetMax),
        "decidedBy" -> ListSql.facetOn(from("decidedBy"), ApprovalQuery.decidedByExpr, ListFilters.FacetMax)
      )
    )

  /** How many approvals are in each state. */
  def approvalCounts()(using DbCon): List[(com.histmersion.domain.automation.Approval.Kind.Wire, com.histmersion.RowCount)] = Keyset.countsOn(Sql.raw(" FROM approvals"), "state")

  /** The approval step `seq` of run `jobId` waits on, opened if it is not there yet (a resumed run finds the one it
    * opened before, never a second). `state` = approved with a `result` records one applied at once (an `auto` mode).
    */
  def openForStep(
      jobId: com.histmersion.util.jobs.Job.Id,
      seq: com.histmersion.domain.automation.agents.AgentRepo.StepRow.Seq,
      kind: ApprovalKind,
      subject: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Subject,
      payload: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Proposal,
      state: ApprovalState,
      result: Option[com.histmersion.domain.automation.agents.AgentRepo.OpenForStepResult],
      now: java.time.Instant
  )(using DbCon): ApprovalInfo =
    val decided = Option.when(state != ApprovalState.Open)(now.toString)
    sql"""INSERT INTO approvals (job_id, seq, kind, subject, proposal, state, result, decided_at)
          VALUES ($jobId, $seq, ${kind.wire}, $subject, $payload::jsonb, ${state.wire}, $result, $decided::timestamptz)
          ON CONFLICT (job_id, seq) WHERE seq IS NOT NULL DO NOTHING""".update.run()
    forStep(jobId, seq).get

  /** A proposal about a goal or an article (`subject`) that the run does NOT wait for (an inferred filter, a plan of
    * articles): recorded `open` for a person, or `approved` with its `result` when it was applied at once (an `auto`
    * mode).
    */
  def openSubject(
      jobId: Option[com.histmersion.util.jobs.Job.Id],
      kind: ApprovalKind,
      subject: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Subject,
      payload: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Proposal,
      state: ApprovalState,
      result: Option[com.histmersion.domain.automation.agents.AgentRepo.OpenSubjectResult],
      now: java.time.Instant
  )(using DbCon): ApprovalInfo =
    val decided = Option.when(state != ApprovalState.Open)(now.toString)
    val id =
      sql"""INSERT INTO approvals (job_id, kind, subject, proposal, state, result, decided_at)
            VALUES ($jobId, ${kind.wire}, $subject, $payload::jsonb, ${state.wire}, $result, $decided::timestamptz)
            RETURNING id""".query[Long].run().head
    approval(id).get

  /** The approvals run `jobId` opened (of `kind`, when given), oldest first. */
  def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind] = None)(using DbCon): List[ApprovalInfo] =
    (approvalSelect ++ Sql.of(" WHERE job_id = ?", Param.S(jobId)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND kind = ?", Param.S(k.wire))) ++ Sql
        .of(" ORDER BY id")).frag.query[ApprovalRow].run().toList.map(info)

  /** The approvals of `kind` the autopilot APPROVED since `since` (their note starts with `autopilot: approved`) — what
    * its daily caps count.
    */
  def autoApprovedSince(kind: ApprovalKind, since: java.time.Instant)(using DbCon): List[ApprovalInfo] =
    val t = since.toString
    (approvalSelect ++ Sql.of(
      " WHERE kind = ? AND note LIKE 'autopilot: approved%' AND decided_at >= ?::timestamptz ORDER BY id",
      Param.S(kind.wire),
      Param.S(t)
    )).frag.query[ApprovalRow].run().toList.map(info)

  /** The OPEN approvals of the given kinds, oldest first (what the supervisor's checks look at). */
  def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.PageSize)(using DbCon): List[ApprovalInfo] =
    (approvalSelect ++ Sql.of(
      " WHERE state = 'open' AND kind = ANY(?::text[]) ORDER BY id LIMIT ?",
      Param.A(kinds.map(_.wire)),
      Param.I(math.max(1, limit))
    )).frag.query[ApprovalRow].run().toList.map(info)

  /** Every OPEN approval, light (id, kind, subject, created), oldest first — what the digest groups. */
  def openLight()(using DbCon): List[ApprovalDigest.Open] =
    sql"""SELECT id, kind, subject, to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
          FROM approvals WHERE state = 'open' ORDER BY id"""
      .query[(Long, String, Option[String], String)]
      .run()
      .toList
      .map((i, k, s, c) => ApprovalDigest.Open(i, k, s, c))

  /** How many approvals of `kind` are open (the backlog a scaled daily cap reads). */
  def openCount(kind: ApprovalKind)(using DbCon): com.histmersion.RowCount =

    sql"SELECT count(*)::int FROM approvals WHERE state = 'open' AND kind = ${kind.wire}".query[Int].run().head

  /** Open approvals per subject (a goal or an article slug). */
  def openBySubject()(using DbCon): Map[com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Subject, com.histmersion.RowCount] =
    sql"""SELECT subject, count(*)::int FROM approvals WHERE state = 'open' AND subject IS NOT NULL GROUP BY subject"""
      .query[(String, Int)]
      .run()
      .toMap

  /** The approval step `seq` of run `jobId` opened, if it did. */
  def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRow.Seq)(using DbCon): Option[ApprovalInfo] =

    (approvalSelect ++ Sql.of(" WHERE job_id = ? AND seq = ?", Param.S(jobId), Param.I(seq))).frag
      .query[ApprovalRow]
      .run()
      .headOption
      .map(info)

  /** A cancelled run's open approvals: rejected, so nobody decides for a run that is gone. */
  def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.AgentRepo.CloseReason)(using DbCon): com.histmersion.RowCount =
    sql"""UPDATE approvals SET state = ${ApprovalState.Rejected.wire}, result = $why, decided_at = now()
          WHERE job_id = $jobId AND seq IS NOT NULL AND state = ${ApprovalState.Open.wire}""".update.run()

  /** Compare-and-set an OPEN approval to its decision. `false` when it had already been decided. */
  def decide(id: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Id, to: ApprovalState, note: Option[com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Note], result: Option[String])(using DbCon): Boolean =
    sql"""UPDATE approvals SET state = ${to.wire}, note = $note, result = $result, decided_at = now()
          WHERE id = $id AND state = ${ApprovalState.Open.wire}""".update.run() > 0

  /** After [[decide]]: the final state and what applying the decision did. */
  def settle(id: com.histmersion.domain.automation.agents.AgentRepo.ApprovalRow.Id, to: ApprovalState, result: com.histmersion.domain.automation.agents.AgentRepo.SettleResult)(using DbCon): Unit =
    val _ =
      sql"UPDATE approvals SET state = ${to.wire}, result = $result WHERE id = $id".update.run()
