package com.histmersion
package domain.personal.reading

import com.histmersion.domain.{ItemKey, ItemKind, ItemRef, ItemUid}
import com.histmersion.domain.personal.{MarkTime, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Timestamps
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The `read_marks` table. Takes the connection it runs on. */
@atBoundary(BoundaryKind.Sql, "reads and writes the reading tables and their row codecs")
private[reading] object ReadingRepo:

  private val Projection =
    sql"""SELECT m.target_kind, m.target_id, m.target_uid, m.target_label, ${Timestamps.iso(
        sql"m.read_at"
      )}, ${Timestamps.iso(sql"m.opened_at")},
               ${Timestamps.iso(sql"m.created_at")}, ${Timestamps.iso(sql"m.updated_at")}
          FROM read_marks m"""

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, size: PageSize)(using DbCon): List[MarkRow] =
    val (where, order) = show match
      case ReadingShow.Read   => (sql" AND m.read_at IS NOT NULL", sql" ORDER BY m.read_at DESC, m.id DESC")
      case ReadingShow.Opened => (sql" AND m.opened_at IS NOT NULL", sql" ORDER BY m.opened_at DESC, m.id DESC")
      case ReadingShow.All    => (sql"", sql" ORDER BY m.updated_at DESC, m.id DESC")
    sql"$Projection WHERE m.user_id = $user${kind.fold(sql"")(k => sql" AND m.target_kind = $k")}$where$order LIMIT $size"
      .query[MarkRow]
      .run()
      .toList

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def get(user: UserId, ref: ItemRef)(using DbCon): Option[MarkRow] =
    sql"$Projection WHERE m.user_id = $user AND m.target_kind = ${ref.kind} AND m.target_id = ${ref.key}"
      .query[MarkRow]
      .run()
      .headOption

  /** The records of `kind` the person marked read. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def read(user: UserId, kind: ItemKind)(using DbCon): List[ItemRef] =
    sql"""SELECT target_kind, target_id, target_uid FROM read_marks
          WHERE user_id = $user AND target_kind = $kind AND read_at IS NOT NULL"""
      .query[(ItemKind, ItemKey, Option[ItemUid])]
      .run()
      .map(ItemRef.apply)
      .toList

  /** Mark read (keeping when it was first marked) or not read yet. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def setRead(user: UserId, ref: ItemRef, targetLabel: Option[TargetLabel], read: ReadingService.Read, at: MarkTime)(
      using DbCon
  ): Unit =
    val stamp = at.iso
    val readAt = Option.when(read.value)(stamp)
    sql"""INSERT INTO read_marks (user_id, target_kind, target_id, target_uid, target_label, read_at, created_at, updated_at)
          VALUES ($user, ${ref.kind}, ${ref.key}, ${ref.uid}, $targetLabel, $readAt::timestamptz,
                  $stamp::timestamptz, $stamp::timestamptz)
          ON CONFLICT (user_id, target_kind, target_id) DO UPDATE SET
            read_at = CASE WHEN EXCLUDED.read_at IS NULL THEN NULL ELSE coalesce(read_marks.read_at, EXCLUDED.read_at) END,
            target_uid = coalesce(EXCLUDED.target_uid, read_marks.target_uid),
            target_label = coalesce(EXCLUDED.target_label, read_marks.target_label),
            updated_at = EXCLUDED.updated_at""".update.run(): Unit

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def setOpened(user: UserId, ref: ItemRef, targetLabel: Option[TargetLabel], at: MarkTime)(using DbCon): Unit =
    val stamp = at.iso
    sql"""INSERT INTO read_marks (user_id, target_kind, target_id, target_uid, target_label, opened_at, created_at, updated_at)
          VALUES ($user, ${ref.kind}, ${ref.key}, ${ref.uid}, $targetLabel, $stamp::timestamptz,
                  $stamp::timestamptz, $stamp::timestamptz)
          ON CONFLICT (user_id, target_kind, target_id) DO UPDATE SET
            opened_at = EXCLUDED.opened_at,
            target_uid = coalesce(EXCLUDED.target_uid, read_marks.target_uid),
            target_label = coalesce(EXCLUDED.target_label, read_marks.target_label),
            updated_at = EXCLUDED.updated_at""".update.run(): Unit
