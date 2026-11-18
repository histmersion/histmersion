package com.histmersion
package domain.personal.notes

import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.personal.{MarkTime, TargetColumns, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*

import scala.util.chaining.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.given

/** The `notes` table. Takes the connection it runs on; a removed note stays as its tombstone. */
private[notes] object NoteRepo:
  type RowsChanged = RowsChanged.T
  object RowsChanged extends Newtype[Int, Pure]

  private def iso(col: String): String = s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  private val Projection =
    s"""SELECT n.id::text, n.target_kind, n.target_id, n.target_uid, n.lat, n.lon, n.period_start, n.period_end, n.body,
               n.target_label, ${iso("n.created_at")}, ${iso("n.updated_at")}
          FROM notes n"""

  /** Last changed first; every one when no `size` is given. */
  def list(user: UserId, kind: Option[MarkKind], item: Option[Note.Item], size: Option[PageSize])(using
      DbCon
  ): List[Note.Row] =
    sql"${SqlLiteral(Projection)} WHERE n.user_id = $user AND n.deleted_at IS NULL${kind.fold(
        sql""
      )(k => sql" AND n.target_kind = ${k.wire}")}${item.fold(sql"")(i =>
        sql" AND (n.target_id = ${i.key} OR n.target_uid = ANY(${i.uids.toList.map(u => u.value.toString)}::uuid[]))"
      )} ORDER BY n.updated_at DESC, n.id DESC${size.fold(sql"")(s => sql" LIMIT $s")}"
      .query[Note.Row]
      .run()
      .toList

  def live(user: UserId, id: com.histmersion.api.personal.Note.Id)(using DbCon): Option[Note.Row] =
    sql"${SqlLiteral(Projection)} WHERE n.user_id = $user AND n.deleted_at IS NULL AND n.id = $id::uuid"
      .query[Note.Row]
      .run()
      .headOption

  def insert(
      user: UserId,
      c: TargetColumns,
      text: com.histmersion.api.personal.Note.Text,
      targetLabel: Option[TargetLabel],
      at: MarkTime
  )(using
      DbCon
  ): com.histmersion.api.personal.Note.Id =
    val stamp = at.iso
    sql"""INSERT INTO notes (user_id, target_kind, target_id, target_uid, target_label, lat, lon, period_start,
                             period_end, body, created_at, updated_at)
          VALUES ($user, ${c.kind}, ${c.key}, ${c.uid}, $targetLabel, ${c.lat}, ${c.lon}, ${c.from},
                  ${c.to}, $text, $stamp::timestamptz, $stamp::timestamptz)
          RETURNING id::text""".query[com.histmersion.api.personal.Note.Id].run().head

  def rewrite(
      user: UserId,
      id: com.histmersion.api.personal.Note.Id,
      text: com.histmersion.api.personal.Note.Text,
      at: MarkTime
  )(using DbCon): RowsChanged =
    val stamp = at.iso
    sql"""UPDATE notes SET body = $text, updated_at = $stamp::timestamptz
          WHERE user_id = $user AND id = $id::uuid AND deleted_at IS NULL""".update
      .run()
      .pipe(RowsChanged(_))

  def tombstone(user: UserId, id: com.histmersion.api.personal.Note.Id, at: MarkTime)(using DbCon): RowsChanged =
    val stamp = at.iso
    sql"""UPDATE notes SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
          WHERE user_id = $user AND id = $id::uuid AND deleted_at IS NULL""".update
      .run()
      .pipe(RowsChanged(_))
