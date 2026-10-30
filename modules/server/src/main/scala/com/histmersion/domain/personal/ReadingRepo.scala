package com.histmersion.domain.personal

import com.histmersion.api.personal.*
import com.histmersion.db.{Param, Sql, given}
import com.histmersion.domain.personal.ItemTitles.{iso, joins, key, title}

import com.augustnagro.magnum.*

import java.time.Instant

/** A read mark as the table answers it. */
private[personal] final case class MarkRow(kind: String, key: String, title: Option[String], readAt: Option[String],
    openedAt: Option[String]) derives DbCodec:
  def mark: Option[ReadMark] =
    ItemKind.values.find(_.wire == kind).map(k =>
      ReadMark(k, ItemKey(key), PersonalRules.title(Target.item(k, key), title, None), readAt.isDefined, readAt, openedAt))

/** A source or an article not marked read, as "Not read yet" lists it. */
private[personal] final case class UnreadDbRow(key: String, title: String, authors: List[String], year: Option[Int],
    status: Option[String], periodStart: Option[Long], periodEnd: Option[Long]) derives DbCodec

/** The `read_marks` table, and what is not marked in it. Takes the connection it runs on. */
private[personal] object ReadingRepo:

  private val Projection =
    s"""SELECT m.target_kind, ${key("m")}, ${title("m")}, ${iso("m.read_at")}, ${iso("m.opened_at")}
          FROM read_marks m ${joins("m")}"""

  def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, limit: Int)(using DbCon): List[MarkRow] =
    val (where, order) = show match
      case ReadingShow.Read   => (" AND m.read_at IS NOT NULL", " ORDER BY m.read_at DESC, m.id DESC")
      case ReadingShow.Opened => (" AND m.opened_at IS NOT NULL", " ORDER BY m.opened_at DESC, m.id DESC")
      case ReadingShow.All    => ("", " ORDER BY m.updated_at DESC, m.id DESC")
    (Sql.of(s"$Projection WHERE m.user_id = ?", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND m.target_kind = ?", Param.S(k.wire))) ++
      Sql.raw(where + order) ++ Sql.of(" LIMIT ?", Param.I(limit)))
      .frag.query[MarkRow].run().toList

  def get(user: UserId, kind: ItemKind, itemKey: String)(using DbCon): Option[MarkRow] =
    Sql.of(s"$Projection WHERE m.user_id = ? AND m.target_kind = ? AND m.target_id = ?", Param.S(user.value),
      Param.S(kind.wire), Param.S(itemKey)).frag.query[MarkRow].run().headOption

  /** Mark read (keeping when it was first marked) or not read yet. */
  def setRead(user: UserId, kind: ItemKind, itemKey: String, uid: Option[String], targetLabel: Option[String], read: Boolean,
      at: Instant)(using DbCon): Unit =
    val stamp = PersonalRules.iso(at)
    val readAt = Option.when(read)(stamp)
    sql"""INSERT INTO read_marks (user_id, target_kind, target_id, target_uid, target_label, read_at, created_at, updated_at)
          VALUES (${user.value}, ${kind.wire}, $itemKey, ${uid}::uuid, $targetLabel, ${readAt}::timestamptz,
                  $stamp::timestamptz, $stamp::timestamptz)
          ON CONFLICT (user_id, target_kind, target_id) DO UPDATE SET
            read_at = CASE WHEN EXCLUDED.read_at IS NULL THEN NULL ELSE coalesce(read_marks.read_at, EXCLUDED.read_at) END,
            target_uid = coalesce(EXCLUDED.target_uid, read_marks.target_uid),
            target_label = coalesce(EXCLUDED.target_label, read_marks.target_label),
            updated_at = EXCLUDED.updated_at""".update.run(): Unit

  def setOpened(user: UserId, kind: ItemKind, itemKey: String, uid: Option[String], targetLabel: Option[String],
      at: Instant)(using DbCon): Unit =
    val stamp = PersonalRules.iso(at)
    sql"""INSERT INTO read_marks (user_id, target_kind, target_id, target_uid, target_label, opened_at, created_at, updated_at)
          VALUES (${user.value}, ${kind.wire}, $itemKey, ${uid}::uuid, $targetLabel, $stamp::timestamptz,
                  $stamp::timestamptz, $stamp::timestamptz)
          ON CONFLICT (user_id, target_kind, target_id) DO UPDATE SET
            opened_at = EXCLUDED.opened_at,
            target_uid = coalesce(EXCLUDED.target_uid, read_marks.target_uid),
            target_label = coalesce(EXCLUDED.target_label, read_marks.target_label),
            updated_at = EXCLUDED.updated_at""".update.run(): Unit

  private def unreadFrom(user: UserId, kind: ItemKind): Sql = kind match
    case ItemKind.Article =>
      Sql.of("""FROM articles i WHERE NOT EXISTS (SELECT 1 FROM read_marks m WHERE m.user_id = ? AND m.target_kind = 'article'
                 AND (m.target_id = i.slug OR m.target_uid = i.uid) AND m.read_at IS NOT NULL)""", Param.S(user.value))
    case _ =>
      Sql.of("""FROM sources i WHERE NOT EXISTS (SELECT 1 FROM read_marks m WHERE m.user_id = ? AND m.target_kind = 'source'
                 AND (m.target_id = i.citekey OR m.target_uid = i.uid) AND m.read_at IS NOT NULL)""", Param.S(user.value))

  /** One page of what is not marked read, by title, and how many there are. */
  def unread(user: UserId, kind: ItemKind, limit: Int, offset: Int)(using DbCon): (List[UnreadDbRow], Int) =
    val select = kind match
      case ItemKind.Article =>
        "SELECT i.slug, i.title, '{}'::text[], NULL::int, NULL::text, lower(i.period), upper(i.period) - 1 "
      case _ =>
        "SELECT i.citekey, i.title, i.authors, i.year, i.status, lower(i.period), upper(i.period) - 1 "
    val order = if kind == ItemKind.Article then " ORDER BY lower(i.title), i.slug" else " ORDER BY lower(i.title), i.citekey"
    val rows = (Sql.raw(select) ++ unreadFrom(user, kind) ++ Sql.of(s"$order LIMIT ? OFFSET ?", Param.I(limit), Param.I(offset)))
      .frag.query[UnreadDbRow].run().toList
    val total = (Sql.raw("SELECT count(*) ") ++ unreadFrom(user, kind)).frag.query[Long].run().head.toInt
    (rows, total)
