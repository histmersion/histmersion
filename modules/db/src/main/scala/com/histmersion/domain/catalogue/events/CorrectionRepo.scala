package com.histmersion
package domain.catalogue.events
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.api.catalogue.Corrections

import com.augustnagro.magnum.*
import com.histmersion.util.database.given_DbCodec_List
import com.histmersion.api.catalogue.Correction
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.checks.Lint
import com.histmersion.util.json.RawJson.equality
import com.histmersion.util.database.Unused

/** CORRECTIONS BY HAND ([[Corrections]]): a person's value for one field of a target an importer writes, with what
  * upstream said then and at the last refresh. Generic over the target kind; the events are the first target
  * (`EventRepo.patch` records them, `EventImportRepo.save` reconciles and applies them). Everything here runs inside
  * the caller's transaction.
  */
private[events] object CorrectionRepo:
  private val Cols =
    sql"c.id, c.target_kind, c.target_id, c.field, c.value::text, c.upstream_value::text, c.status, c.upstream, c.upstream_now::text, c.created_at::text, c.checked_at::text"

  /** The corrections of one target, by field. */
  def of(kind: Correction.TargetKind, id: Correction.TargetId)(using DbCon): List[CorrectionRow] =
    sql"""SELECT $Cols FROM corrections c WHERE c.target_kind = $kind AND c.target_id = $id
          ORDER BY c.field""".query[CorrectionRow].run().toList

  /** A person set `field` to `value` while upstream (`upstream`, the catalogue) says `upstreamValue`: equal → no
    * correction is needed (one there goes); otherwise it is recorded, or updated — the upstream value then is what
    * upstream says NOW, and the status starts `active` again.
    */
  def record(
      kind: Correction.TargetKind,
      id: Correction.TargetId,
      field: Correction.Field,
      value: Json,
      upstreamValue: Json,
      upstream: Option[BCatalogueRow.Provider]
  )(using
      DbCon
  ): Unit =
    if value == upstreamValue then
      Unused(
        sql"DELETE FROM corrections WHERE target_kind = $kind AND target_id = $id AND field = $field".update.run(),
        Unused.Why.Idempotent
      )
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
  def reconcile(kind: Correction.TargetKind, id: Correction.TargetId, now: Map[Correction.Field, Json])(using
      DbCon
  ): (Map[Correction.Field, Json], List[Corrections.Outcome]) =
    val applied =
      of(kind, id).filter(c => Correction.Status.applied.contains(Correction.Status.Applied(c.status.value)))
    val results = applied.flatMap { c =>
      now.get(c.field).map { up =>
        val o = Corrections.reconcile(c.valueJson, c.upstreamValueJson, up)
        val (st, upNow) = (Corrections.status(o), up.noSpaces)
        Unused(
          sql"""UPDATE corrections SET status = $st, upstream_now = $upNow::jsonb, checked_at = now(),
                updated_at = CASE WHEN status = $st THEN updated_at ELSE now() END WHERE id = ${c.id}""".update.run(),
          Unused.Why.Held
        )
        (c, o)
      }
    }
    val keep = results.collect { case (c, o) if o != Corrections.Outcome.Retire => c.field -> c.valueJson }.toMap
    (keep, results.map(_._2))

  /** `histmersion events corrections`: the corrections of `kind` (one target's with `id`), the ones in `status`
    * (default: all but the retired), conflicts first, with each event's label.
    */
  def list(
      kind: Correction.TargetKind,
      id: Option[Correction.TargetId],
      status: Option[Correction.StatusValue],
      limit: PageSize,
      offset: Offset
  )(using
      DbCon
  ): Correction.List =
    val st = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
    val where = sql"c.target_kind = $kind${id
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold(sql"")(i => sql" AND c.target_id = $i")}${st.fold(sql" AND c.status <> 'agreed'")(s =>
        sql" AND c.status = $s"
      )}"
    val count =
      sql"SELECT count(*)::int FROM corrections c WHERE $where".query[Int].run().head
    val rows =
      sql"SELECT $Cols, e.label FROM corrections c LEFT JOIN events e ON c.target_kind = 'event' AND e.id = c.target_id WHERE $where ORDER BY c.status = 'conflict' DESC, c.target_id, c.field LIMIT ${limit.value.max(1)} OFFSET ${offset.value.max(0)}"
        .query[(CorrectionRow, Option[Correction.Label])]
        .run()
        .toList
    Correction.List(Correction.List.Total(count), rows.map((r, label) => r.toDto(label)))

  /** `lint --rule event-correction-conflict`: the events whose catalogue changed a field a person kept by hand, each
    * with what the person kept, what the catalogue said and what it says now.
    */
  def conflictsIn()(using DbCon): List[(Correction.TargetId, Lint.LintItem.Detail)] =
    sql"$ConflictsSql".query[(Correction.TargetId, Lint.LintItem.Detail)].run().toList

  private val ConflictsSql =
    sql"""SELECT c.target_id,
                c.field || ': kept “' || left(coalesce(c.value #>> '{}', c.value::text), 60) || '” by hand; the catalogue (' ||
                coalesce(c.upstream, '?') || ') said “' || left(coalesce(c.upstream_value #>> '{}', c.upstream_value::text), 60) ||
                '” and now says “' || left(coalesce(c.upstream_now #>> '{}', c.upstream_now::text, '?'), 60) || '”'
         FROM corrections c WHERE c.target_kind = 'event' AND c.status = 'conflict'
         ORDER BY c.target_id, c.field"""
