package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** Flat projection of `jobs` (positional, like the other write-path rows). */
private[db] final case class JobRow(
    id: String, kind: String, status: String, citekey: Option[String], progress: Option[String],
    error: Option[String], sha256: Option[String], createdAt: String, updatedAt: String,
    acquireItem: Option[Int], detail: Option[String]
)

given DbCodec[JobRow] = DbCodec.derived

/** OCR jobs (plan §1.8). The row is the durable part of a job; the queue itself is in memory, which
  * is precisely why a restart must reconcile: [[failInterrupted]] turns every `running` row into
  * `failed: interrupted` at startup, so no job is left claiming to be in progress by a process that
  * no longer exists. Re-running is idempotent — the same file has the same sha256. */
object JobRepo:

  /** ONE projection, reused by both reads (AGENTS.md: no duplicated column lists). Magnum's `sql""`
    * cannot splice a `Frag`, so composition goes through the module's own [[Sql]] fragment type. */
  private val select = Sql.raw(
    """SELECT id, kind, status, citekey, progress, error, sha256,
              to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              acquire_item, detail::text
       FROM jobs""")

  /** A stored detail this build cannot read (written by a newer one) is left out, not fatal. */
  private def toInfo(r: JobRow): JobInfo =
    JobInfo(r.id, r.kind, r.status, r.citekey, r.progress, r.error, r.sha256, r.createdAt, r.updatedAt,
      r.acquireItem, r.detail.flatMap(io.circe.parser.decode[JobDetail](_).toOption))

  private def json(d: JobDetail): String =
    import io.circe.syntax.*
    d.asJson.noSpaces

  /** Create a `queued` job. The id is opaque to clients; a UUID keeps it unguessable and unique.
    * `acquireItem` is the acquisition item the job ticks off when it succeeds; `detail` what the job
    * is about beyond its citekey. */
  def create(ds: DataSource, kind: String, citekey: Option[String], sha256: Option[String],
      acquireItem: Option[Int] = None, detail: Option[JobDetail] = None): JobInfo =
    val id = java.util.UUID.randomUUID().toString
    val js = detail.map(json)
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, citekey, status, sha256, acquire_item, detail)
            VALUES ($id, $kind, $citekey, ${JobState.Queued.wire}, $sha256, $acquireItem, ${js}::jsonb)""".update.run()
    get(ds, id).getOrElse(JobInfo(id, kind, JobState.Queued.wire, citekey, None, None, sha256,
      acquireItem = acquireItem, detail = detail))

  /** Replace a job's [[JobDetail]] — e.g. an init job adding the periods it skipped. */
  def setDetail(ds: DataSource, id: String, detail: JobDetail): Unit =
    val js = json(detail)
    val _ = transact(ds)(sql"UPDATE jobs SET detail = ${js}::jsonb WHERE id = $id".update.run())

  def get(ds: DataSource, id: String): Option[JobInfo] =
    connect(ds):
      (select ++ Sql.of(" WHERE id = ?", Param.S(id))).frag.query[JobRow].run().headOption.map(toInfo)

  /** Newest first; `status` (when given) narrows to one state. */
  def list(ds: DataSource, status: Option[String], limit: Int): Vector[JobInfo] =
    val where = status.map(_.trim.toLowerCase).filter(_.nonEmpty) match
      case Some(s) => Sql.of(" WHERE status = ?", Param.S(s))
      case None    => Sql.empty
    val q = select ++ where ++ Sql.of(" ORDER BY created_at DESC, id LIMIT ?", Param.I(math.max(1, limit)))
    connect(ds)(q.frag.query[JobRow].run().map(toInfo))

  /** Free-text progress on a `running` job — what the CLI's progress line prints. */
  def progress(ds: DataSource, id: String, text: String): Unit =
    val _ = transact(ds):
      sql"UPDATE jobs SET progress = $text, updated_at = now() WHERE id = $id AND status = ${JobState.Running.wire}"
        .update.run()

  /** Apply a [[JobState]] transition, guarded BY THE STORE as well as by the state machine: the
    * `status = <from>` predicate makes the update a compare-and-set, so two workers cannot both
    * claim the same job. Returns the new state, or `None` when the transition was refused. */
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

  /** Startup reconciliation: nothing survives the process, so anything still `running` failed with
    * it. Returns how many rows were corrected. */
  def failInterrupted(ds: DataSource): Int =
    transact(ds):
      sql"""UPDATE jobs SET status = ${JobState.Failed.wire}, error = ${JobState.Interrupted},
                            progress = NULL, updated_at = now()
            WHERE status IN (${JobState.Running.wire}, ${JobState.Queued.wire})""".update.run()
