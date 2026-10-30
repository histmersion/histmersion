package com.histmersion.domain.personal

import com.histmersion.api.personal.*
import com.histmersion.db.{Param, Sql}
import com.histmersion.domain.personal.ItemTitles.{iso, joins, key, title}

import com.augustnagro.magnum.*

import java.time.Instant

/** A bookmark as the table answers it, with its item's key and title now. */
final private[personal] case class BookmarkRow(
    id: String,
    kind: String,
    key: Option[String],
    lat: Option[Double],
    lon: Option[Double],
    zoom: Option[Double],
    from: Option[Long],
    to: Option[Long],
    view: Option[String],
    label: Option[String],
    title: Option[String],
    createdAt: String,
    updatedAt: String
) derives DbCodec:
  def bookmark: Option[Bookmark] =
    for
      t <- PersonalRules.target(kind, key, lat, lon, zoom, from, to, view)
      id <- BookmarkId.parse(id).toOption
    yield Bookmark(id, t, PersonalRules.title(t, title, None), label.map(BookmarkLabel(_)), createdAt, updatedAt)

/** The `bookmarks` table. Takes the connection it runs on; a removed bookmark stays as its tombstone. */
private[personal] object BookmarkRepo:

  private val Projection =
    s"""SELECT b.id::text, b.target_kind, ${key("b")}, b.lat, b.lon, b.zoom, b.period_start, b.period_end, b.view,
               b.label, ${title("b")}, ${iso("b.created_at")}, ${iso("b.updated_at")}
          FROM bookmarks b ${joins("b")}"""

  def list(user: UserId, kind: Option[ItemKind], words: List[String], limit: Int)(using DbCon): List[BookmarkRow] =
    (Sql.of(s"$Projection WHERE b.user_id = ? AND b.deleted_at IS NULL", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND b.target_kind = ?", Param.S(k.wire))) ++
      ItemTitles.wordsSql(words, s"concat_ws(' ', b.label, ${title("b")}, ${key("b")})") ++
      Sql.of(" ORDER BY b.created_at DESC, b.id DESC LIMIT ?", Param.I(limit))).frag.query[BookmarkRow].run().toList

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

  /** The live bookmark of an item. */
  def ofItem(user: UserId, kind: ItemKind, itemKey: String)(using DbCon): Option[BookmarkId] =
    sql"""SELECT id::text FROM bookmarks WHERE user_id = ${user.value} AND target_kind = ${kind.wire}
            AND target_id = $itemKey AND deleted_at IS NULL"""
      .query[String]
      .run()
      .headOption
      .flatMap(BookmarkId.parse(_).toOption)

  def insert(
      user: UserId,
      t: Target,
      label: Option[BookmarkLabel],
      targetLabel: Option[String],
      uid: Option[String],
      at: Instant
  )(using DbCon): BookmarkId =
    val stamp = PersonalRules.iso(at)
    val c = TargetColumns.of(t)
    val name: Option[String] = label.map(_.value)
    val id = sql"""INSERT INTO bookmarks (user_id, target_kind, target_id, target_uid, target_label, lat, lon, zoom,
                     period_start, period_end, view, label, created_at, updated_at)
                   VALUES (${c.user(
        user
      )}, ${c.kind}, ${c.key}, $uid::uuid, $targetLabel, ${c.lat}, ${c.lon}, ${c.zoom},
                     ${c.from}, ${c.to}, ${c.view}, $name, $stamp::timestamptz, $stamp::timestamptz)
                   RETURNING id::text""".query[String].run().head
    BookmarkId.parse(id).fold(e => throw IllegalStateException(e), identity)

  def relabel(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: Instant)(using DbCon): Int =
    val stamp = PersonalRules.iso(at)
    val name: Option[String] = label.map(_.value)
    sql"""UPDATE bookmarks SET label = $name, updated_at = $stamp::timestamptz
           WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()

  def tombstone(user: UserId, id: BookmarkId, at: Instant)(using DbCon): Int =
    val stamp = PersonalRules.iso(at)
    sql"""UPDATE bookmarks SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
           WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()
