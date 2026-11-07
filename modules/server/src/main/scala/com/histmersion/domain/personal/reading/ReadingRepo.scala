package com.histmersion.domain.personal.reading

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.{ItemKey, ItemKind, ItemRef, ItemUid}
import com.histmersion.domain.personal.{MarkTime, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*

/** The `read_marks` table. Takes the connection it runs on. */
private[reading] object ReadingRepo:
  type Column = Column.T
  object Column extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private def iso(col: com.histmersion.domain.personal.reading.ReadingRepo.Column): com.histmersion.domain.personal.MarkTime.Iso = s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  private val Projection =
    s"""SELECT m.target_kind, m.target_id, m.target_uid, m.target_label, ${iso(com.histmersion.domain.personal.reading.ReadingRepo.Column("m.read_at"))}, ${iso(com.histmersion.domain.personal.reading.ReadingRepo.Column("m.opened_at"))},
               ${iso(com.histmersion.domain.personal.reading.ReadingRepo.Column("m.created_at"))}, ${iso(com.histmersion.domain.personal.reading.ReadingRepo.Column("m.updated_at"))}
          FROM read_marks m"""

  def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, size: PageSize)(using DbCon): List[MarkRow] =
    val (where, order) = show match
      case ReadingShow.Read   => (" AND m.read_at IS NOT NULL", " ORDER BY m.read_at DESC, m.id DESC")
      case ReadingShow.Opened => (" AND m.opened_at IS NOT NULL", " ORDER BY m.opened_at DESC, m.id DESC")
      case ReadingShow.All    => ("", " ORDER BY m.updated_at DESC, m.id DESC")
    (Sql.of(s"$Projection WHERE m.user_id = ?", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND m.target_kind = ?", Param.S(k.value))) ++
      Sql.raw(where + order) ++ Sql.of(" LIMIT ?", Param.I(size.value))).frag.query[MarkRow].run().toList

  def get(user: UserId, ref: ItemRef)(using DbCon): Option[MarkRow] =
    Sql
      .of(
        s"$Projection WHERE m.user_id = ? AND m.target_kind = ? AND m.target_id = ?",
        Param.S(user.value),
        Param.S(ref.kind.value),
        Param.S(ref.key.value)
      )
      .frag
      .query[MarkRow]
      .run()
      .headOption

  /** The records of `kind` the person marked read. */
  def read(user: UserId, kind: ItemKind)(using DbCon): List[ItemRef] =
    sql"""SELECT target_kind, target_id, target_uid FROM read_marks
          WHERE user_id = ${user.value} AND target_kind = $kind AND read_at IS NOT NULL"""
      .query[(ItemKind, ItemKey, Option[ItemUid])]
      .run()
      .map(ItemRef.apply)
      .toList

  /** Mark read (keeping when it was first marked) or not read yet. */
  def setRead(user: UserId, ref: ItemRef, targetLabel: Option[TargetLabel], read: com.histmersion.domain.personal.reading.ReadingService.Read, at: MarkTime)(using
      DbCon
  ): Unit =
    val stamp = at.iso
    val readAt = Option.when(read)(stamp)
    sql"""INSERT INTO read_marks (user_id, target_kind, target_id, target_uid, target_label, read_at, created_at, updated_at)
          VALUES (${user.value}, ${ref.kind}, ${ref.key}, ${ref.uid}, $targetLabel, $readAt::timestamptz,
                  $stamp::timestamptz, $stamp::timestamptz)
          ON CONFLICT (user_id, target_kind, target_id) DO UPDATE SET
            read_at = CASE WHEN EXCLUDED.read_at IS NULL THEN NULL ELSE coalesce(read_marks.read_at, EXCLUDED.read_at) END,
            target_uid = coalesce(EXCLUDED.target_uid, read_marks.target_uid),
            target_label = coalesce(EXCLUDED.target_label, read_marks.target_label),
            updated_at = EXCLUDED.updated_at""".update.run(): Unit

  def setOpened(user: UserId, ref: ItemRef, targetLabel: Option[TargetLabel], at: MarkTime)(using DbCon): Unit =
    val stamp = at.iso
    sql"""INSERT INTO read_marks (user_id, target_kind, target_id, target_uid, target_label, opened_at, created_at, updated_at)
          VALUES (${user.value}, ${ref.kind}, ${ref.key}, ${ref.uid}, $targetLabel, $stamp::timestamptz,
                  $stamp::timestamptz, $stamp::timestamptz)
          ON CONFLICT (user_id, target_kind, target_id) DO UPDATE SET
            opened_at = EXCLUDED.opened_at,
            target_uid = coalesce(EXCLUDED.target_uid, read_marks.target_uid),
            target_label = coalesce(EXCLUDED.target_label, read_marks.target_label),
            updated_at = EXCLUDED.updated_at""".update.run(): Unit
