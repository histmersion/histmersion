package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Queue.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The store behind `histmersion queue` (migration `V009__queue.sql`, plan §5.2 stream 2.8).
  *
  * Deliberately thin: all the shape lives in the pure [[com.histmersion.api.Queue]] parser/renderer, and this only does
  * CRUD plus the two bulk paths (`replaceAll` for an import, `all` for an export). Timestamps go out as the same UTC
  * ISO-8601 strings the rest of the wire uses.
  */
object QueueRepo:

  /** Column order is fixed once here and reused by every projection (AGENTS.md: no duplicated column lists). `to_char`
    * normalises to UTC so the value is stable whatever the session TZ is.
    */
  private val Cols = SqlLiteral("""id, list, text, topic, status, reason,
       to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
       to_char(done_at    AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
       goal_slug, created_job, closed_job""")

  private given DbCodec[QueueItem] = DbCodec.derived

  /** `open` (and, in a checkbox list, `done`) items — never `note` blocks, which are export-only scaffolding. `all`
    * includes everything, in id order, which is what the export renders.
    */
  def list(ds: DataSource, kind: Kind, all: Boolean): Vector[QueueItem] =
    connect(ds):
      if all then
        sql"""SELECT $Cols FROM queue_items WHERE list = ${kind.label} ORDER BY id"""
          .query[QueueItem]
          .run()
      else sql"""SELECT $Cols FROM queue_items
              WHERE list = ${kind.label} AND status = $Open ORDER BY id""".query[QueueItem].run()

  /** The open (with `all`, every) items of `kind` that serve research goal `goal`: their own goal, or a goal link of
    * kind `queue-item` naming them.
    */
  def forGoal(ds: DataSource, kind: Kind, goal: String, all: Boolean): Vector[QueueItem] =
    connect(ds):
      sql"""SELECT $Cols FROM queue_items
            WHERE list = ${kind.label} AND status <> $Note AND ($all OR status = $Open)
              AND (goal_slug = $goal OR id::text IN (SELECT ref FROM goal_links WHERE goal_slug = $goal AND kind = 'queue-item'))
            ORDER BY id""".query[QueueItem].run()

  /** The items of `kind` (any status but notes) that agent run or background job `job` wrote or ticked off. */
  def forJob(ds: DataSource, kind: Kind, job: String): Vector[QueueItem] =
    connect(ds):
      sql"""SELECT $Cols FROM queue_items
            WHERE list = ${kind.label} AND status <> $Note AND (created_job = $job OR closed_job = $job)
            ORDER BY id""".query[QueueItem].run()

  def get(ds: DataSource, id: Int): Option[QueueItem] =
    connect(ds)(sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().headOption)

  /** The items with these ids (any list, any status), by id; ids with no row are simply absent. */
  def byIds(ds: DataSource, ids: List[Int]): Vector[QueueItem] =
    if ids.isEmpty then Vector.empty
    else
      // a text[] crosses as a real array (db/Codecs.scala); the ids are compared as text there
      val refs = ids.distinct.map(_.toString)
      connect(ds)(
        sql"SELECT $Cols FROM queue_items WHERE id::text = ANY($refs::text[]) ORDER BY id".query[QueueItem].run()
      )

  /** The acquisition items (any status) whose text names `doi`, by id — the lookup a sweep's dedupe and the ingest
    * backlog make right before adding one (a sweep's snapshot of the list can be minutes old, and another watcher's
    * sweep may have added it since).
    */
  def acquireNaming(ds: DataSource, doi: String): Vector[QueueItem] =
    val d = doi.trim.toLowerCase
    if d.isEmpty then Vector.empty
    else
      connect(ds)(sql"""SELECT $Cols FROM queue_items WHERE list = ${Kind.Acquire.label} AND strpos(lower(text), $d) > 0
                        ORDER BY id""".query[QueueItem].run())
        .filter(i => Scheduling.dois(i.text).contains(d))

  /** Insert one row. `createdAt` (an ISO instant) is honoured when given — an imported LOG entry keeps the date from
    * its heading, which is what orders the exported file.
    */
  def add(ds: DataSource, kind: Kind, in: QueueAdd): QueueItem =
    transact(ds)(insert(kind, in))

  private[db] def insert(kind: Kind, in: QueueAdd)(using DbCon): QueueItem =
    val status = in.status.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Open)
    val goal = in.goal.map(_.trim).filter(_.nonEmpty)
    val job = in.job.map(_.trim).filter(_.nonEmpty)
    val id =
      sql"""INSERT INTO queue_items (list, text, topic, status, reason, created_at, done_at, goal_slug, created_job)
                   VALUES (${kind.label}, ${in.text}, ${in.topic}, $status, ${in.reason},
                           coalesce(${in.createdAt}::timestamptz, now()),
                           CASE WHEN $status = $Done THEN now() END, $goal, $job)
                   RETURNING id""".query[Int].run().head
    sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().head

  /** Only the parts present are changed. `status = 'done'` stamps `done_at`; anything else clears it, so reopening an
    * item leaves no misleading completion date behind.
    */
  def patch(ds: DataSource, id: Int, p: QueuePatch): Either[ApiError, QueueItem] =
    transact(ds):
      sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().headOption match
        case None      => Left(ApiError(s"No queue item #$id", Some(Write.CodeNotFound)))
        case Some(cur) =>
          val next = Queue.applyPatch(cur, p)
          sql"""UPDATE queue_items
                SET text = ${next.text}, topic = ${next.topic}, status = ${next.status}, reason = ${next.reason},
                    goal_slug = ${next.goal}, closed_job = ${next.closedBy},
                    done_at = CASE WHEN ${next.status} = $Done THEN coalesce(done_at, now()) END
                WHERE id = $id""".update.run()
          Right(sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().head)

  def delete(ds: DataSource, id: Int): Either[ApiError, QueueItem] =
    transact(ds):
      sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().headOption match
        case None       => Left(ApiError(s"No queue item #$id", Some(Write.CodeNotFound)))
        case Some(item) =>
          sql"DELETE FROM queue_items WHERE id = $id".update.run()
          Right(item)

  /** Replace a whole list in ONE transaction (`queue import`). Emptying first is what makes importing the same file
    * twice idempotent; doing it in the same transaction means a failed parse or a bad row never leaves the list
    * half-erased. Returns (rows written, rows removed).
    */
  def replaceAll(ds: DataSource, kind: Kind, rows: List[QueueAdd], replace: Boolean): (Int, Int) =
    transact(ds):
      val removed =
        if !replace then 0
        else sql"DELETE FROM queue_items WHERE list = ${kind.label}".update.run()
      rows.foreach { r =>
        val _ = insert(kind, r)
      }
      (rows.size, removed)
