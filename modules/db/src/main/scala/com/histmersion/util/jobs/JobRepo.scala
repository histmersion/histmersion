package com.histmersion
package util.jobs
import com.histmersion.util.jobs.Job
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.{Jsonb, Keyset, ListSql}
import com.histmersion.api.automation.Agents
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult}
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.library.Citekey
import com.histmersion.api.Sha256
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.AgentJobKinds
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.Pipeline
import com.histmersion.util.data.exchange.MergeRepo
import com.histmersion.util.data.exchange.MergeRow
import com.histmersion.api.exchange.ExchangeJobKinds
import com.histmersion.api.exchange.MergePlan

/** OCR jobs (plan §1.8). The row is the durable part of a job; the queue itself is in memory, which is precisely why a
  * restart must reconcile: [[failInterrupted]] turns every `running` row into `failed: interrupted` at startup, so no
  * job is left claiming to be in progress by a process that no longer exists. Re-running is idempotent — the same file
  * has the same sha256.
  */
object JobRepo:
  type DueAt = DueAt.T
  object DueAt extends Newtype[String, Pure]
  type PageCursor = PageCursor.T
  object PageCursor extends Newtype[String, Pure]
  type ResumeAt = ResumeAt.T
  object ResumeAt extends Newtype[String, Pure]
  type TokenBudget = TokenBudget.T
  object TokenBudget extends Newtype[Long, Pure]
  type WarningText = WarningText.T
  object WarningText extends Newtype[String, Pure]

  /** ONE projection, reused by both reads (AGENTS.md: no duplicated column lists), nested into each as a `Frag`. */
  private val select = sql"""SELECT id, kind, status, citekey, progress, error, sha256,
              to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              acquire_item, detail::text
       FROM jobs"""

  /** A stored detail this build cannot read (written by a newer one) is left out, not fatal. */
  private def toInfo(r: JobRow): JobInfo =
    JobInfo(
      SpatialHierarchyResult.Job.Id(r.id.value),
      JobKinds.Wire(r.kind.value),
      JobInfo.Status(r.status.value),
      r.citekey.map(c => Citekey(c.value)),
      r.progress.map(p => JobInfo.Progress(p.value)),
      r.error,
      r.sha256.map(h => Sha256(h.value)),
      JobInfo.CreatedAt(r.createdAt.value),
      JobInfo.UpdatedAt(r.updatedAt.value),
      r.acquireItem.map(a => Queue.QueueItem.Id(a.value)),
      r.detail.flatMap(d => Jsonb.read[JobDetail](Jsonb.JsonText(d.value)))
    )

  private def json(d: JobDetail): Jsonb.JsonText = Jsonb.write(d)

  /** Create a `queued` job. The id is opaque to clients; a UUID keeps it unguessable and unique. `acquireItem` is the
    * acquisition item the job ticks off when it succeeds; `detail` what the job is about beyond its citekey.
    */
  def create(
      kind: Job.Kind,
      citekey: Option[com.histmersion.domain.library.Citekey],
      sha256: Option[com.histmersion.Sha256],
      acquireItem: Option[JobRow.AcquireItem] = None,
      detail: Option[JobDetail] = None
  )(using DbCon): JobInfo =
    val id = java.util.UUID.randomUUID().toString
    val js = detail.map(json)
    sql"""INSERT INTO jobs (id, kind, citekey, status, sha256, acquire_item, detail)
          VALUES ($id, $kind, $citekey, ${JobState.Queued.wire}, $sha256, $acquireItem, $js::jsonb)""".update.run()
    get(Job.Id.assume(id)).getOrElse(
      JobInfo(
        SpatialHierarchyResult.Job.Id(id),
        JobKinds.Wire(kind.value),
        JobState.Queued.wire,
        citekey.map(c => Citekey(c.value)),
        None,
        None,
        sha256.map(h => Sha256(h.value)),
        acquireItem = acquireItem.map(a => Queue.QueueItem.Id(a.value)),
        detail = detail
      )
    )

  /** Replace a job's [[JobDetail]] — e.g. an init job adding the periods it skipped. */
  def setDetail(id: Job.Id, detail: JobDetail)(using DbCon): Unit =
    val js = json(detail)
    val _ = (sql"UPDATE jobs SET detail = $js::jsonb WHERE id = $id".update.run())

  def get(id: Job.Id)(using DbCon): Option[JobInfo] =
    sql"$select WHERE id = $id".query[JobRow].run().headOption.map(toInfo)

  /** Newest first; `status` (when given) narrows to one state. */
  def list(status: Option[JobState.Wire], limit: PageSize)(using DbCon): Vector[JobInfo] =
    val where = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) => sql" WHERE status = $s"
      case None    => sql""
    val q = sql"$select$where${Keyset.orderBy(sql"")} LIMIT ${math.max(1, limit.value)}"
    (q.query[JobRow].run().map(toInfo))

  /** One page of the newest-first list ([[com.histmersion.api.Paging]]): `before` = only jobs older than that one,
    * `upTo` = only jobs down to and including it. Left: a cursor that names no job. Returns the page and whether older
    * jobs exist.
    */
  def page(
      status: Option[JobState.Wire],
      limit: PageSize,
      before: Option[PageCursor] = None,
      upTo: Option[PageCursor] = None
  )(using DbCon): Either[Problem, (Vector[JobInfo], Boolean)] =
    page(
      JobQuery(status = status.map(st => JobQuery.Status(st.value.trim.toLowerCase)).filter(_.value.nonEmpty).toList),
      limit,
      before,
      upTo
    )

  /** The job queue by state, on the caller's connection (the jobs' figures). */
  def states(using DbCon): List[(Keyset.State, RowCount)] =
    sql"SELECT status, count(*)::int FROM jobs GROUP BY status"
      .query[(String, Int)]
      .run()
      .toList
      .map((state, n) => (Keyset.State(state), RowCount(n)))

  /** How many jobs are in each state — the whole table (one `GROUP BY` over the status index). */
  def stateCounts(using DbCon): List[(Keyset.State, RowCount)] =
    Keyset.countsOn(sql" FROM jobs", sql"status")

  private def conds(q: JobQuery): ListSql.Conds =
    val text = q.q.fold(sql"") { t =>
      val p = ListSql.contains(t.value)
      sql" AND (citekey ILIKE $p OR target_id ILIKE $p OR pipeline ILIKE $p OR detail::text ILIKE $p OR id LIKE ${ListSql.contains(t.value).drop(1)})"
    }
    ListSql.Conds(
      List(
        "status" -> ListSql.anyOf(sql"status", q.status.map(_.value)),
        "kind" -> ListSql.anyOf(sql"kind", q.kinds.map(_.value)),
        "time" -> ListSql.between(sql"created_at", q.since, q.until),
        "q" -> text
      )
    )

  /** One page of the jobs that match `q`, newest first. Left: a cursor that names no job. */
  def page(
      q: JobQuery,
      limit: PageSize,
      before: Option[PageCursor],
      upTo: Option[PageCursor]
  )(using DbCon): Either[Problem, (Vector[JobInfo], Boolean)] =
    (before.toList ++ upTo.toList).find(c => !Keyset.existsOn(sql"jobs", c)) match
      case Some(c) => Left(Problem.NotFound.Entity(Problem.TypeName.of(Job.Id), Problem.Raw.of(c)))
      case None    =>
        val (rows, more) = Keyset.pageOn[JobRow, PageCursor, JobRow.Id](
          select,
          sql" WHERE true${conds(q).all}",
          Keyset.Table(sql"jobs"),
          limit,
          before,
          upTo,
          _.id
        )
        Right((rows.map(toInfo), more))

  /** The counts that go with a filtered page: per state, and the `kind` facet (at most `facetMax` kinds) — each under
    * every filter but its own.
    */
  def facets(q: JobQuery, facetMax: PageSize)(using
      DbCon
  ): (List[(Keyset.State, RowCount)], List[(String, List[(String, RowCount)])]) =
    val c = conds(q)
    def from(except: String) = sql" FROM jobs WHERE true${c.except(except)}"
    (Keyset.countsOn(from("status"), sql"status"), List("kind" -> ListSql.facetOn(from("kind"), sql"kind", facetMax)))

  /** Free-text progress on a `running` job — what the CLI's progress line prints. */
  def progress(id: Job.Id, text: JobRow.Progress)(using DbCon): Unit =
    val _ =
      sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND status = ${JobState.Running.wire}".update
        .run()

  /** Apply a [[JobState]] transition, guarded BY THE STORE as well as by the state machine: the `status = <from>`
    * predicate makes the update a compare-and-set, so two workers cannot both claim the same job. Returns the new
    * state, or `None` when the transition was refused.
    */
  def transition(
      id: Job.Id,
      from: JobState,
      to: JobState,
      progress: Option[JobRow.Progress] = None,
      error: Option[NonEmptyVector[Problem]] = None
  )(using DbCon): Option[JobState] =
    JobState.next(from, to).flatMap { target =>
      val updated =
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
  def failInterrupted(except: List[Job.Id] = Nil)(using DbCon): RowCount =
    val interrupted = NonEmptyVector.of[Problem](Problem.Run.Interrupted)
    RowCount(
      sql"""UPDATE jobs SET status = ${JobState.Failed.wire}, error = $interrupted,
                          progress = NULL, updated_at = now()
          WHERE status IN (${JobState.Running.wire}, ${JobState.Queued.wire}, ${JobState.Paused.wire})
            AND kind <> ${AgentJobKinds.Agent.kind}
            AND NOT (id = ANY($except::text[]))""".update.run()
    )

  // ── runs: jobs that run as checkpointed steps (agent runs) ───────────────────────────────────

  /** A new run's row; its id. */
  def insertRun(r: RunRow)(using DbCon): Run.Id =
    val id = java.util.UUID.randomUUID().toString
    sql"""INSERT INTO jobs (id, kind, citekey, status, pipeline, pipeline_version, target_kind, target_id,
                            model, budget_usd, budget_tokens, params, acquire_item, goal_slug, parent_job, origin,
                            dry_run, manager_id, manager_item)
          VALUES ($id, ${r.kind}, ${r.citekey}, ${r.status},
                  ${r.pipeline}, ${r.pipelineVersion}, ${r.targetKind}, ${r.target}, ${r.model}, ${r.budgetUsd},
                  ${r.budgetTokens}, ${r.params}::jsonb, ${r.acquireItem}, ${r.goal}, ${r.parent}, ${r.origin},
                  ${r.dryRun}, ${r.managerId}, ${r.managerItem})""".update
      .run()
    Run.Id.applyUnsafe(id)

  /** The pipeline version a run was started with. */
  def runPipelineVersion(id: Run.Id)(using DbCon): Option[Pipeline.Version] =
    sql"SELECT pipeline_version FROM jobs WHERE id = $id"
      .query[Option[Int]]
      .run()
      .headOption
      .flatten
      .map(Pipeline.Version(_))

  def runHeartbeat(kind: Job.Kind, id: Run.Id)(using DbCon): Unit =
    val _ = sql"UPDATE jobs SET heartbeat_at = now() WHERE id = $id AND kind = $kind".update.run()

  /** How many runs of `kind` are in one of `statuses`. */
  def runsIn(kind: Job.Kind, statuses: (RunRow.Status, RunRow.Status))(using DbCon): RowCount =
    RowCount(
      sql"""SELECT count(*)::int FROM jobs WHERE kind = $kind
          AND status IN (${statuses._1}, ${statuses._2})""".query[Int].run().head
    )

  /** Compare-and-set: `from` → `to`, setting the error, progress and resume time (None clears them). */
  def moveRun(
      kind: Job.Kind,
      id: Run.Id,
      from: RunRow.Status,
      to: RunRow.Status,
      error: Option[Problem],
      progress: Option[JobRow.Progress],
      resumeAt: Option[ResumeAt]
  )(using DbCon): Boolean =
    val kept = error.map(NonEmptyVector.of(_))
    sql"""UPDATE jobs SET status = $to, error = $kept, progress = $progress,
                          resume_at = $resumeAt::timestamptz, updated_at = now()
          WHERE id = $id AND kind = $kind AND status = $from""".update.run() > 0

  /** The `paused` runs whose resume time has come, oldest resume first. */
  def runsDue(kind: Job.Kind, paused: RunRow.Status, at: DueAt)(using DbCon): Vector[Run.Id] =
    sql"""SELECT id FROM jobs WHERE kind = $kind AND status = $paused
                                AND resume_at IS NOT NULL AND resume_at <= $at::timestamptz
          ORDER BY resume_at, created_at, id""".query[Run.Id].run()

  /** The `paused` runs that do not resume by themselves before `at`: (id, pipeline, model, the problem its row keeps).
    */
  def runsPausedUntil(
      kind: Job.Kind,
      paused: RunRow.Status,
      at: DueAt
  )(using DbCon): Vector[(Run.Id, Pipeline.Wire, Option[RunRow.Model], Option[Problem])] =
    sql"""SELECT id, coalesce(pipeline, ''), model, error FROM jobs
          WHERE kind = $kind AND status = $paused
            AND (resume_at IS NULL OR resume_at > $at::timestamptz)
          ORDER BY updated_at, created_at, id"""
      .query[(Run.Id, Pipeline.Wire, Option[RunRow.Model], Option[NonEmptyVector[Problem]])]
      .run()
      .map((id, pipeline, model, error) => (id, pipeline, model, error.map(Problems.asOne)))

  def runProgress(kind: Job.Kind, id: Run.Id, text: JobRow.Progress)(using DbCon): Unit =
    val _ = sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND kind = $kind".update.run()

  def runWarning(kind: Job.Kind, id: Run.Id, w: WarningText)(using DbCon): Unit =
    val _ = sql"""UPDATE jobs SET warnings = array_append(warnings, $w), updated_at = now()
                  WHERE id = $id AND kind = $kind""".update.run()

  def runBudget(kind: Job.Kind, id: Run.Id, usd: Option[BigDecimal], tokens: Option[TokenBudget])(using DbCon): Unit =
    val _ =
      sql"""UPDATE jobs SET budget_usd = coalesce($usd, budget_usd), budget_tokens = coalesce($tokens, budget_tokens),
                          updated_at = now() WHERE id = $id AND kind = $kind""".update.run()

  /** The `running` runs (but `except`) moved to `paused` with `error`, returned with their origin; then the `queued`
    * ones (but `except`), oldest first.
    */
  def runsReconciled(
      kind: Job.Kind,
      running: RunRow.Status,
      paused: RunRow.Status,
      error: Problem,
      queued: RunRow.Status,
      except: List[Run.Id]
  )(using DbCon): (Vector[(Run.Id, Option[RunRow.Origin])], Vector[Run.Id]) =
    val kept = NonEmptyVector.of(error)
    val moved = sql"""UPDATE jobs SET status = $paused, error = $kept, updated_at = now()
                      WHERE kind = $kind AND status = $running AND NOT (id = ANY($except::text[]))
                      RETURNING id, origin""".query[(Run.Id, Option[RunRow.Origin])].run()
    val waiting = sql"""SELECT id FROM jobs WHERE kind = $kind AND status = $queued
                          AND NOT (id = ANY($except::text[]))
                        ORDER BY created_at, id""".query[Run.Id].run()
    (moved, waiting)

  /** A `paused` run's resume time (unless it moved on meanwhile). */
  def runResumesAt(kind: Job.Kind, id: Run.Id, paused: RunRow.Status, at: ResumeAt)(using DbCon): Boolean =
    sql"""UPDATE jobs SET resume_at = $at::timestamptz, updated_at = now()
          WHERE id = $id AND kind = $kind AND status = $paused""".update.run() > 0

  // ── merge apply (#888) ─────────────────────────────────────────────────────────────────────────

  /** Start a merge apply: a `running` row at once (it runs on its own thread, never queued), its bundle in `sha256` and
    * its plan in `detail`. A restart fails it as `interrupted` like every other unfinished job ([[failInterrupted]]),
    * so a poll never sees it running forever.
    */
  def startMergeApply(bundle: MergeRepo.BundleName, planHash: MergeRow.PlanHash)(using DbCon): Job.Id =
    val id = java.util.UUID.randomUUID().toString
    val js = json(ExchangeJobKinds.PlanHash(Some(MergePlan.PlanHash(planHash.value))))
    sql"""INSERT INTO jobs (id, kind, status, sha256, detail)
          VALUES ($id, ${ExchangeJobKinds.MergeApply.kind}, ${JobState.Running.wire}, $bundle, $js::jsonb)""".update
      .run()
    Job.Id.assume(id)

  /** Record how a merge apply ended: `done` with its report (applied or refused), or `failed` with why. A row a restart
    * already failed is left as it is.
    */
  def finishMergeApply(id: Job.Id, outcome: Either[Problems, MergeApplyResult])(using DbCon): Unit =
    val (status, error, result) = outcome match
      case Right(r) => (JobState.Done, None, Some(Jsonb.write(r)))
      case Left(e)  => (JobState.Failed, Some(e.toNonEmpty), None)
    val _ =
      sql"""UPDATE jobs SET status = ${status.wire}, error = $error, result = $result::jsonb, updated_at = now()
            WHERE id = $id AND kind = ${ExchangeJobKinds.MergeApply.kind} AND status = ${JobState.Running.wire}""".update
        .run()

  /** One merge apply as the API answers it; `None` for an unknown id (or another kind of job). The time so far of a
    * running one is measured against the database's clock.
    */
  def mergeApply(id: Job.Id)(using DbCon): Option[MergeApplyJob] =
    sql"""SELECT id, status, coalesce(sha256, ''), coalesce(detail->>'planHash', ''),
                 (extract(epoch FROM (CASE WHEN status = ${JobState.Running.wire} THEN clock_timestamp()
                                          ELSE updated_at END) - created_at) * 1000)::bigint,
                 error, result::text
          FROM jobs WHERE id = $id AND kind = ${ExchangeJobKinds.MergeApply.kind}"""
      .query[(String, String, String, String, Long, Option[NonEmptyVector[Problem]], Option[String])]
      .run()
      .headOption
      .map { (id, status, bundle, plan, ms, error, result) =>
        MergeApplyJob.of(
          SpatialHierarchyResult.Job.Id(id),
          JobState.parse(JobState.Wire(status)).getOrElse(JobState.Failed),
          MergeApplyJob.OfBundle(bundle),
          MergeApplyJob.OfPlanHash(plan),
          MergeApplyJob.OfElapsedMs(math.max(0L, ms)),
          error.map(problems =>
            NonEmptyVector.toVector(problems) match
              case Vector(Problem.Run.Interrupted) => MergeApplyJob.Failure.Interrupted
              case _                               => MergeApplyJob.Failure.Other(problems)
          ),
          result.flatMap(r => Jsonb.read[MergeApplyResult](Jsonb.JsonText(r)))
        )
      }

  /** Is a merge apply running? One at a time. */
  def mergeApplyRunning(using DbCon): Boolean =
    sql"SELECT 1 FROM jobs WHERE kind = ${ExchangeJobKinds.MergeApply.kind} AND status = ${JobState.Running.wire} LIMIT 1"
      .query[Int]
      .run()
      .nonEmpty
