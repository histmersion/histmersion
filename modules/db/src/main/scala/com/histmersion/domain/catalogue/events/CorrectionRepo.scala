package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.Corrections
import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*
import io.circe.Json
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.api.catalogue.Correction

/** CORRECTIONS BY HAND ([[Corrections]]): a person's value for one field of a target an importer writes, with what
  * upstream said then and at the last refresh. Generic over the target kind; the events are the first target
  * (`EventRepo.patch` records them, `EventImportRepo.save` reconciles and applies them). Everything here runs inside
  * the caller's transaction.
  */
private[events] object CorrectionRepo:
  type CorrectionStatus = CorrectionStatus.T
  object CorrectionStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileNowKey = ReconcileNowKey.T
  object ReconcileNowKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfKind = OfKind.T
  object OfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfId = OfId.T
  object OfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordKind = RecordKind.T
  object RecordKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordId = RecordId.T
  object RecordId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordField = RecordField.T
  object RecordField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileResultPart1Part1 = ReconcileResultPart1Part1.T
  object ReconcileResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileKind = ReconcileKind.T
  object ReconcileKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReconcileId = ReconcileId.T
  object ReconcileId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListKind = ListKind.T
  object ListKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListId = ListId.T
  object ListId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConflictsInResultPart1 = ConflictsInResultPart1.T
  object ConflictsInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConflictsInResultPart2 = ConflictsInResultPart2.T
  object ConflictsInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Cols =
    "c.id, c.target_kind, c.target_id, c.field, c.value::text, c.upstream_value::text, c.status, c.upstream, " +
      "c.upstream_now::text, c.created_at::text, c.checked_at::text"

  /** The corrections of one target, by field. */
  def of(kind: com.histmersion.domain.catalogue.events.CorrectionRepo.OfKind, id: com.histmersion.domain.catalogue.events.CorrectionRepo.OfId)(using DbCon): List[CorrectionRow] =
    sql"""SELECT ${SqlLiteral(Cols)} FROM corrections c WHERE c.target_kind = $kind AND c.target_id = $id
          ORDER BY c.field""".query[CorrectionRow].run().toList

  /** A person set `field` to `value` while upstream (`upstream`, the catalogue) says `upstreamValue`: equal → no
    * correction is needed (one there goes); otherwise it is recorded, or updated — the upstream value then is what
    * upstream says NOW, and the status starts `active` again.
    */
  def record(kind: com.histmersion.domain.catalogue.events.CorrectionRepo.RecordKind, id: com.histmersion.domain.catalogue.events.CorrectionRepo.RecordId, field: com.histmersion.domain.catalogue.events.CorrectionRepo.RecordField, value: Json, upstreamValue: Json, upstream: Option[com.histmersion.domain.catalogue.events.BCatalogueRow.Provider])(using
      DbCon
  ): Unit =
    if value == upstreamValue then
      val _ =
        sql"""DELETE FROM corrections WHERE target_kind = $kind AND target_id = $id AND field = $field""".update.run()
    else
      val (v, u) = (value.noSpaces, upstreamValue.noSpaces)
      val _ = sql"""INSERT INTO corrections (target_kind, target_id, field, value, upstream, upstream_value)
            VALUES ($kind, $id, $field, $v::jsonb, $upstream, $u::jsonb)
            ON CONFLICT (target_kind, target_id, field) DO UPDATE SET value = EXCLUDED.value,
              upstream = EXCLUDED.upstream, upstream_value = EXCLUDED.upstream_value, upstream_now = NULL,
              status = 'active', updated_at = now(), checked_at = NULL""".update.run()

  /** Reconcile one target's applied corrections with what upstream says now (`now`, by field): each keeps, retires or
    * conflicts ([[Corrections.reconcile]]) and records `now`. Answers the values the target must keep (field → the
    * person's value) and the outcomes.
    */
  def reconcile(kind: com.histmersion.domain.catalogue.events.CorrectionRepo.ReconcileKind, id: com.histmersion.domain.catalogue.events.CorrectionRepo.ReconcileId, now: Map[com.histmersion.domain.catalogue.events.CorrectionRepo.ReconcileNowKey, Json])(using
      DbCon
  ): (Map[com.histmersion.domain.catalogue.events.CorrectionRepo.ReconcileResultPart1Part1, Json], List[Corrections.Outcome]) =
    val applied = of(kind, id).filter(c => Correction.Status.applied(c.status))
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

  /** `histmersion events corrections`: the corrections of `kind` (one target's with `id`), the ones in `status`
    * (default: all but the retired), conflicts first, with each event's label.
    */
  def list(kind: com.histmersion.domain.catalogue.events.CorrectionRepo.ListKind, id: Option[com.histmersion.domain.catalogue.events.CorrectionRepo.ListId], status: Option[com.histmersion.domain.catalogue.events.CorrectionRepo.CorrectionStatus], limit: com.histmersion.PageSize, offset: com.histmersion.Offset)(using
      DbCon
  ): Correction.List =
    val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val where = Sql.of("c.target_kind = ?", Param.S(kind)) ++
      id.map(_.trim).filter(_.nonEmpty).fold(Sql.empty)(i => Sql.of(" AND c.target_id = ?", Param.S(i))) ++
      st.fold(Sql.raw(" AND c.status <> 'agreed'"))(s => Sql.of(" AND c.status = ?", Param.S(s)))
    val count = (Sql.raw("SELECT count(*)::int FROM corrections c WHERE ") ++ where).frag.query[Int].run().head
    val rows = (Sql.raw(
      s"SELECT $Cols, e.label FROM corrections c " +
        "LEFT JOIN events e ON c.target_kind = 'event' AND e.id = c.target_id WHERE "
    ) ++ where ++
      Sql.raw(" ORDER BY c.status = 'conflict' DESC, c.target_id, c.field") ++
      Sql.of(" LIMIT ? OFFSET ?", Param.I(limit.max(1)), Param.I(offset.max(0)))).frag
      .query[(CorrectionRow, Option[String])]
      .run()
      .toList
    Correction.List(count, rows.map((r, label) => r.toDto(label)))

  /** `lint --rule event-correction-conflict`: the events whose catalogue changed a field a person kept by hand, each
    * with what the person kept, what the catalogue said and what it says now.
    */
  def conflictsIn()(using DbCon): List[(com.histmersion.domain.catalogue.events.CorrectionRepo.ConflictsInResultPart1, com.histmersion.domain.catalogue.events.CorrectionRepo.ConflictsInResultPart2)] =
    Sql.raw(ConflictsSql).frag.query[(String, String)].run().toList

  private val ConflictsSql =
    """SELECT c.target_id,
                c.field || ': kept “' || left(coalesce(c.value #>> '{}', c.value::text), 60) || '” by hand; the catalogue (' ||
                coalesce(c.upstream, '?') || ') said “' || left(coalesce(c.upstream_value #>> '{}', c.upstream_value::text), 60) ||
                '” and now says “' || left(coalesce(c.upstream_now #>> '{}', c.upstream_now::text, '?'), 60) || '”'
         FROM corrections c WHERE c.target_kind = 'event' AND c.status = 'conflict'
         ORDER BY c.target_id, c.field"""
