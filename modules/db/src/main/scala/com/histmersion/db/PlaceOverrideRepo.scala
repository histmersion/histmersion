package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** A person's kind and period for a place (V080): `histmersion places set`, MCP `place_override`.
  *
  * The override is a row of `place_overrides`; the DATABASE puts it over whatever kind and period any
  * writer of `places` sends (the `places_override` trigger), so `places seed`, a gazetteer load, a bundle
  * or a lint fix can never undo it, and it remembers what the source says, so a reset restores that
  * without a re-import. Setting or resetting one re-matches that place's findings in the same
  * transaction ([[PlaceRepo.materialisePlaces]]): a kind decides how a place takes findings — a site by
  * the point tolerance, a point-only region only by an explicit match. */
object PlaceOverrideRepo:

  private final case class Row(label: String, kind: String, start: Option[Long], end: Option[Long])
  private given DbCodec[Row] = DbCodec.derived
  private given DbCodec[PlaceOverride] = DbCodec.derived

  /** The place's label, kind and STATED period (an unknown start reads as none). */
  private def rowOf(id: String)(using DbCon): Option[Row] =
    sql"""SELECT p.label, p.kind, ${SqlLiteral(PlaceRepo.startCol)},
                 CASE WHEN upper_inf(p.period) THEN NULL ELSE upper(p.period) - 1 END
          FROM places p WHERE p.id = $id""".query[Row].run().headOption

  private def linked(id: String)(using DbCon): List[String] =
    sql"SELECT finding_id FROM finding_places WHERE place_id = $id".query[String].run().toList

  /** Apply `edit` to place `id` and re-match its findings. `Left` when there is no such place, or a
    * reset finds no override. */
  def set(ds: DataSource, id: String, edit: PlaceOverrideEdit, toleranceKm: Double): Either[ApiError, PlaceOverrideChanged] =
    transact(ds):
      rowOf(id).toRight(ApiError(s"No place '$id'", Some(Write.CodeNotFound))).flatMap { before =>
        val had = linked(id)
        val done: Either[ApiError, Unit] = edit match
          case PlaceOverrideEdit.Reset =>
            // the override goes first: while it exists the trigger would put it back
            val gone = sql"""DELETE FROM place_overrides WHERE place_id = $id
                             RETURNING source_kind, source_period::text, source_start_unknown"""
              .query[(String, String, Boolean)].run().headOption
            gone match
              case None => Left(ApiError(s"'${before.label}' [$id] has no kind or period set by hand", Some(Write.CodeNotFound)))
              case Some((k, p, su)) =>
                val _ = sql"""UPDATE places SET kind = $k, period = $p::int8range, start_unknown = $su
                              WHERE id = $id""".update.run()
                Right(())
          case PlaceOverrideEdit.Put(kind, period, reason) =>
            // a side not given keeps what the override had (open, the first time)
            val hasPeriod = period.isDefined
            val lo        = period.flatMap(_._1)
            val hi        = period.flatMap(_._2)
            val hasLo     = lo.isDefined
            val hasHi     = hi.isDefined
            // a new override remembers what the place says now (the source's); a changed one keeps that
            val _ = sql"""INSERT INTO place_overrides (place_id, kind, period, reason, source_kind, source_period,
                                                       source_start_unknown)
                          SELECT p.id, ${kind}::text,
                                 CASE WHEN $hasPeriod THEN int8range(${lo}::bigint, ${hi}::bigint + 1, '[)') END,
                                 ${reason}::text, p.kind, p.period, p.start_unknown
                          FROM places p WHERE p.id = $id
                          ON CONFLICT (place_id) DO UPDATE SET
                            kind = coalesce(EXCLUDED.kind, place_overrides.kind),
                            period = CASE WHEN NOT $hasPeriod THEN place_overrides.period
                                          ELSE int8range(CASE WHEN $hasLo THEN lower(EXCLUDED.period) ELSE lower(place_overrides.period) END,
                                                         CASE WHEN $hasHi THEN upper(EXCLUDED.period) ELSE upper(place_overrides.period) END,
                                                         '[)') END,
                            reason = coalesce(EXCLUDED.reason, place_overrides.reason),
                            updated_at = now()""".update.run()
            val _ = sql"""UPDATE places p SET kind = coalesce(o.kind, p.kind), period = coalesce(o.period, p.period),
                            start_unknown = CASE WHEN o.period IS NULL THEN p.start_unknown ELSE false END
                          FROM place_overrides o WHERE o.place_id = p.id AND p.id = $id""".update.run()
            Right(())
        done.map { _ =>
          val _     = PlaceRepo.materialisePlaces(List(id), toleranceKm)
          val after = rowOf(id).getOrElse(before)
          val now   = linked(id)
          val lost  = sql"""SELECT count(*)::int FROM unnest(${had}::text[]) AS f(id)
                            WHERE NOT EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id)"""
            .query[Int].run().head
          PlaceOverrideChanged(id, before.label, edit == PlaceOverrideEdit.Reset,
            before.kind, after.kind,
            PlaceOverrides.periodText(before.start, before.end), PlaceOverrides.periodText(after.start, after.end),
            had.size, now.size, lost)
        }
      }

  private def labelOf(id: String)(using DbCon): Option[String] =
    sql"SELECT label FROM places WHERE id = $id".query[String].run().headOption

  private def stillLocated(ids: List[String])(using DbCon): Int =
    sql"""SELECT count(*)::int FROM unnest(${ids.distinct}::text[]) AS f(id)
          WHERE NOT EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id)""".query[Int].run().head

  /** Merge place `from` into `into` ([[PlaceReviewRepo.mergeIn]]: findings, links, children, events,
    * attestations and asset links move, `from` is deleted and stays an old id of `into`, which no seed
    * import re-creates) and match `into`'s findings again. */
  def merge(ds: DataSource, from: String, into: String, toleranceKm: Double): Either[ApiError, PlaceGone] =
    transact(ds):
      (labelOf(from), labelOf(into)) match
        case (None, _) => Left(ApiError(s"No place '$from'", Some(Write.CodeNotFound)))
        case (_, None) => Left(ApiError(s"No place '$into'", Some(Write.CodeNotFound)))
        case _ if from == into => Left(ApiError("a place cannot be merged into itself", Some(Write.CodeInvalid)))
        case (Some(fl), Some(il)) =>
          val had = (linked(from) ++ linked(into)).distinct
          val _   = PlaceReviewRepo.mergeIn(from, into)
          val _   = PlaceRepo.materialisePlaces(List(into), toleranceKm)
          Right(PlaceGone(from, fl, Some(into), Some(il), had.size, linked(into).size, stillLocated(had)))

  /** Remove place `id` when nothing refers to it — no event, attestation, asset link, child place, name
    * added by hand, nor a finding it is the ONLY place of — and remember the removal, so no seed import
    * brings it back (`place_removals`, V080). */
  def remove(ds: DataSource, id: String, reason: Option[String]): Either[ApiError, PlaceGone] =
    transact(ds):
      labelOf(id).toRight(ApiError(s"No place '$id'", Some(Write.CodeNotFound))).flatMap { label =>
        def n(q: Frag) = q.query[Int].run().head
        val refs = List(
          "event(s)"            -> n(sql"SELECT count(*)::int FROM events WHERE place_id = $id"),
          "attestation(s)"      -> n(sql"SELECT count(*)::int FROM attestations WHERE target_type = 'place' AND target_id = $id"),
          "asset link(s)"       -> n(sql"SELECT count(*)::int FROM asset_links WHERE target_type = 'place' AND target_id = $id"),
          "child place(s)"      -> n(sql"SELECT count(*)::int FROM place_relations WHERE parent_id = $id"),
          "name(s) added by hand" -> n(sql"SELECT count(*)::int FROM place_names WHERE place_id = $id AND manual"),
          "finding(s) it is the only place of" -> n(sql"""SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = $id
                AND NOT EXISTS (SELECT 1 FROM finding_places o WHERE o.finding_id = fp.finding_id AND o.place_id <> $id)""")
        ).filter(_._2 > 0)
        if refs.nonEmpty then
          Left(ApiError(s"'$label' [$id] is referred to by ${refs.map((w, c) => s"$c $w").mkString(", ")} — " +
            "merge it into the place it is the same as instead, or move those first", Some(Write.CodeInvalid)))
        else
          val had = linked(id)
          val _ = sql"""INSERT INTO place_removals (id, label, reason) VALUES ($id, $label, ${reason}::text)
                        ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, reason = EXCLUDED.reason,
                          removed_at = now()""".update.run()
          val _ = sql"DELETE FROM places WHERE id = $id".update.run()
          Right(PlaceGone(id, label, None, None, had.size, 0, stillLocated(had)))
      }

  /** The overrides, by place id; `id` narrows to one place. */
  def list(ds: DataSource, id: Option[String]): List[PlaceOverride] =
    connect(ds):
      sql"""SELECT o.place_id, p.label, o.kind,
                   CASE WHEN o.period IS NULL OR lower_inf(o.period) THEN NULL ELSE lower(o.period) END,
                   CASE WHEN o.period IS NULL OR upper_inf(o.period) THEN NULL ELSE upper(o.period) - 1 END,
                   o.period IS NOT NULL, o.reason, o.source_kind,
                   CASE WHEN lower_inf(o.source_period) OR o.source_start_unknown THEN NULL ELSE lower(o.source_period) END,
                   CASE WHEN upper_inf(o.source_period) THEN NULL ELSE upper(o.source_period) - 1 END,
                   to_char(o.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
            FROM place_overrides o JOIN places p ON p.id = o.place_id
            WHERE ${id}::text IS NULL OR o.place_id = ${id}::text
            ORDER BY o.place_id""".query[PlaceOverride].run().toList
