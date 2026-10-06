package com.histmersion.db

import com.histmersion.core.Agents.*
import com.histmersion.core.{AgentProposals, EdgeKind, GoalPlans, SourceStatus}
import io.circe.parser.decode
import io.circe.syntax.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The store of embedded agent runs (migrations V035, V040, V041): a run is a `jobs` row with kind
  * `agent` (its options in `params`), its steps are `agent_steps` (one row per attempt; a `done` row
  * is the checkpoint a resume replays), its trace is `agent_messages`, and what it proposed for a
  * person to decide is `approvals`. Token and cost totals are sums over the steps, never a second
  * copy. */
object AgentRepo:

  private final case class RunRow(
      id: String, pipeline: Option[String], target: Option[String], status: String, progress: Option[String],
      error: Option[String], model: Option[String], budgetUsd: Option[BigDecimal], budgetTokens: Option[Long],
      warnings: List[String], createdAt: String, updatedAt: String,
      tin: Long, tout: Long, tcr: Long, tcw: Long, cost: BigDecimal,
      targetKind: Option[String], params: String, acquireItem: Option[Int],
      goal: Option[String], parent: Option[String], origin: Option[String], resumeAt: Option[String])
  private given DbCodec[RunRow] = DbCodec.derived

  private final case class StepRow(
      seq: Int, name: String, kind: String, attempt: Int, status: String, model: Option[String],
      promptHash: Option[String], tin: Long, tout: Long, tcr: Long, tcw: Long, cost: BigDecimal,
      error: Option[String], output: Option[String], startedAt: String, finishedAt: String)
  private given DbCodec[StepRow] = DbCodec.derived

  private given DbCodec[MessageInfo] = DbCodec.derived

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** ONE projection of a run with its usage totals. */
  private val runSelect = Sql.raw(
    s"""SELECT j.id, j.pipeline, j.target_id, j.status, j.progress, j.error, j.model, j.budget_usd,
              j.budget_tokens, j.warnings,
              to_char(j.created_at AT TIME ZONE 'UTC', $ts), to_char(j.updated_at AT TIME ZONE 'UTC', $ts),
              coalesce(u.tin, 0), coalesce(u.tout, 0), coalesce(u.tcr, 0), coalesce(u.tcw, 0), coalesce(u.cost, 0),
              j.target_kind, j.params::text, j.acquire_item, j.goal_slug, j.parent_job, j.origin,
              to_char(j.resume_at AT TIME ZONE 'UTC', $ts)
       FROM jobs j
       LEFT JOIN LATERAL (
         SELECT sum(tokens_in)::bigint AS tin, sum(tokens_out)::bigint AS tout,
                sum(tokens_cache_read)::bigint AS tcr, sum(tokens_cache_write)::bigint AS tcw,
                sum(cost_usd) AS cost
         FROM agent_steps s WHERE s.job_id = j.id) u ON true
       WHERE j.kind = '$JobKind'""")

  private def toInfo(r: RunRow): RunInfo =
    RunInfo(r.id, r.pipeline.getOrElse(""), r.target.getOrElse(""), r.status, r.progress, r.error, r.model,
      Usage(r.tin, r.tout, r.tcr, r.tcw), r.cost.toDouble, r.budgetUsd.map(_.toDouble).getOrElse(0),
      r.budgetTokens.getOrElse(0L), r.warnings, r.createdAt, r.updatedAt, r.targetKind.getOrElse(""),
      // `{}` (and anything a later version wrote that this one cannot read) is every option's default
      decode[RunParams](r.params).getOrElse(RunParams()), r.acquireItem, r.goal, r.parent, r.origin, r.resumeAt)

  /** A `queued` run. `goal` is the goal it works for, `parent` the run that started it, `origin` who. */
  def create(ds: DataSource, p: Pipeline, target: String, model: Option[String], budgetUsd: BigDecimal,
      budgetTokens: Long, params: RunParams = RunParams(), acquireItem: Option[Int] = None,
      goal: Option[String] = None, parent: Option[String] = None, origin: Origin = Origin.Person): RunInfo =
    val id   = java.util.UUID.randomUUID().toString
    val json = params.asJson.dropNullValues.noSpaces
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, citekey, status, pipeline, pipeline_version, target_kind, target_id,
                              model, budget_usd, budget_tokens, params, acquire_item, goal_slug, parent_job, origin)
            VALUES ($id, $JobKind, ${Option.when(p.targetKind == TargetKind.Source)(target)}, ${RunState.Queued.wire},
                    ${p.wire}, ${p.version}, ${p.targetKind.wire}, $target, $model, $budgetUsd, $budgetTokens,
                    $json::jsonb, $acquireItem, $goal, $parent, ${origin.wire})""".update.run()
    get(ds, id).get

  def get(ds: DataSource, id: String): Option[RunInfo] =
    connect(ds)((runSelect ++ Sql.of(" AND j.id = ?", Param.S(id))).frag.query[RunRow].run().headOption.map(toInfo))

  /** The pipeline version a run was started with (a resume under a changed pipeline is refused). */
  def pipelineVersion(ds: DataSource, id: String): Option[Int] =
    connect(ds)(sql"SELECT pipeline_version FROM jobs WHERE id = $id".query[Option[Int]].run().headOption.flatten)

  def list(ds: DataSource, status: Option[String], limit: Int, goal: Option[String] = None): Vector[RunInfo] =
    val where = status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) => Sql.of(" AND j.status = ?", Param.S(s))
      case None    => Sql.empty
    val forGoal = goal.map(_.trim).filter(_.nonEmpty).fold(Sql.empty)(g => Sql.of(" AND j.goal_slug = ?", Param.S(g)))
    connect(ds):
      (runSelect ++ where ++ forGoal ++ Sql.of(" ORDER BY j.created_at DESC, j.id LIMIT ?", Param.I(math.max(1, limit))))
        .frag.query[RunRow].run().map(toInfo)

  /** Agent runs queued or running now — what a scheduled start counts against `max-concurrent-runs`. */
  def active(ds: DataSource): Int =
    connect(ds)(sql"""SELECT count(*)::int FROM jobs WHERE kind = $JobKind
                      AND status IN (${RunState.Queued.wire}, ${RunState.Running.wire})""".query[Int].run().head)

  /** Compare-and-set state change through [[RunState.next]]: `None` when refused or when the row had
    * already moved on (another worker, a concurrent cancel). `resumeAt` is when a run paused for an
    * allowance may go on; every transition sets it (None clears it). */
  def transition(ds: DataSource, id: String, from: RunState, to: RunState, error: Option[String] = None,
      progress: Option[String] = None, resumeAt: Option[java.time.Instant] = None): Option[RunState] =
    RunState.next(from, to).flatMap { target =>
      val at = resumeAt.map(_.toString)
      val n = transact(ds):
        sql"""UPDATE jobs SET status = ${target.wire}, error = $error, progress = $progress,
                              resume_at = $at::timestamptz, updated_at = now()
              WHERE id = $id AND kind = $JobKind AND status = ${from.wire}""".update.run()
      Option.when(n > 0)(target)
    }

  /** Paused runs whose `resume_at` has come (an allowance window freed): the engine re-queues them. */
  def dueToResume(ds: DataSource, now: java.time.Instant): Vector[String] =
    val at = now.toString
    connect(ds):
      sql"""SELECT id FROM jobs WHERE kind = $JobKind AND status = ${RunState.Paused.wire}
                                  AND resume_at IS NOT NULL AND resume_at <= $at::timestamptz
            ORDER BY resume_at, created_at""".query[String].run()

  def progress(ds: DataSource, id: String, text: String): Unit =
    val _ = transact(ds):
      sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND kind = $JobKind".update.run()

  def addWarning(ds: DataSource, id: String, w: String): Unit =
    val _ = transact(ds):
      sql"""UPDATE jobs SET warnings = array_append(warnings, $w), updated_at = now()
            WHERE id = $id AND kind = $JobKind""".update.run()

  /** Raise a paused run's budget before it resumes. */
  def setBudget(ds: DataSource, id: String, usd: Option[BigDecimal], tokens: Option[Long]): Unit =
    val _ = transact(ds):
      sql"""UPDATE jobs SET budget_usd = coalesce($usd, budget_usd), budget_tokens = coalesce($tokens, budget_tokens),
                            updated_at = now() WHERE id = $id AND kind = $JobKind""".update.run()

  /** Startup: a run that was `running` when the process died is RESUMABLE — `paused: interrupted`,
    * not failed (its finished steps are checkpointed). Queued runs stay queued; the caller re-enqueues
    * them. Returns the ids of the runs to re-enqueue. */
  def reconcile(ds: DataSource): (Int, Vector[String]) =
    transact(ds):
      val paused = sql"""UPDATE jobs SET status = ${RunState.Paused.wire}, error = ${PauseReason.Interrupted.wire},
                                         updated_at = now()
                         WHERE kind = $JobKind AND status = ${RunState.Running.wire}""".update.run()
      val queued = sql"""SELECT id FROM jobs WHERE kind = $JobKind AND status = ${RunState.Queued.wire}
                         ORDER BY created_at""".query[String].run()
      (paused, queued)

  // ── steps: checkpoints and attempts ────────────────────────────────────────────────────────────

  /** The checkpoint of step `seq`: its name and recorded output, if an attempt finished. */
  def checkpoint(ds: DataSource, jobId: String, seq: Int): Option[(String, String)] =
    connect(ds):
      sql"""SELECT name, output::text FROM agent_steps
            WHERE job_id = $jobId AND seq = $seq AND status = ${StepStatus.Done.wire}
            ORDER BY attempt DESC LIMIT 1""".query[(String, Option[String])].run().headOption
        .map((n, o) => (n, o.getOrElse("null")))

  /** How many attempts step `seq` already has (failed ones included) — a resumed step continues the
    * count rather than restarting it, so `max-retries` holds across a restart. */
  def attempts(ds: DataSource, jobId: String, seq: Int): Int =
    connect(ds)(sql"SELECT coalesce(max(attempt), 0) FROM agent_steps WHERE job_id = $jobId AND seq = $seq"
      .query[Int].run().head)

  final case class StepRecord(seq: Int, attempt: Int, name: String, kind: StepKind, status: StepStatus,
      output: Option[String], promptHash: Option[String], model: Option[String], usage: Usage, costUsd: BigDecimal,
      error: Option[String], startedAt: java.time.Instant)

  final case class MessageRecord(n: Int, role: String, content: Option[String], toolName: Option[String],
      toolInput: Option[String], toolResult: Option[String], latencyMs: Option[Int])

  /** One attempt and its trace, in ONE transaction (a checkpoint without its trace, or the reverse,
    * is never visible). */
  def record(ds: DataSource, jobId: String, s: StepRecord, messages: List[MessageRecord]): Unit =
    val started = s.startedAt.toString
    transact(ds):
      sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, output, prompt_hash, model,
                                     tokens_in, tokens_out, tokens_cache_read, tokens_cache_write, cost_usd,
                                     error, started_at, finished_at)
            VALUES ($jobId, ${s.seq}, ${s.attempt}, ${s.name}, ${s.kind.wire}, ${s.status.wire}, ${s.output}::jsonb,
                    ${s.promptHash}, ${s.model}, ${s.usage.input}, ${s.usage.output}, ${s.usage.cacheRead},
                    ${s.usage.cacheWrite}, ${s.costUsd}, ${s.error}, $started::timestamptz, now())""".update.run()
      messages.foreach { m =>
        sql"""INSERT INTO agent_messages (job_id, seq, attempt, n, role, content, tool_name, tool_input,
                                          tool_result, latency_ms)
              VALUES ($jobId, ${s.seq}, ${s.attempt}, ${m.n}, ${m.role}, ${m.content}, ${m.toolName},
                      ${m.toolInput}, ${m.toolResult}, ${m.latencyMs})""".update.run()
      }
      sql"UPDATE jobs SET updated_at = now() WHERE id = $jobId".update.run()

  def steps(ds: DataSource, jobId: String, outputChars: Int): List[StepInfo] =
    connect(ds):
      sql"""SELECT seq, name, kind, attempt, status, model, prompt_hash, tokens_in, tokens_out, tokens_cache_read,
                   tokens_cache_write, cost_usd, error, left(output::text, $outputChars),
                   to_char(started_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)}),
                   to_char(finished_at AT TIME ZONE 'UTC', ${SqlLiteral(ts)})
            FROM agent_steps WHERE job_id = $jobId ORDER BY seq, attempt""".query[StepRow].run().toList.map { r =>
        StepInfo(r.seq, r.name, r.kind, r.attempt, r.status, r.model, r.promptHash,
          Usage(r.tin, r.tout, r.tcr, r.tcw), r.cost.toDouble, r.error, r.output, r.startedAt, r.finishedAt)
      }

  def messages(ds: DataSource, jobId: String): List[MessageInfo] =
    connect(ds):
      sql"""SELECT seq, n, role, content, tool_name, tool_input, tool_result, latency_ms
            FROM agent_messages WHERE job_id = $jobId ORDER BY seq, attempt, n""".query[MessageInfo].run().toList

  // ── spend ──────────────────────────────────────────────────────────────────────────────────────

  /** What one run has spent (every attempt counts — a rejected answer was still paid for). */
  def runSpend(ds: DataSource, jobId: String): (Usage, BigDecimal) =
    connect(ds):
      sql"""SELECT coalesce(sum(tokens_in), 0)::bigint, coalesce(sum(tokens_out), 0)::bigint,
                   coalesce(sum(tokens_cache_read), 0)::bigint, coalesce(sum(tokens_cache_write), 0)::bigint,
                   coalesce(sum(cost_usd), 0)
            FROM agent_steps WHERE job_id = $jobId""".query[(Long, Long, Long, Long, BigDecimal)].run().head match
        case (i, o, cr, cw, c) => (Usage(i, o, cr, cw), c)

  /** Cost of every run since the start of the current UTC day and month — the global caps. */
  def spendDayMonth(ds: DataSource): (BigDecimal, BigDecimal) =
    connect(ds):
      sql"""SELECT coalesce(sum(cost_usd) FILTER (WHERE finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'), 0),
                   coalesce(sum(cost_usd) FILTER (WHERE finished_at >= date_trunc('month', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'), 0)
            FROM agent_steps
            WHERE finished_at >= date_trunc('month', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"""
        .query[(BigDecimal, BigDecimal)].run().head

  // ── trace retention ────────────────────────────────────────────────────────────────────────────

  /** Delete traced messages older than `days` (0 or less = keep everything). The steps — checkpoints,
    * tokens, cost — are never pruned: a resume and the budget sums need them. Returns how many rows
    * went. */
  def pruneMessages(ds: DataSource, days: Int): Int =
    if days <= 0 then 0
    else transact(ds):
      sql"""DELETE FROM agent_messages WHERE created_at < now() - make_interval(days => $days)""".update.run()

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  private final case class ApprovalRow(
      id: Long, jobId: Option[String], kind: String, citekey: Option[String], status: Option[String],
      current: Option[String], edgeKind: Option[String], target: Option[String], proposal: String, state: String,
      note: Option[String], result: Option[String], createdAt: String, decidedAt: Option[String],
      subject: Option[String], seq: Option[Int])
  private given DbCodec[ApprovalRow] = DbCodec.derived

  private val approvalSelect = Sql.raw(
    s"""SELECT id, job_id, kind, citekey, status, current_status, edge_kind, target, proposal::text, state, note,
              result, to_char(created_at AT TIME ZONE 'UTC', $ts), to_char(decided_at AT TIME ZONE 'UTC', $ts),
              subject, seq
       FROM approvals""")

  /** The payload decides how a row reads: a source proposal, a goal's plan, or an article draft. */
  private def toApproval(r: ApprovalRow): ApprovalInfo =
    val base = ApprovalInfo(r.id, r.jobId, r.kind, r.citekey.getOrElse(""), r.status, r.current, r.edgeKind, r.target,
      "", None, r.state, r.note, r.result, r.createdAt, r.decidedAt, r.subject)
    ApprovalKind.parse(r.kind) match
      case Some(ApprovalKind.GoalPlan) =>
        val p = decode[GoalPlans.Plan](r.proposal).toOption
        base.copy(plan = p, evidence = p.map(_.rationale).getOrElse(""))
      case Some(ApprovalKind.ArticlePublish) =>
        val d = decode[GoalPlans.ArticleDraft](r.proposal).toOption
        base.copy(article = d, evidence = d.flatMap(_.summary).getOrElse(""))
      case _ =>
        val p = decode[AgentProposals.Proposal](r.proposal).toOption
        base.copy(evidence = p.map(_.evidence).getOrElse(""), quote = p.flatMap(_.quote))

  /** Record a proposal. `state` is `open` for a person to decide, or `approved` with its `result` when
    * it was applied at once (an `auto` mode). An identical OPEN proposal already there is not doubled:
    * `None` then. */
  def propose(ds: DataSource, jobId: Option[String], p: AgentProposals.Proposal, current: Option[SourceStatus],
      state: ApprovalState = ApprovalState.Open, result: Option[String] = None): Option[ApprovalInfo] =
    val json = p.asJson.noSpaces
    val decided = Option.when(state != ApprovalState.Open)(java.time.Instant.now().toString)
    connect(ds):
      sql"""INSERT INTO approvals (job_id, kind, citekey, status, current_status, edge_kind, target, proposal, state,
                                   result, decided_at)
            VALUES ($jobId, ${p.kind.wire}, ${p.citekey}, ${p.status.map(_.wire)}, ${current.map(_.wire)},
                    ${p.edgeKind.map(EdgeKind.wireOf)}, ${p.target}, $json::jsonb, ${state.wire}, $result,
                    $decided::timestamptz)
            ON CONFLICT DO NOTHING RETURNING id""".query[Long].run().headOption
    .flatMap(approval(ds, _))

  /** Was this change ever proposed (in any state)? A scheduled check does not ask a person twice
    * about an edge they already rejected. */
  def proposedBefore(ds: DataSource, p: AgentProposals.Proposal): Boolean =
    connect(ds):
      sql"""SELECT 1 FROM approvals WHERE kind = ${p.kind.wire} AND citekey = ${p.citekey}
            AND coalesce(status, '') = ${p.status.fold("")(_.wire)}
            AND coalesce(edge_kind, '') = ${p.edgeKind.fold("")(EdgeKind.wireOf)}
            AND coalesce(target, '') = ${p.target.getOrElse("")} LIMIT 1""".query[Int].run().nonEmpty

  def approval(ds: DataSource, id: Long): Option[ApprovalInfo] =
    connect(ds)((approvalSelect ++ Sql.of(" WHERE id = ?", Param.L(id))).frag.query[ApprovalRow].run().headOption
      .map(toApproval))

  /** The verified proposal behind an approval. */
  def proposal(ds: DataSource, id: Long): Option[AgentProposals.Proposal] =
    connect(ds)(sql"SELECT proposal::text FROM approvals WHERE id = $id".query[String].run().headOption)
      .flatMap(j => decode[AgentProposals.Proposal](j).toOption)

  /** Newest first; `state` narrows (none = every state). */
  def approvals(ds: DataSource, state: Option[ApprovalState], limit: Int): List[ApprovalInfo] =
    val where = state.fold(Sql.empty)(s => Sql.of(" WHERE state = ?", Param.S(s.wire)))
    connect(ds):
      (approvalSelect ++ where ++ Sql.of(" ORDER BY created_at DESC, id DESC LIMIT ?", Param.I(math.max(1, limit))))
        .frag.query[ApprovalRow].run().toList.map(toApproval)

  /** The approval step `seq` of run `jobId` waits on, opened if it is not there yet (a resumed run
    * finds the one it opened before, never a second). `state` = approved with a `result` records one
    * applied at once (an `auto` mode). */
  def openForStep(ds: DataSource, jobId: String, seq: Int, kind: ApprovalKind, subject: String, payload: String,
      state: ApprovalState = ApprovalState.Open, result: Option[String] = None): ApprovalInfo =
    val decided = Option.when(state != ApprovalState.Open)(java.time.Instant.now().toString)
    connect(ds):
      sql"""INSERT INTO approvals (job_id, seq, kind, subject, proposal, state, result, decided_at)
            VALUES ($jobId, $seq, ${kind.wire}, $subject, $payload::jsonb, ${state.wire}, $result, $decided::timestamptz)
            ON CONFLICT (job_id, seq) WHERE seq IS NOT NULL DO NOTHING""".update.run()
    forStep(ds, jobId, seq).get

  /** The approval step `seq` of run `jobId` opened, if it did. */
  def forStep(ds: DataSource, jobId: String, seq: Int): Option[ApprovalInfo] =
    connect(ds)((approvalSelect ++ Sql.of(" WHERE job_id = ? AND seq = ?", Param.S(jobId), Param.I(seq)))
      .frag.query[ApprovalRow].run().headOption.map(toApproval))

  /** A cancelled run's open approvals: rejected, so nobody decides for a run that is gone. */
  def closeForRun(ds: DataSource, jobId: String, why: String): Int =
    transact(ds):
      sql"""UPDATE approvals SET state = ${ApprovalState.Rejected.wire}, result = $why, decided_at = now()
            WHERE job_id = $jobId AND seq IS NOT NULL AND state = ${ApprovalState.Open.wire}""".update.run()

  /** Compare-and-set an OPEN approval to its decision. `false` when it had already been decided. */
  def decide(ds: DataSource, id: Long, to: ApprovalState, note: Option[String], result: Option[String]): Boolean =
    transact(ds):
      sql"""UPDATE approvals SET state = ${to.wire}, note = $note, result = $result, decided_at = now()
            WHERE id = $id AND state = ${ApprovalState.Open.wire}""".update.run() > 0

  /** After [[decide]]: the final state and what applying the decision did. */
  def settle(ds: DataSource, id: Long, to: ApprovalState, result: String): Unit =
    val _ = transact(ds):
      sql"UPDATE approvals SET state = ${to.wire}, result = $result WHERE id = $id".update.run()

  // ── corpus facts the verifiers need ────────────────────────────────────────────────────────────

  def slugExists(ds: DataSource, slug: String): Boolean =
    connect(ds)(sql"SELECT count(*) FROM articles WHERE slug = $slug".query[Long].run().head > 0)

  /** The newest saved version of an article: after a patch, the row holding the text it replaced. */
  def latestArticleVersion(ds: DataSource, slug: String): Option[Long] =
    connect(ds)(sql"SELECT max(id) FROM article_versions WHERE slug = $slug".query[Option[Long]].run().headOption.flatten)
