package com.histmersion
package domain.automation.worklists

import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.automation.queue.QueueAdd

import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.*
import com.histmersion.domain.{CreatedAt, Creator, Scheduling}

import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.util.database.{Jsonb, StoredCreator}
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal
import com.histmersion.domain.library.Doi
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The store of the work lists (`queue_items`, migration `V009__queue.sql`): one table, every list in it by its `list`.
  * Every method runs on the connection it is given; the service ([[QueueService]], in the server) or the import that
  * writes a list's rows in its own transaction opens it.
  *
  * All the shape lives in the pure [[com.histmersion.api.automation.Queue]] parser and renderer. Timestamps go out as
  * the same UTC ISO-8601 strings the rest of the wire uses.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the queue tables and their row codecs")
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
       goal_slug, created_job, closed_job, creator::text, actor"""

  final case class Row(
      id: QueueItem.Id,
      list: QueueItem.List,
      text: QueueItem.Text,
      topic: Option[QueueItem.Topic],
      status: QueueItem.Status,
      reason: Option[Rationale],
      createdAt: Option[QueueItem.CreatedAt],
      doneAt: Option[QueueItem.DoneAt],
      goal: Option[Goal.Slug],
      createdBy: Option[QueueItem.CreatedBy],
      closedBy: Option[QueueItem.ClosedBy],
      creator: Jsonb.JsonText,
      actor: Option[Creation.Actor]
  ) derives DbCodec:
    def item: QueueItem =
      QueueItem(
        id,
        list,
        text,
        topic,
        status,
        reason,
        createdAt,
        doneAt,
        goal,
        createdBy,
        closedBy,
        Some(Jsonb.read[Creation](creator).getOrElse(Creation(Creation.Unreadable))),
        actor
      )

  private def items(rows: Vector[Row]): Vector[QueueItem] = rows.map(_.item)

  /** `open` (and, in a checkbox list, `done`) items — never `note` blocks, which are export-only scaffolding. `all`
    * includes everything, in id order, which is what the export renders.
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def list(kind: Kind, all: Boolean)(using DbCon): Vector[QueueItem] =
    if all then items(sql"""SELECT $Cols FROM queue_items WHERE list = ${kind.label} ORDER BY id""".query[Row].run())
    else items(sql"""SELECT $Cols FROM queue_items
              WHERE list = ${kind.label} AND status = $Open ORDER BY id""".query[Row].run())

  /** The open (with `all`, every) items of `kind` that serve goal `goal`: their own goal, or one of `linked` (the
    * references of the goal's `queue-item` links, compared with the id as text).
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def forGoal(kind: Kind, goal: GoalText, linked: List[Goal.Plans.GoalLink.Ref], all: Boolean)(using
      DbCon
  ): Vector[QueueItem] =
    items(sql"""SELECT $Cols FROM queue_items
            WHERE list = ${kind.label} AND status <> $Note AND ($all OR status = $Open)
              AND (goal_slug = $goal OR id::text = ANY($linked::text[]))
            ORDER BY id""".query[Row].run())

  /** The items of `kind` (any status but notes) that agent run or background job `job` wrote or ticked off. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def forJob(kind: Kind, job: JobText)(using DbCon): Vector[QueueItem] =
    items(sql"""SELECT $Cols FROM queue_items
            WHERE list = ${kind.label} AND status <> $Note AND (created_job = $job OR closed_job = $job)
            ORDER BY id""".query[Row].run())

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(id: QueueItemId)(using DbCon): Option[QueueItem] =
    items(sql"SELECT $Cols FROM queue_items WHERE id = $id".query[Row].run()).headOption

  /** The items with these ids (any list, any status), by id; ids with no row are simply absent. */
  def byIds(ids: List[QueueItemId])(using DbCon): Vector[QueueItem] =
    if ids.isEmpty then Vector.empty
    else
      // a text[] crosses as a real array (db/Codecs.scala); the ids are compared as text there
      val refs = ids.distinct.map(_.toString)
      items(sql"SELECT $Cols FROM queue_items WHERE id::text = ANY($refs::text[]) ORDER BY id".query[Row].run())

  /** The items of `kind` (any status) whose text names `doi`, by id — the lookup a sweep's dedupe and the ingest
    * backlog make right before adding one to the acquisition list (a sweep's snapshot of the list can be minutes old,
    * and another watcher's sweep may have added it since).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def naming(kind: Kind, doi: Doi)(using DbCon): Vector[QueueItem] =
    val d = doi.value.trim.toLowerCase
    if d.isEmpty then Vector.empty
    else
      items(sql"""SELECT $Cols FROM queue_items WHERE list = ${kind.label} AND strpos(lower(text), $d) > 0
                        ORDER BY id""".query[Row].run())
        .filter(i => Scheduling.dois(i.text.value).exists(_.value == d))

  /** Insert one row. `createdAt` (an ISO instant) is honoured when given — an imported LOG entry keeps the date from
    * its heading, which is what orders the exported file.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def insert(kind: Kind, in: QueueAdd, creator: Creator.New, at: CreatedAt)(using DbCon): QueueItem =
    val status = in.status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Open)
    val goal = in.goal.map(_.value.trim).filter(_.nonEmpty)
    val job = in.job.map(_.value.trim).filter(_.nonEmpty)
    val stored = StoredCreator.write(creator.stored)
    val id =
      sql"""INSERT INTO queue_items (list, text, topic, status, reason, created_at, done_at, goal_slug, created_job,
                                     creator, actor)
                   VALUES (${kind.label}, ${in.text}, ${in.topic}, $status, ${in.reason},
                           coalesce(${in.createdAt}::timestamptz, $at::timestamptz),
                           CASE WHEN $status = $Done THEN now() END, $goal, $job,
                           $stored::jsonb, ${StoredCreator.actor(creator)})
                   RETURNING id""".query[Int].run().head
    items(sql"SELECT $Cols FROM queue_items WHERE id = $id".query[Row].run()).head

  /** Write `next` over its row. `status = 'done'` keeps the first `done_at` (stamped now when there is none); anything
    * else clears it, so reopening an item leaves no misleading completion date behind. False when the row is gone
    * (removed since it was read).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def update(next: QueueItem)(using DbCon): Boolean =
    sql"""UPDATE queue_items
          SET text = ${next.text}, topic = ${next.topic}, status = ${next.status}, reason = ${next.reason},
              goal_slug = ${next.goal}, closed_job = ${next.closedBy}, actor = coalesce(${next.actor}, actor),
              done_at = CASE WHEN ${next.status} = $Done THEN coalesce(done_at, now()) END
          WHERE id = ${next.id}""".update.run() > 0

  /** False when the row is gone (removed since it was read). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def delete(id: QueueItemId)(using DbCon): Boolean =
    sql"DELETE FROM queue_items WHERE id = $id".update.run() > 0

  /** Every row of `kind` removed; how many. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def deleteList(kind: Kind)(using DbCon): RowCount =
    RowCount(sql"DELETE FROM queue_items WHERE list = ${kind.label}".update.run())
