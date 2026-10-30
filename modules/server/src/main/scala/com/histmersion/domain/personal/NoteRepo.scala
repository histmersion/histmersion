package com.histmersion.domain.personal

import com.histmersion.values.UserId

import com.histmersion.api.personal.*
import com.histmersion.db.{Param, Sql}
import com.histmersion.domain.personal.ItemTitles.{iso, joins, key, title}

import com.augustnagro.magnum.*

import java.time.Instant

/** A note as the table answers it, with its item's key and title now. */
final private[personal] case class NoteRow(
    id: String,
    kind: String,
    key: Option[String],
    lat: Option[Double],
    lon: Option[Double],
    from: Option[Long],
    to: Option[Long],
    body: String,
    title: Option[String],
    createdAt: String,
    updatedAt: String
) derives DbCodec:
  def note: Option[Note] =
    for
      t <- PersonalRules.target(kind, key, lat, lon, None, from, to, None)
      id <- NoteId.parse(id).toOption
    yield Note(id, t, NoteText(body), PersonalRules.title(t, title, None), createdAt, updatedAt)

/** The `notes` table. Takes the connection it runs on; a removed note stays as its tombstone. */
private[personal] object NoteRepo:

  private val Projection =
    s"""SELECT n.id::text, n.target_kind, ${key("n")}, n.lat, n.lon, n.period_start, n.period_end, n.body,
               ${title("n")}, ${iso("n.created_at")}, ${iso("n.updated_at")}
          FROM notes n ${joins("n")}"""

  def list(user: UserId, kind: Option[ItemKind], itemKey: Option[String], words: List[String], limit: Int)(using
      DbCon
  ): List[NoteRow] =
    (Sql.of(s"$Projection WHERE n.user_id = ? AND n.deleted_at IS NULL", Param.S(user.value)) ++
      kind.fold(Sql.empty)(k => Sql.of(" AND n.target_kind = ?", Param.S(k.wire))) ++
      itemKey.fold(Sql.empty)(k => Sql.of(s" AND (n.target_id = ? OR ${key("n")} = ?)", Param.S(k), Param.S(k))) ++
      ItemTitles.wordsSql(words, s"concat_ws(' ', n.body, ${title("n")}, ${key("n")})") ++
      Sql.of(" ORDER BY n.updated_at DESC, n.id DESC LIMIT ?", Param.I(limit))).frag.query[NoteRow].run().toList

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

  def insert(user: UserId, t: Target, text: NoteText, targetLabel: Option[String], uid: Option[String], at: Instant)(
      using DbCon
  ): NoteId =
    val stamp = PersonalRules.iso(at)
    val c = TargetColumns.of(t)
    val body: String = text.value
    val id = sql"""INSERT INTO notes (user_id, target_kind, target_id, target_uid, target_label, lat, lon, period_start,
                     period_end, body, created_at, updated_at)
                   VALUES (${c.user(
        user
      )}, ${c.kind}, ${c.key}, $uid::uuid, $targetLabel, ${c.lat}, ${c.lon}, ${c.from},
                     ${c.to}, $body, $stamp::timestamptz, $stamp::timestamptz)
                   RETURNING id::text""".query[String].run().head
    NoteId.parse(id).fold(e => throw IllegalStateException(e), identity)

  def rewrite(user: UserId, id: NoteId, text: NoteText, at: Instant)(using DbCon): Int =
    val stamp = PersonalRules.iso(at)
    val body: String = text.value
    sql"""UPDATE notes SET body = $body, updated_at = $stamp::timestamptz
           WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()

  def tombstone(user: UserId, id: NoteId, at: Instant)(using DbCon): Int =
    val stamp = PersonalRules.iso(at)
    sql"""UPDATE notes SET deleted_at = $stamp::timestamptz, updated_at = $stamp::timestamptz
           WHERE user_id = ${user.value} AND id = ${id.value}::uuid AND deleted_at IS NULL""".update.run()
