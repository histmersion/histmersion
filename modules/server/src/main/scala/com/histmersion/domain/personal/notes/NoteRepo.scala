package com.histmersion.domain.personal.notes

import com.histmersion.api.personal.{NoteId, NoteText}
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.personal.{MarkTime, TargetColumns, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*

/** The `notes` table. Takes the connection it runs on; a removed note stays as its tombstone. */
private[notes] object NoteRepo:

  private def iso(col: String): String = s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  private val Projection =
    s"""SELECT n.id::text, n.target_kind, n.target_id, n.target_uid, n.lat, n.lon, n.period_start, n.period_end, n.body,
               n.target_label, ${iso("n.created_at")}, ${iso("n.updated_at")}
          FROM notes n"""

  /** Last changed first; every one when no `size` is given. */
  def list(user: UserId, kind: Option[MarkKind], item: Option[NoteItem], size: Option[PageSize])(using
      DbCon
  ): List[NoteRow] =
    (Sql.of(s"$Projection WHERE n.user_id = ? AND n.deleted_at IS NULL", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND n.target_kind = ?", Param.S(k.wire))) ++
      item.fold(Sql.empty)(i =>
        Sql.of(
          " AND (n.target_id = ? OR n.target_uid = ANY(?::uuid[]))",
          Param.S(i.key.value),
          Param.A(i.uids.toList.map(_.value.toString))
        )
      ) ++
      Sql.raw(" ORDER BY n.updated_at DESC, n.id DESC") ++
      size.fold(Sql.empty)(s => Sql.of(" LIMIT ?", Param.I(s.value)))).frag.query[NoteRow].run().toList

  def live(user: UserId, id: NoteId)(using DbCon): Option[NoteRow] =
    Sql
      .of(
        s"$Projection WHERE n.user_id = ? AND n.deleted_at IS NULL AND n.id = ?::uuid",
        Param.S(user.value),
        Param.S(id.value)
      )
      .frag
      .query[NoteRow]
      .run()
      .headOption

  def insert(user: UserId, c: TargetColumns, text: NoteText, targetLabel: Option[TargetLabel], at: MarkTime)(using
      DbCon
  ): NoteId =
    val stamp = at.iso
    sql"""INSERT INTO notes (user_id, target_kind, target_id, target_uid, target_label, lat, lon, period_start,
                             period_end, body, created_at, updated_at)
          VALUES (${user.value}, ${c.kind}, ${c.key}, ${c.uid}, $targetLabel, ${c.lat}, ${c.lon}, ${c.from},
                  ${c.to}, $text, $stamp::timestamptz, $stamp::timestamptz)
          RETURNING id::text""".query[NoteId].run().head

  def rewrite(user: UserId, id: NoteId, text: NoteText, at: MarkTime)(using DbCon): Int =
    val stamp = at.iso
    sql"""UPDATE notes SET body = $text, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()

  def tombstone(user: UserId, id: NoteId, at: MarkTime)(using DbCon): Int =
    val stamp = at.iso
    sql"""UPDATE notes SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()
