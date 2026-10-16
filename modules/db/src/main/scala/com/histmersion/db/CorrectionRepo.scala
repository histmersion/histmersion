package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import io.circe.Json
import javax.sql.DataSource

private[db] final case class CorrectionRow(id: Long, targetKind: String, targetId: String, field: String, value: String,
    upstreamValue: String, status: String, upstream: Option[String], upstreamNow: Option[String], createdAt: String,
    checkedAt: Option[String]):
  def toDto(label: Option[String] = None): Correction =
    Correction(id, targetKind, targetId, field, value, upstreamValue, status, upstream, upstreamNow, label, createdAt, checkedAt)
  def json(s: String): Json = io.circe.parser.parse(s).getOrElse(Json.Null)

given DbCodec[CorrectionRow] = DbCodec.derived

/** CORRECTIONS BY HAND (V088, [[Corrections]]): a person's value for one field of a target an importer
  * writes, with what upstream said then and at the last refresh. Generic over the target kind; the
  * events are the first target (`EventRepo.patch` records them, `EventCatalogRepo.save` reconciles and
  * applies them). Everything here runs inside the caller's transaction. */
object CorrectionRepo:

  private val Cols =
    "c.id, c.target_kind, c.target_id, c.field, c.value::text, c.upstream_value::text, c.status, c.upstream, " +
      "c.upstream_now::text, c.created_at::text, c.checked_at::text"

  /** The corrections of one target, by field. */
  def of(kind: String, id: String)(using DbCon): List[CorrectionRow] =
    sql"""SELECT ${SqlLiteral(Cols)} FROM corrections c WHERE c.target_kind = $kind AND c.target_id = $id
          ORDER BY c.field""".query[CorrectionRow].run().toList

  /** A person set `field` to `value` while upstream (`upstream`, the catalogue) says `upstreamValue`:
    * equal → no correction is needed (one there goes); otherwise it is recorded, or updated — the
    * upstream value then is what upstream says NOW, and the status starts `active` again. */
  def record(kind: String, id: String, field: String, value: Json, upstreamValue: Json, upstream: Option[String])
      (using DbCon): Unit =
    if value == upstreamValue then
      val _ = sql"""DELETE FROM corrections WHERE target_kind = $kind AND target_id = $id AND field = $field""".update.run()
    else
      val (v, u) = (value.noSpaces, upstreamValue.noSpaces)
      val _ = sql"""INSERT INTO corrections (target_kind, target_id, field, value, upstream, upstream_value)
            VALUES ($kind, $id, $field, $v::jsonb, $upstream, $u::jsonb)
            ON CONFLICT (target_kind, target_id, field) DO UPDATE SET value = EXCLUDED.value,
              upstream = EXCLUDED.upstream, upstream_value = EXCLUDED.upstream_value, upstream_now = NULL,
              status = 'active', updated_at = now(), checked_at = NULL""".update.run()

  /** Reconcile one target's applied corrections with what upstream says now (`now`, by field): each
    * keeps, retires or conflicts ([[Corrections.reconcile]]) and records `now`. Answers the values the
    * target must keep (field → the person's value) and the outcomes. */
  def reconcile(kind: String, id: String, now: Map[String, Json])(using DbCon): (Map[String, Json], List[Corrections.Outcome]) =
    val applied = of(kind, id).filter(c => CorrectionStatus.applied(c.status))
    val results = applied.flatMap { c =>
      now.get(c.field).map { up =>
        val o = Corrections.reconcile(c.json(c.value), c.json(c.upstreamValue), up)
        val (st, upNow) = (Corrections.status(o), up.noSpaces)
        val _ = sql"""UPDATE corrections SET status = $st, upstream_now = $upNow::jsonb, checked_at = now(),
                updated_at = CASE WHEN status = $st THEN updated_at ELSE now() END WHERE id = ${c.id}""".update.run()
        (c, o)
      }
    }
    val keep = results.collect { case (c, o) if o != Corrections.Outcome.Retire => c.field -> c.json(c.value) }.toMap
    (keep, results.map(_._2))

  /** `histmersion events corrections`: the corrections of `kind` (one target's with `id`), the ones in
    * `status` (default: all but the retired), conflicts first, with each event's label. */
  def list(ds: DataSource, kind: String, id: Option[String], status: Option[String], limit: Int, offset: Int): CorrectionList =
    connect(ds):
      val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
      val where = Sql.of("c.target_kind = ?", Param.S(kind)) ++
        id.map(_.trim).filter(_.nonEmpty).fold(Sql.empty)(i => Sql.of(" AND c.target_id = ?", Param.S(i))) ++
        st.fold(Sql.raw(" AND c.status <> 'agreed'"))(s => Sql.of(" AND c.status = ?", Param.S(s)))
      val count = (Sql.raw("SELECT count(*)::int FROM corrections c WHERE ") ++ where).frag.query[Int].run().head
      val rows = (Sql.raw(s"SELECT $Cols, e.label FROM corrections c " +
          "LEFT JOIN events e ON c.target_kind = 'event' AND e.id = c.target_id WHERE ") ++ where ++
        Sql.raw(" ORDER BY c.status = 'conflict' DESC, c.target_id, c.field") ++
        Sql.of(" LIMIT ? OFFSET ?", Param.I(limit.max(1)), Param.I(offset.max(0))))
        .frag.query[(CorrectionRow, Option[String])].run().toList
      CorrectionList(count, rows.map((r, label) => r.toDto(label)))
