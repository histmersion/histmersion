package com.histmersion
package domain.personal.bookmarks

import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.ItemRef
import com.histmersion.domain.personal.{MarkTime, TargetColumns, TargetLabel}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Timestamps
import com.histmersion.{PageSize, UserId}

import com.augustnagro.magnum.*

import scala.util.chaining.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.util.database.StoredCreator
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The `bookmarks` table. Takes the connection it runs on; a removed bookmark stays as its tombstone. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Sql, "reads and writes the bookmark tables and their row codecs")
private[bookmarks] object BookmarkRepo:
  type RowsChanged = RowsChanged.T
  object RowsChanged extends Newtype[Int, Pure]

  private val Projection =
    sql"""SELECT b.id::text, b.target_kind, b.target_id, b.target_uid, b.lat, b.lon, b.zoom, b.period_start, b.period_end,
               b.view, b.label, b.target_label, ${Timestamps.iso(sql"b.created_at")}, ${Timestamps.iso(
        sql"b.updated_at"
      )}
          FROM bookmarks b"""

  /** Newest first; every one when no `size` is given. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def list(user: UserId, kind: Option[MarkKind], size: Option[PageSize])(using DbCon): List[Bookmark.Row] =
    val ofKind = kind.fold(sql"")(k => sql" AND b.target_kind = ${k.wire}")
    val ordered =
      sql"$Projection WHERE b.user_id = $user AND b.deleted_at IS NULL$ofKind ORDER BY b.created_at DESC, b.id DESC"
    sql"$ordered${size.fold(sql"")(s => sql" LIMIT $s")}"
      .query[Bookmark.Row]
      .run()
      .toList

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def live(user: UserId, id: com.histmersion.api.personal.Bookmark.Id)(using DbCon): Option[Bookmark.Row] =
    sql"$Projection WHERE b.user_id = $user AND b.deleted_at IS NULL AND b.id = $id::uuid"
      .query[Bookmark.Row]
      .run()
      .headOption

  /** The live bookmark of an item, by the key it was bookmarked under. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def ofItem(user: UserId, ref: ItemRef)(using DbCon): Option[com.histmersion.api.personal.Bookmark.Id] =
    sql"""SELECT id::text FROM bookmarks WHERE user_id = $user AND target_kind = ${ref.kind.value: String}
            AND target_id = ${ref.key} AND deleted_at IS NULL"""
      .query[com.histmersion.api.personal.Bookmark.Id]
      .run()
      .headOption

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def insert(
      user: UserId,
      c: TargetColumns,
      label: Option[com.histmersion.api.personal.Bookmark.Label],
      targetLabel: Option[TargetLabel],
      at: MarkTime
  )(using DbCon)(using creator: Creator.New): com.histmersion.api.personal.Bookmark.Id =
    val stamp = at.iso
    sql"""INSERT INTO bookmarks (user_id, target_kind, target_id, target_uid, target_label, lat, lon, zoom,
                                 period_start, period_end, view, label, created_at, updated_at, creator)
          VALUES ($user, ${c.kind}, ${c.key}, ${c.uid}, $targetLabel, ${c.lat}, ${c.lon}, ${c.zoom},
                  ${c.from}, ${c.to}, ${c.view}, $label, $stamp::timestamptz, $stamp::timestamptz, ${StoredCreator
        .column(creator)}::jsonb)
          RETURNING id::text""".query[com.histmersion.api.personal.Bookmark.Id].run().head

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def relabel(
      user: UserId,
      id: com.histmersion.api.personal.Bookmark.Id,
      label: Option[com.histmersion.api.personal.Bookmark.Label],
      at: MarkTime
  )(using DbCon): RowsChanged =
    val stamp = at.iso
    sql"""UPDATE bookmarks SET label = $label, updated_at = $stamp::timestamptz
          WHERE user_id = $user AND id = $id::uuid AND deleted_at IS NULL""".update
      .run()
      .pipe(RowsChanged(_))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def tombstone(user: UserId, id: com.histmersion.api.personal.Bookmark.Id, at: MarkTime)(using DbCon): RowsChanged =
    val stamp = at.iso
    sql"""UPDATE bookmarks SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
          WHERE user_id = $user AND id = $id::uuid AND deleted_at IS NULL""".update
      .run()
      .pipe(RowsChanged(_))
