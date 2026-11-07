package com.histmersion.domain.personal.bookmarks

import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.ItemRef
import com.histmersion.domain.personal.{MarkTime, TargetColumns, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*

/** The `bookmarks` table. Takes the connection it runs on; a removed bookmark stays as its tombstone. */
private[bookmarks] object BookmarkRepo:
  type Column = Column.T
  object Column extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelabelCount = RelabelCount.T
  object RelabelCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TombstoneCount = TombstoneCount.T
  object TombstoneCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private def iso(col: com.histmersion.domain.personal.bookmarks.BookmarkRepo.Column): com.histmersion.domain.personal.MarkTime.Iso = s"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

  private val Projection =
    s"""SELECT b.id::text, b.target_kind, b.target_id, b.target_uid, b.lat, b.lon, b.zoom, b.period_start, b.period_end,
               b.view, b.label, b.target_label, ${iso("b.created_at")}, ${iso("b.updated_at")}
          FROM bookmarks b"""

  /** Newest first; every one when no `size` is given. */
  def list(user: UserId, kind: Option[MarkKind], size: Option[PageSize])(using DbCon): List[Bookmark.Row] =
    (Sql.of(s"$Projection WHERE b.user_id = ? AND b.deleted_at IS NULL", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND b.target_kind = ?", Param.S(k.wire))) ++
      Sql.raw(" ORDER BY b.created_at DESC, b.id DESC") ++
      size.fold(Sql.empty)(s => Sql.of(" LIMIT ?", Param.I(s.value)))).frag.query[Bookmark.Row].run().toList

  def live(user: UserId, id: com.histmersion.api.personal.Bookmark.Id)(using DbCon): Option[Bookmark.Row] =
    Sql
      .of(
        s"$Projection WHERE b.user_id = ? AND b.deleted_at IS NULL AND b.id = ?::uuid",
        Param.S(user.value),
        Param.S(id.value)
      )
      .frag
      .query[Bookmark.Row]
      .run()
      .headOption

  /** The live bookmark of an item, by the key it was bookmarked under. */
  def ofItem(user: UserId, ref: ItemRef)(using DbCon): Option[com.histmersion.api.personal.Bookmark.Id] =
    sql"""SELECT id::text FROM bookmarks WHERE user_id = ${user.value} AND target_kind = ${ref.kind.value: String}
            AND target_id = ${ref.key} AND deleted_at IS NULL"""
      .query[com.histmersion.api.personal.Bookmark.Id]
      .run()
      .headOption

  def insert(
      user: UserId,
      c: TargetColumns,
      label: Option[com.histmersion.api.personal.Bookmark.Label],
      targetLabel: Option[TargetLabel],
      at: MarkTime
  )(using DbCon): com.histmersion.api.personal.Bookmark.Id =
    val stamp = at.iso
    sql"""INSERT INTO bookmarks (user_id, target_kind, target_id, target_uid, target_label, lat, lon, zoom,
                                 period_start, period_end, view, label, created_at, updated_at)
          VALUES (${user.value}, ${c.kind}, ${c.key}, ${c.uid}, $targetLabel, ${c.lat}, ${c.lon}, ${c.zoom},
                  ${c.from}, ${c.to}, ${c.view}, $label, $stamp::timestamptz, $stamp::timestamptz)
          RETURNING id::text""".query[com.histmersion.api.personal.Bookmark.Id].run().head

  def relabel(
      user: UserId,
      id: com.histmersion.api.personal.Bookmark.Id,
      label: Option[com.histmersion.api.personal.Bookmark.Label],
      at: MarkTime
  )(using DbCon): com.histmersion.domain.personal.bookmarks.BookmarkRepo.RelabelCount =
    val stamp = at.iso
    sql"""UPDATE bookmarks SET label = $label, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()

  def tombstone(user: UserId, id: com.histmersion.api.personal.Bookmark.Id, at: MarkTime)(using DbCon): com.histmersion.domain.personal.bookmarks.BookmarkRepo.TombstoneCount =
    val stamp = at.iso
    sql"""UPDATE bookmarks SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
          WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()
