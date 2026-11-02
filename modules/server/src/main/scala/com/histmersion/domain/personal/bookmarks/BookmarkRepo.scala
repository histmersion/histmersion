package com.histmersion.domain.personal.bookmarks

import com.histmersion.api.personal.{BookmarkId, BookmarkLabel, ItemKind as MarkKind}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.ItemRef
import com.histmersion.domain.personal.{MarkTime, TargetColumns, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.values.{PageSize, UserId}

import com.augustnagro.magnum.*

/** The `bookmarks` table. Takes the connection it runs on; a removed bookmark stays as its tombstone. */
private[bookmarks] object BookmarkRepo:

  private def iso(col: String): String = s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  private val Projection =
    s"""SELECT b.id::text, b.target_kind, b.target_id, b.target_uid, b.lat, b.lon, b.zoom, b.period_start, b.period_end,
               b.view, b.label, b.target_label, ${iso("b.created_at")}, ${iso("b.updated_at")}
          FROM bookmarks b"""

  /** Newest first; every one when no `size` is given. */
  def list(user: UserId, kind: Option[MarkKind], size: Option[PageSize])(using DbCon): List[BookmarkRow] =
    (Sql.of(s"$Projection WHERE b.user_id = ? AND b.deleted_at IS NULL", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND b.target_kind = ?", Param.S(k.wire))) ++
      Sql.raw(" ORDER BY b.created_at DESC, b.id DESC") ++
      size.fold(Sql.empty)(s => Sql.of(" LIMIT ?", Param.I(s.value)))).frag.query[BookmarkRow].run().toList

  def live(user: UserId, id: BookmarkId)(using DbCon): Option[BookmarkRow] =
    Sql
      .of(
        s"$Projection WHERE b.user_id = ? AND b.deleted_at IS NULL AND b.id = ?::uuid",
        Param.S(user.value),
        Param.S(id.value)
      )
      .frag
      .query[BookmarkRow]
      .run()
      .headOption

  /** The live bookmark of an item, by the key it was bookmarked under. */
  def ofItem(user: UserId, ref: ItemRef)(using DbCon): Option[BookmarkId] =
    sql"""SELECT id::text FROM bookmarks WHERE user_id = ${user.value} AND target_kind = ${ref.kind.value: String}
            AND target_id = ${ref.key} AND deleted_at IS NULL"""
      .query[BookmarkId]
      .run()
      .headOption

  def insert(
      user: UserId,
      c: TargetColumns,
      label: Option[BookmarkLabel],
      targetLabel: Option[TargetLabel],
      at: MarkTime
  )(using DbCon): BookmarkId =
    val stamp = at.iso
    sql"""INSERT INTO bookmarks (user_id, target_kind, target_id, target_uid, target_label, lat, lon, zoom,
                                 period_start, period_end, view, label, created_at, updated_at)
          VALUES (${user.value}, ${c.kind}, ${c.key}, ${c.uid}, $targetLabel, ${c.lat}, ${c.lon}, ${c.zoom},
                  ${c.from}, ${c.to}, ${c.view}, $label, $stamp::timestamptz, $stamp::timestamptz)
          RETURNING id::text""".query[BookmarkId].run().head

  def relabel(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: MarkTime)(using DbCon): Int =
    val stamp = at.iso
    sql"""UPDATE bookmarks SET label = $label, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()

  def tombstone(user: UserId, id: BookmarkId, at: MarkTime)(using DbCon): Int =
    val stamp = at.iso
    sql"""UPDATE bookmarks SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()
