package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** A person's kind, period and point for a place (V080, V081): `histmersion places set`, MCP `place_override`.
  *
  * The override is a row of `place_overrides`; the DATABASE puts it over whatever kind and period any
  * writer of `places` sends (the `places_override` trigger), so `places seed`, a gazetteer load, a bundle
  * or a lint fix can never undo it, and it remembers what the source says, so a reset restores that
  * without a re-import. Setting or resetting one re-matches that place's findings in the same
  * transaction ([[PlaceRepo.materialisePlaces]]): a kind decides how a place takes findings — a site by
  * the point tolerance, a point-only region only by an explicit match. */
object PlaceOverrideRepo:

  private final case class Row(label: String, kind: String, start: Option[Long], end: Option[Long],
      lat: Option[Double], lon: Option[Double], hasShape: Boolean)
  private given DbCodec[Row] = DbCodec.derived
  private given DbCodec[PlaceOverride] = DbCodec.derived

  /** The place's label, kind, STATED period (an unknown start reads as none) and point (when it is a
    * point; `hasShape` when its geometry is a line or an area). */
  private def rowOf(id: String)(using DbCon): Option[Row] =
    sql"""SELECT p.label, p.kind, ${SqlLiteral(PlaceRepo.startCol)},
                 ${SqlLiteral(PeriodSql.end("p.period"))},
                 CASE WHEN GeometryType(p.geom) = 'POINT' THEN ST_Y(p.geom) END,
                 CASE WHEN GeometryType(p.geom) = 'POINT' THEN ST_X(p.geom) END,
                 coalesce(GeometryType(p.geom) <> 'POINT', false)
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
            // (a point override's source geometry is put back as it was; `point IS NULL` = the geometry stays)
            val gone = sql"""DELETE FROM place_overrides WHERE place_id = $id
                             RETURNING source_kind, source_period::text, source_start_unknown,
                                       point IS NOT NULL, ST_AsText(source_geom)"""
              .query[(String, String, Boolean, Boolean, Option[String])].run().headOption
            gone match
              case None => Left(ApiError(s"'${before.label}' [$id] has no kind, period or point set by hand", Some(Write.CodeNotFound)))
              case Some((k, p, su, hadPoint, srcGeom)) =>
                val _ = sql"""UPDATE places SET kind = $k, period = $p::int8range, start_unknown = $su,
                                geom = CASE WHEN $hadPoint THEN ST_GeomFromText(${srcGeom}::text, 4326) ELSE geom END
                              WHERE id = $id""".update.run()
                Right(())
          case PlaceOverrideEdit.Put(_, _, _, Some(_)) if before.hasShape =>
            Left(ApiError(s"'${before.label}' [$id] has a shape, not a point — a point set by hand moves only a place " +
              "known by a point", Some(Write.CodeInvalid)))
          case PlaceOverrideEdit.Put(kind, period, reason, point) =>
            // a side not given keeps what the override had (open, the first time)
            val hasPeriod = period.isDefined
            val range     = period.map((lo, hi) => PeriodSql.bounds(lo, hi))
            val hasLo     = period.flatMap(_._1).isDefined
            val hasHi     = period.flatMap(_._2).isDefined
            val lon       = point.map(_.lon)
            val lat       = point.map(_.lat)
            // a new override remembers what the place says now (the source's); a changed one keeps that —
            // and the source's geometry is remembered the first time a point is set
            val _ = sql"""INSERT INTO place_overrides (place_id, kind, period, reason, source_kind, source_period,
                                                       source_start_unknown, point, source_geom)
                          SELECT p.id, ${kind}::text,
                                 ${range}::int8range,
                                 ${reason}::text, p.kind, p.period, p.start_unknown,
                                 CASE WHEN ${lon}::float8 IS NULL THEN NULL
                                      ELSE ST_SetSRID(ST_MakePoint(${lon}::float8, ${lat}::float8), 4326) END,
                                 CASE WHEN ${lon}::float8 IS NULL THEN NULL ELSE p.geom END
                          FROM places p WHERE p.id = $id
                          ON CONFLICT (place_id) DO UPDATE SET
                            kind = coalesce(EXCLUDED.kind, place_overrides.kind),
                            period = CASE WHEN NOT $hasPeriod THEN place_overrides.period
                                          ELSE int8range(CASE WHEN $hasLo THEN lower(EXCLUDED.period) ELSE lower(place_overrides.period) END,
                                                         CASE WHEN $hasHi THEN upper(EXCLUDED.period) ELSE upper(place_overrides.period) END,
                                                         '[)') END,
                            reason = coalesce(EXCLUDED.reason, place_overrides.reason),
                            point = coalesce(EXCLUDED.point, place_overrides.point),
                            source_geom = CASE WHEN place_overrides.point IS NULL THEN EXCLUDED.source_geom
                                               ELSE place_overrides.source_geom END,
                            updated_at = now()""".update.run()
            // a point set by hand replaces a point only: a shape a source gave since then stays
            val _ = sql"""UPDATE places p SET kind = coalesce(o.kind, p.kind), period = coalesce(o.period, p.period),
                            start_unknown = CASE WHEN o.period IS NULL THEN p.start_unknown ELSE false END,
                            geom = CASE WHEN o.point IS NOT NULL AND (p.geom IS NULL OR GeometryType(p.geom) = 'POINT')
                                        THEN o.point ELSE p.geom END
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
            had.size, now.size, lost, before.lat, before.lon, after.lat, after.lon)
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
                   ${SqlLiteral(PeriodSql.cols("o.period"))},
                   o.period IS NOT NULL, o.reason, o.source_kind,
                   CASE WHEN o.source_start_unknown THEN NULL ELSE ${SqlLiteral(PeriodSql.start("o.source_period"))} END,
                   ${SqlLiteral(PeriodSql.end("o.source_period"))},
                   to_char(o.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                   ST_Y(o.point), ST_X(o.point),
                   CASE WHEN GeometryType(o.source_geom) = 'POINT' THEN ST_Y(o.source_geom) END,
                   CASE WHEN GeometryType(o.source_geom) = 'POINT' THEN ST_X(o.source_geom) END
            FROM place_overrides o JOIN places p ON p.id = o.place_id
            WHERE ${id}::text IS NULL OR o.place_id = ${id}::text
            ORDER BY o.place_id""".query[PlaceOverride].run().toList
