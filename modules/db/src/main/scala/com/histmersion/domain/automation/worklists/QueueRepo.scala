package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.Queue.*
import com.histmersion.domain.Scheduling

import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal
import com.histmersion.domain.library.Doi

/** The store of the work lists (`queue_items`, migration `V009__queue.sql`): one table, every list in it by its `list`.
  * Every method runs on the connection it is given; the service ([[QueueService]], in the server) or the import that
  * writes a list's rows in its own transaction opens it.
  *
  * All the shape lives in the pure [[com.histmersion.api.automation.Queue]] parser and renderer. Timestamps go out as
  * the same UTC ISO-8601 strings the rest of the wire uses.
  */
private[worklists] object QueueRepo:
  type QueueItemId = QueueItemId.T
  object QueueItemId extends Newtype[Int, Pure]
  type JobText = JobText.T
  object JobText extends Newtype[String, Pure]
  type GoalText = GoalText.T
  object GoalText extends Newtype[String, Pure]

  /** Column order is fixed once here and reused by every projection (AGENTS.md: no duplicated column lists). `to_char`
    * normalises to UTC so the value is stable whatever the session TZ is.
    */

  private val Cols = sql"""id, list, text, topic, status, reason,
       to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
       to_char(done_at    AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
       goal_slug, created_job, closed_job"""

  private given DbCodec[QueueItem] = DbCodec.derived

  /** `open` (and, in a checkbox list, `done`) items — never `note` blocks, which are export-only scaffolding. `all`
    * includes everything, in id order, which is what the export renders.
    */
  def list(kind: Kind, all: Boolean)(using DbCon): Vector[QueueItem] =
    if all then
      sql"""SELECT $Cols FROM queue_items WHERE list = ${kind.label} ORDER BY id"""
        .query[QueueItem]
        .run()
    else sql"""SELECT $Cols FROM queue_items
              WHERE list = ${kind.label} AND status = $Open ORDER BY id""".query[QueueItem].run()

  /** The open (with `all`, every) items of `kind` that serve goal `goal`: their own goal, or one of `linked` (the
    * references of the goal's `queue-item` links, compared with the id as text).
    */
  def forGoal(kind: Kind, goal: GoalText, linked: List[Goal.Plans.GoalLink.Ref], all: Boolean)(using
      DbCon
  ): Vector[QueueItem] =
    sql"""SELECT $Cols FROM queue_items
            WHERE list = ${kind.label} AND status <> $Note AND ($all OR status = $Open)
              AND (goal_slug = $goal OR id::text = ANY($linked::text[]))
            ORDER BY id""".query[QueueItem].run()

  /** The items of `kind` (any status but notes) that agent run or background job `job` wrote or ticked off. */
  def forJob(kind: Kind, job: JobText)(using DbCon): Vector[QueueItem] =
    sql"""SELECT $Cols FROM queue_items
            WHERE list = ${kind.label} AND status <> $Note AND (created_job = $job OR closed_job = $job)
            ORDER BY id""".query[QueueItem].run()

  def get(id: QueueItemId)(using DbCon): Option[QueueItem] =
    sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().headOption

  /** The items with these ids (any list, any status), by id; ids with no row are simply absent. */
  def byIds(ids: List[QueueItemId])(using DbCon): Vector[QueueItem] =
    if ids.isEmpty then Vector.empty
    else
      // a text[] crosses as a real array (db/Codecs.scala); the ids are compared as text there
      val refs = ids.distinct.map(_.toString)
      sql"SELECT $Cols FROM queue_items WHERE id::text = ANY($refs::text[]) ORDER BY id".query[QueueItem].run()

  /** The items of `kind` (any status) whose text names `doi`, by id — the lookup a sweep's dedupe and the ingest
    * backlog make right before adding one to the acquisition list (a sweep's snapshot of the list can be minutes old,
    * and another watcher's sweep may have added it since).
    */
  def naming(kind: Kind, doi: Doi)(using DbCon): Vector[QueueItem] =
    val d = doi.value.trim.toLowerCase
    if d.isEmpty then Vector.empty
    else
      sql"""SELECT $Cols FROM queue_items WHERE list = ${kind.label} AND strpos(lower(text), $d) > 0
                        ORDER BY id"""
        .query[QueueItem]
        .run()
        .filter(i => Scheduling.dois(i.text.value).exists(_.value == d))

  /** Insert one row. `createdAt` (an ISO instant) is honoured when given — an imported LOG entry keeps the date from
    * its heading, which is what orders the exported file.
    */
  def insert(kind: Kind, in: QueueAdd)(using DbCon): QueueItem =
    val status = in.status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Open)
    val goal = in.goal.map(_.value.trim).filter(_.nonEmpty)
    val job = in.job.map(_.value.trim).filter(_.nonEmpty)
    val id =
      sql"""INSERT INTO queue_items (list, text, topic, status, reason, created_at, done_at, goal_slug, created_job)
                   VALUES (${kind.label}, ${in.text}, ${in.topic}, $status, ${in.reason},
                           coalesce(${in.createdAt}::timestamptz, now()),
                           CASE WHEN $status = $Done THEN now() END, $goal, $job)
                   RETURNING id""".query[Int].run().head
    sql"SELECT $Cols FROM queue_items WHERE id = $id".query[QueueItem].run().head

  /** Write `next` over its row. `status = 'done'` keeps the first `done_at` (stamped now when there is none); anything
    * else clears it, so reopening an item leaves no misleading completion date behind.
    */
  def update(next: QueueItem)(using DbCon): Unit =
    val _ = sql"""UPDATE queue_items
                SET text = ${next.text}, topic = ${next.topic}, status = ${next.status}, reason = ${next.reason},
                    goal_slug = ${next.goal}, closed_job = ${next.closedBy},
                    done_at = CASE WHEN ${next.status} = $Done THEN coalesce(done_at, now()) END
                WHERE id = ${next.id}""".update.run()

  def delete(id: QueueItemId)(using DbCon): Unit =
    val _ = sql"DELETE FROM queue_items WHERE id = $id".update.run()

  /** Every row of `kind` removed; how many. */
  def deleteList(kind: Kind)(using DbCon): RowCount =
    RowCount(sql"DELETE FROM queue_items WHERE list = ${kind.label}".update.run())
