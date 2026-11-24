package com.histmersion
package domain.personal.seen

import com.histmersion.domain.personal.MarkTime
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Timestamps

import com.augustnagro.magnum.*

import java.time.Instant
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The `ui_seen` table: one mark per board. Takes the connection it runs on. */
@atBoundary(BoundaryKind.Sql, "reads and writes the seen tables and their row codecs")
private[seen] object SeenRepo:

  def all(using DbCon): Map[SeenBoard, MarkTime] =
    sql"SELECT board, ${Timestamps.iso(sql"seen_at")} FROM ui_seen"
      .query[(SeenBoard, MarkTime.Iso)]
      .run()
      .map((b, at) => b -> MarkTime.parse(at))
      .toMap

  /** The mark moved forward to `at` (the database's now when none); the mark kept. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def mark(board: SeenBoard, at: Option[Instant])(using DbCon): MarkTime =
    val asked = at.map(_.toString)
    MarkTime.parse(
      sql"""INSERT INTO ui_seen (board, seen_at) VALUES ($board, coalesce($asked::timestamptz, now()))
            ON CONFLICT (board) DO UPDATE SET seen_at = greatest(ui_seen.seen_at, EXCLUDED.seen_at)
            RETURNING ${Timestamps.iso(sql"seen_at")}""".query[MarkTime.Iso].run().head
    )
