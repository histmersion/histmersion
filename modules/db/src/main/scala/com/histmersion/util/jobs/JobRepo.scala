package com.histmersion.util.jobs

import com.histmersion.db.*
import com.histmersion.db.given

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Flat projection of `jobs` (positional, like the other write-path rows). */
final private[jobs] case class JobRow(
    id: String,
    kind: String,
    status: String,
    citekey: Option[String],
    progress: Option[String],
    error: Option[String],
    sha256: Option[String],
    createdAt: String,
    updatedAt: String,
    acquireItem: Option[Int],
    detail: Option[String]
) derives DbCodec

/** OCR jobs (plan §1.8). The row is the durable part of a job; the queue itself is in memory, which is precisely why a
  * restart must reconcile: [[failInterrupted]] turns every `running` row into `failed: interrupted` at startup, so no
  * job is left claiming to be in progress by a process that no longer exists. Re-running is idempotent — the same file
  * has the same sha256.
  */
object JobRepo:

  /** ONE projection, reused by both reads (AGENTS.md: no duplicated column lists). Magnum's `sql""` cannot splice a
    * `Frag`, so composition goes through the module's own [[Sql]] fragment type.
    */
  private val select = Sql.raw("""SELECT id, kind, status, citekey, progress, error, sha256,
              to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              acquire_item, detail::text
       FROM jobs""")

  /** A stored detail this build cannot read (written by a newer one) is left out, not fatal. */
  private def toInfo(r: JobRow): JobInfo =
    JobInfo(
      r.id,
      r.kind,
      r.status,
      r.citekey,
      r.progress,
      r.error,
      r.sha256,
      r.createdAt,
      r.updatedAt,
      r.acquireItem,
      r.detail.flatMap(Jsonb.read[JobDetail])
    )

  private def json(d: JobDetail): String = Jsonb.write(d)

  /** Create a `queued` job. The id is opaque to clients; a UUID keeps it unguessable and unique. `acquireItem` is the
    * acquisition item the job ticks off when it succeeds; `detail` what the job is about beyond its citekey.
    */
  def create(
      ds: DataSource,
      kind: String,
      citekey: Option[String],
      sha256: Option[String],
      acquireItem: Option[Int] = None,
      detail: Option[JobDetail] = None
  ): JobInfo =
    val id = java.util.UUID.randomUUID().toString
    val js = detail.map(json)
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, citekey, status, sha256, acquire_item, detail)
            VALUES ($id, $kind, $citekey, ${JobState.Queued.wire}, $sha256, $acquireItem, $js::jsonb)""".update.run()
    get(ds, id).getOrElse(
      JobInfo(id, kind, JobState.Queued.wire, citekey, None, None, sha256, acquireItem = acquireItem, detail = detail)
    )

  /** Replace a job's [[JobDetail]] — e.g. an init job adding the periods it skipped. */
  def setDetail(ds: DataSource, id: String, detail: JobDetail): Unit =
    val js = json(detail)
    val _ = transact(ds)(sql"UPDATE jobs SET detail = $js::jsonb WHERE id = $id".update.run())

  def get(ds: DataSource, id: String): Option[JobInfo] =
    connect(ds):
      (select ++ Sql.of(" WHERE id = ?", Param.S(id))).frag.query[JobRow].run().headOption.map(toInfo)

  /** Newest first; `status` (when given) narrows to one state. */
  def list(ds: DataSource, status: Option[String], limit: Int): Vector[JobInfo] =
    val where = status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) => Sql.of(" WHERE status = ?", Param.S(s))
      case None    => Sql.empty
    val q = select ++ where ++ Keyset.orderBy("") ++ Sql.of(" LIMIT ?", Param.I(math.max(1, limit)))
    connect(ds)(q.frag.query[JobRow].run().map(toInfo))

  /** One page of the newest-first list ([[com.histmersion.api.Paging]]): `before` = only jobs older than that one,
    * `upTo` = only jobs down to and including it. Left: a cursor that names no job. Returns the page and whether older
    * jobs exist.
    */
  def page(
      ds: DataSource,
      status: Option[String],
      limit: Int,
      before: Option[String] = None,
      upTo: Option[String] = None
  ): Either[String, (Vector[JobInfo], Boolean)] =
    page(ds, JobQuery(status = status.map(_.trim.toLowerCase).filter(_.nonEmpty).toList), limit, before, upTo)

  /** How many jobs are in each state — the whole table (one `GROUP BY` over the status index). */
  /** The job queue by state, on the caller's connection (the jobs' figures). */
  def states(using DbCon): List[(String, Int)] =
    sql"SELECT status, count(*)::int FROM jobs GROUP BY status".query[(String, Int)].run().toList

  def stateCounts(ds: DataSource): List[(String, Int)] = Keyset.counts(ds, Sql.raw(" FROM jobs"), "status")

  private def conds(q: JobQuery): ListSql.Conds =
    val text = q.q.fold(Sql.empty) { t =>
      val p = Param.S(ListSql.contains(t))
      Sql.of(
        " AND (citekey ILIKE ? OR target_id ILIKE ? OR pipeline ILIKE ? OR detail::text ILIKE ? OR id LIKE ?)",
        p,
        p,
        p,
        p,
        Param.S(ListSql.contains(t).drop(1))
      )
    }
    ListSql.Conds(
      List(
        "status" -> ListSql.anyOf("status", q.status),
        "kind" -> ListSql.anyOf("kind", q.kinds),
        "time" -> ListSql.between("created_at", q.since, q.until),
        "q" -> text
      )
    )

  /** One page of the jobs that match `q`, newest first. Left: a cursor that names no job. */
  def page(
      ds: DataSource,
      q: JobQuery,
      limit: Int,
      before: Option[String],
      upTo: Option[String]
  ): Either[String, (Vector[JobInfo], Boolean)] =
    (before.toList ++ upTo.toList).find(c => !Keyset.exists(ds, "jobs", Param.S(c))) match
      case Some(c) => Left(s"No job '$c' to page from")
      case None    =>
        val (rows, more) = Keyset.page[JobRow](
          ds,
          select,
          Sql.raw(" WHERE true") ++ conds(q).all,
          "",
          "jobs",
          math.max(1, limit),
          before.map(Param.S(_)),
          upTo.map(Param.S(_)),
          r => Param.S(r.id)
        )
        Right((rows.map(toInfo), more))

  /** The counts that go with a filtered page: per state, and the `kind` facet — each under every filter but its own.
    */
  /** The per-state counts and the kind facet (at most `facetMax` kinds) under `q`. */
  def facets(ds: DataSource, q: JobQuery, facetMax: Int): (List[(String, Int)], List[(String, List[(String, Int)])]) =
    val c = conds(q)
    def from(except: String) = Sql.raw(" FROM jobs WHERE true") ++ c.except(except)
    (Keyset.counts(ds, from("status"), "status"), List("kind" -> ListSql.facet(ds, from("kind"), "kind", facetMax)))

  /** Free-text progress on a `running` job — what the CLI's progress line prints. */
  def progress(ds: DataSource, id: String, text: String): Unit =
    val _ = transact(ds):
      sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND status = ${JobState.Running.wire}".update
        .run()

  /** Apply a [[JobState]] transition, guarded BY THE STORE as well as by the state machine: the `status = <from>`
    * predicate makes the update a compare-and-set, so two workers cannot both claim the same job. Returns the new
    * state, or `None` when the transition was refused.
    */
  def transition(
      ds: DataSource,
      id: String,
      from: JobState,
      to: JobState,
      progress: Option[String] = None,
      error: Option[String] = None
  ): Option[JobState] =
    JobState.next(from, to).flatMap { target =>
      val updated = transact(ds):
        sql"""UPDATE jobs SET status = ${target.wire}, progress = $progress, error = $error, updated_at = now()
              WHERE id = $id AND status = ${from.wire}""".update.run()
      if updated > 0 then Some(target) else None
    }

  /** Startup reconciliation: nothing survives the process, so anything still `running` failed with it — and a
    * background job a person paused, whose way to go on was the process's. Returns how many rows were corrected. Agent
    * runs are NOT touched: their finished steps are checkpointed, so an interrupted run is paused and resumable
    * (`JobRuns.reconcile`). * `except`: the jobs this process is still running or holding queued — a config reload that
    * swapped back to a database (docs/research-plugins.md §3.6) must not fail its own work there.
    */
  def failInterrupted(ds: DataSource, except: List[String] = Nil): Int =
    transact(ds):
      sql"""UPDATE jobs SET status = ${JobState.Failed.wire}, error = ${JobState.Interrupted},
                            progress = NULL, updated_at = now()
            WHERE status IN (${JobState.Running.wire}, ${JobState.Queued.wire}, ${JobState.Paused.wire})
              AND kind <> ${Agents.JobKind}
              AND NOT (id = ANY($except::text[]))""".update.run()

  // ── runs: jobs that run as checkpointed steps (agent runs) ───────────────────────────────────

  /** A new run's row; its id. */
  def insertRun(r: RunRow)(using DbCon): String =
    val id = java.util.UUID.randomUUID().toString
    sql"""INSERT INTO jobs (id, kind, citekey, status, pipeline, pipeline_version, target_kind, target_id,
                            model, budget_usd, budget_tokens, params, acquire_item, goal_slug, parent_job, origin,
                            dry_run, manager_id, manager_item)
          VALUES ($id, ${r.kind}, ${r.citekey}, ${r.status},
                  ${r.pipeline}, ${r.pipelineVersion}, ${r.targetKind}, ${r.target}, ${r.model}, ${r.budgetUsd},
                  ${r.budgetTokens}, ${r.params}::jsonb, ${r.acquireItem}, ${r.goal}, ${r.parent}, ${r.origin},
                  ${r.dryRun}, ${r.managerId}, ${r.managerItem})""".update
      .run()
    id

  /** The pipeline version a run was started with. */
  def runPipelineVersion(id: String)(using DbCon): Option[Int] =
    sql"SELECT pipeline_version FROM jobs WHERE id = $id".query[Option[Int]].run().headOption.flatten

  def runHeartbeat(kind: String, id: String)(using DbCon): Unit =
    val _ = sql"UPDATE jobs SET heartbeat_at = now() WHERE id = $id AND kind = $kind".update.run()

  /** How many runs of `kind` are in one of `statuses`. */
  def runsIn(kind: String, statuses: (String, String))(using DbCon): Int =
    sql"""SELECT count(*)::int FROM jobs WHERE kind = $kind
          AND status IN (${statuses._1}, ${statuses._2})""".query[Int].run().head

  /** Compare-and-set: `from` → `to`, setting the error, progress and resume time (None clears them). */
  def moveRun(
      kind: String,
      id: String,
      from: String,
      to: String,
      error: Option[String],
      progress: Option[String],
      resumeAt: Option[String]
  )(using DbCon): Boolean =
    sql"""UPDATE jobs SET status = $to, error = $error, progress = $progress,
                          resume_at = $resumeAt::timestamptz, updated_at = now()
          WHERE id = $id AND kind = $kind AND status = $from""".update.run() > 0

  /** The `paused` runs whose resume time has come, oldest resume first. */
  def runsDue(kind: String, paused: String, at: String)(using DbCon): Vector[String] =
    sql"""SELECT id FROM jobs WHERE kind = $kind AND status = $paused
                                AND resume_at IS NOT NULL AND resume_at <= $at::timestamptz
          ORDER BY resume_at, created_at""".query[String].run()

  /** The `paused` runs whose error starts like `like` and that do not resume by themselves before `at`: (id, pipeline,
    * model, error).
    */
  def runsPausedLike(
      kind: String,
      paused: String,
      like: String,
      at: String
  )(using DbCon): Vector[(String, String, Option[String], String)] =
    sql"""SELECT id, coalesce(pipeline, ''), model, coalesce(error, '') FROM jobs
          WHERE kind = $kind AND status = $paused AND error LIKE $like
            AND (resume_at IS NULL OR resume_at > $at::timestamptz)
          ORDER BY updated_at, created_at""".query[(String, String, Option[String], String)].run()

  def runProgress(kind: String, id: String, text: String)(using DbCon): Unit =
    val _ = sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND kind = $kind".update.run()

  def runWarning(kind: String, id: String, w: String)(using DbCon): Unit =
    val _ = sql"""UPDATE jobs SET warnings = array_append(warnings, $w), updated_at = now()
                  WHERE id = $id AND kind = $kind""".update.run()

  def runBudget(kind: String, id: String, usd: Option[BigDecimal], tokens: Option[Long])(using DbCon): Unit =
    val _ =
      sql"""UPDATE jobs SET budget_usd = coalesce($usd, budget_usd), budget_tokens = coalesce($tokens, budget_tokens),
                          updated_at = now() WHERE id = $id AND kind = $kind""".update.run()

  /** The `running` runs (but `except`) moved to `paused` with `error`, returned with their origin; then the `queued`
    * ones (but `except`), oldest first.
    */
  def runsReconciled(
      kind: String,
      running: String,
      paused: String,
      error: String,
      queued: String,
      except: List[String]
  )(using DbCon): (Vector[(String, Option[String])], Vector[String]) =
    val moved = sql"""UPDATE jobs SET status = $paused, error = $error, updated_at = now()
                      WHERE kind = $kind AND status = $running AND NOT (id = ANY($except::text[]))
                      RETURNING id, origin""".query[(String, Option[String])].run()
    val waiting = sql"""SELECT id FROM jobs WHERE kind = $kind AND status = $queued
                          AND NOT (id = ANY($except::text[]))
                        ORDER BY created_at""".query[String].run()
    (moved, waiting)

  /** A `paused` run's resume time (unless it moved on meanwhile). */
  def runResumesAt(kind: String, id: String, paused: String, at: String)(using DbCon): Boolean =
    sql"""UPDATE jobs SET resume_at = $at::timestamptz, updated_at = now()
          WHERE id = $id AND kind = $kind AND status = $paused""".update.run() > 0

  // ── merge apply (#888) ─────────────────────────────────────────────────────────────────────────

  /** Start a merge apply: a `running` row at once (it runs on its own thread, never queued), its bundle in `sha256` and
    * its plan in `detail`. A restart fails it as `interrupted` like every other unfinished job ([[failInterrupted]]),
    * so a poll never sees it running forever.
    */
  def startMergeApply(ds: DataSource, bundle: String, planHash: String): String =
    val id = java.util.UUID.randomUUID().toString
    val js = json(com.histmersion.util.data.exchange.ExchangeJobKinds.PlanHash(Some(planHash)))
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, status, sha256, detail)
            VALUES ($id, ${MergeApplyJob.JobKind}, ${JobState.Running.wire}, $bundle, $js::jsonb)""".update.run()
    id

  /** Record how a merge apply ended: `done` with its report (applied or refused), or `failed` with why. A row a restart
    * already failed is left as it is.
    */
  def finishMergeApply(ds: DataSource, id: String, outcome: Either[String, MergeApplyResult]): Unit =
    val (status, error, result) = outcome match
      case Right(r) => (JobState.Done, None, Some(Jsonb.write(r)))
      case Left(e)  => (JobState.Failed, Some(e), None)
    val _ = transact(ds):
      sql"""UPDATE jobs SET status = ${status.wire}, error = $error, result = $result::jsonb, updated_at = now()
            WHERE id = $id AND kind = ${MergeApplyJob.JobKind} AND status = ${JobState.Running.wire}""".update.run()

  /** One merge apply as the API answers it; `None` for an unknown id (or another kind of job). The time so far of a
    * running one is measured against the database's clock.
    */
  def mergeApply(ds: DataSource, id: String): Option[MergeApplyJob] =
    connect(ds):
      sql"""SELECT id, status, coalesce(sha256, ''), coalesce(detail->>'planHash', ''),
                   (extract(epoch FROM (CASE WHEN status = ${JobState.Running.wire} THEN clock_timestamp()
                                            ELSE updated_at END) - created_at) * 1000)::bigint,
                   error, result::text
            FROM jobs WHERE id = $id AND kind = ${MergeApplyJob.JobKind}"""
        .query[(String, String, String, String, Long, Option[String], Option[String])]
        .run()
        .headOption
        .map { (id, status, bundle, plan, ms, error, result) =>
          MergeApplyJob.of(
            id,
            JobState.parse(status).getOrElse(JobState.Failed),
            bundle,
            plan,
            math.max(0L, ms),
            error,
            result.flatMap(Jsonb.read[MergeApplyResult])
          )
        }

  /** Is a merge apply running? One at a time. */
  def mergeApplyRunning(ds: DataSource): Boolean =
    connect(ds):
      sql"SELECT 1 FROM jobs WHERE kind = ${MergeApplyJob.JobKind} AND status = ${JobState.Running.wire} LIMIT 1"
        .query[Int]
        .run()
        .nonEmpty
