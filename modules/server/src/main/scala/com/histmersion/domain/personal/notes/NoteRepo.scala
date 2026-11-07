package com.histmersion.domain.personal.notes

import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.personal.{MarkTime, TargetColumns, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*

import scala.util.chaining.*

/** The `notes` table. Takes the connection it runs on; a removed note stays as its tombstone. */
private[notes] object NoteRepo:
  type RowsChanged = RowsChanged.T
  object RowsChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private def iso(col: String): String = s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  private val Projection =
    s"""SELECT n.id::text, n.target_kind, n.target_id, n.target_uid, n.lat, n.lon, n.period_start, n.period_end, n.body,
               n.target_label, ${iso("n.created_at")}, ${iso("n.updated_at")}
          FROM notes n"""

  /** Last changed first; every one when no `size` is given. */
  def list(user: UserId, kind: Option[MarkKind], item: Option[Note.Item], size: Option[PageSize])(using
      DbCon
  ): List[Note.Row] =
    (Sql.of(Sql.Text(s"$Projection WHERE n.user_id = ? AND n.deleted_at IS NULL"), Param.S(Param.S.V(user.value))) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND n.target_kind = ?", Param.S(Param.S.V(k.wire.value)))) ++
      item.fold(Sql.empty)(i =>
        Sql.of(
          " AND (n.target_id = ? OR n.target_uid = ANY(?::uuid[]))",
          Param.S(Param.S.V(i.key.value)),
          Param.A(i.uids.toList.map(u => Param.A.V(u.value.toString)))
        )
      ) ++
      Sql.raw(" ORDER BY n.updated_at DESC, n.id DESC") ++
      size.fold(Sql.empty)(s => Sql.of(" LIMIT ?", Param.I(Param.I.V(s.value))))).frag.query[Note.Row].run().toList

  def live(user: UserId, id: com.histmersion.api.personal.Note.Id)(using DbCon): Option[Note.Row] =
    Sql
      .of(
        Sql.Text(s"$Projection WHERE n.user_id = ? AND n.deleted_at IS NULL AND n.id = ?::uuid"),
        Param.S(Param.S.V(user.value)),
        Param.S(Param.S.V(id.value))
      )
      .frag
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
          VALUES (${user.value}, ${c.kind}, ${c.key}, ${c.uid}, $targetLabel, ${c.lat}, ${c.lon}, ${c.from},
                  ${c.to}, $text, $stamp::timestamptz, $stamp::timestamptz)
          RETURNING id::text""".query[com.histmersion.api.personal.Note.Id].run().head

  def rewrite(
      user: UserId,
      id: com.histmersion.api.personal.Note.Id,
      text: com.histmersion.api.personal.Note.Text,
      at: MarkTime
  )(using DbCon): com.histmersion.domain.personal.notes.NoteRepo.RowsChanged =
    val stamp = at.iso
    sql"""UPDATE notes SET body = $text, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run().pipe(RowsChanged(_))

  def tombstone(user: UserId, id: com.histmersion.api.personal.Note.Id, at: MarkTime)(using DbCon): com.histmersion.domain.personal.notes.NoteRepo.RowsChanged =
    val stamp = at.iso
    sql"""UPDATE notes SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run().pipe(RowsChanged(_))
