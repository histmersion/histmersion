package com.histmersion.db

import com.histmersion.core.Agents.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The store of embedded agent runs (migration V035): a run is a `jobs` row with kind `agent`, its
  * steps are `agent_steps` (one row per attempt; a `done` row is the checkpoint a resume replays) and
  * its trace is `agent_messages`. Token and cost totals are sums over the steps, never a second copy. */
object AgentRepo:

  private final case class RunRow(
      id: String, pipeline: Option[String], target: Option[String], status: String, progress: Option[String],
      error: Option[String], model: Option[String], budgetUsd: Option[BigDecimal], budgetTokens: Option[Long],
      warnings: List[String], createdAt: String, updatedAt: String,
      tin: Long, tout: Long, tcr: Long, tcw: Long, cost: BigDecimal)
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
              coalesce(u.tin, 0), coalesce(u.tout, 0), coalesce(u.tcr, 0), coalesce(u.tcw, 0), coalesce(u.cost, 0)
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
      r.budgetTokens.getOrElse(0L), r.warnings, r.createdAt, r.updatedAt)

  /** A `queued` run. */
  def create(ds: DataSource, p: Pipeline, target: String, model: Option[String], budgetUsd: BigDecimal,
      budgetTokens: Long): RunInfo =
    val id = java.util.UUID.randomUUID().toString
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, citekey, status, pipeline, pipeline_version, target_kind, target_id,
                              model, budget_usd, budget_tokens)
            VALUES ($id, $JobKind, ${Option.when(p.targetKind == "source")(target)}, ${RunState.Queued.wire},
                    ${p.wire}, ${p.version}, ${p.targetKind}, $target, $model, $budgetUsd, $budgetTokens)""".update.run()
    get(ds, id).get

  def get(ds: DataSource, id: String): Option[RunInfo] =
    connect(ds)((runSelect ++ Sql.of(" AND j.id = ?", Param.S(id))).frag.query[RunRow].run().headOption.map(toInfo))

  /** The pipeline version a run was started with (a resume under a changed pipeline is refused). */
  def pipelineVersion(ds: DataSource, id: String): Option[Int] =
    connect(ds)(sql"SELECT pipeline_version FROM jobs WHERE id = $id".query[Option[Int]].run().headOption.flatten)

  def list(ds: DataSource, status: Option[String], limit: Int): Vector[RunInfo] =
    val where = status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) => Sql.of(" AND j.status = ?", Param.S(s))
      case None    => Sql.empty
    connect(ds):
      (runSelect ++ where ++ Sql.of(" ORDER BY j.created_at DESC, j.id LIMIT ?", Param.I(math.max(1, limit))))
        .frag.query[RunRow].run().map(toInfo)

  /** Compare-and-set state change through [[RunState.next]]: `None` when refused or when the row had
    * already moved on (another worker, a concurrent cancel). */
  def transition(ds: DataSource, id: String, from: RunState, to: RunState, error: Option[String] = None,
      progress: Option[String] = None): Option[RunState] =
    RunState.next(from, to).flatMap { target =>
      val n = transact(ds):
        sql"""UPDATE jobs SET status = ${target.wire}, error = $error, progress = $progress, updated_at = now()
              WHERE id = $id AND kind = $JobKind AND status = ${from.wire}""".update.run()
      Option.when(n > 0)(target)
    }

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

  // ── corpus facts the verifiers need ────────────────────────────────────────────────────────────

  /** A gazetteer point for a place NAME: an exact (case-insensitive) match on a place's label or one
    * of its names, the point on its surface. Ambiguity is resolved the way a person would — a site
    * before a polity before a region — and the label comes back so the caller can say what it chose. */
  def geocode(ds: DataSource, name: String): Option[(String, Double, Double)] =
    val n = name.trim
    if n.isEmpty then None
    else connect(ds):
      sql"""SELECT p.label, ST_X(ST_PointOnSurface(p.geom)), ST_Y(ST_PointOnSurface(p.geom))
            FROM places p
            WHERE p.geom IS NOT NULL
              AND (lower(p.label) = lower($n)
                   OR EXISTS (SELECT 1 FROM place_names m WHERE m.place_id = p.id AND lower(m.name) = lower($n)))
            ORDER BY CASE p.kind WHEN 'site' THEN 0 WHEN 'feature' THEN 1 WHEN 'polity' THEN 2 ELSE 3 END,
                     ST_Area(p.geom), p.id
            LIMIT 1""".query[(String, Double, Double)].run().headOption

  def slugExists(ds: DataSource, slug: String): Boolean =
    connect(ds)(sql"SELECT count(*) FROM articles WHERE slug = $slug".query[Long].run().head > 0)
