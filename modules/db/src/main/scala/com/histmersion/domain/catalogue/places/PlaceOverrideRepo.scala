package com.histmersion
package domain.catalogue.places

import com.histmersion.api.Problem
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.util.database.RangeSql

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year

/** A person's kind, period and point for a place (V080, V081): `histmersion places set`, MCP `place_override`.
  *
  * The override is a row of `place_overrides`; the DATABASE puts it over whatever kind and period any writer of
  * `places` sends (the `places_override` trigger), so `places seed`, a gazetteer load, a bundle or a lint fix can never
  * undo it, and it remembers what the source says, so a reset restores that without a re-import. Setting or resetting
  * one re-matches that place's findings in the same transaction ([[FindingPlaceRepo.materialisePlaces]]): a kind
  * decides how a place takes findings — a site by the point tolerance, a point-only region only by an explicit match.
  */
private[places] object PlaceOverrideRepo:

  final private case class Row(
      label: Row.Label,
      kind: Row.Kind,
      start: Option[Row.Start],
      end: Option[Row.End],
      lat: Option[Latitude],
      lon: Option[Longitude],
      hasShape: Row.HasShape
  ) derives DbCodec

  object Row:
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Start = Start.T
    object Start extends Newtype[Long, Pure]
    type End = End.T
    object End extends Newtype[Long, Pure]
    type HasShape = HasShape.T
    object HasShape extends Newtype[Boolean, Pure]
  private given DbCodec[Place.Override] = DbCodec.derived

  /** The place's label, kind, STATED period (an unknown start reads as none) and point (when it is a point; `hasShape`
    * when its geometry is a line or an area).
    */
  private def rowOf(id: BPlaceRow.Id)(using DbCon): Option[Row] =
    sql"""SELECT p.label, p.kind, ${SqlLiteral(PlaceRepo.startCol)},
                 ${SqlLiteral(RangeSql.end("p.period"))},
                 CASE WHEN GeometryType(p.geom) = 'POINT' THEN ST_Y(p.geom) END,
                 CASE WHEN GeometryType(p.geom) = 'POINT' THEN ST_X(p.geom) END,
                 coalesce(GeometryType(p.geom) <> 'POINT', false)
          FROM places p WHERE p.id = $id""".query[Row].run().headOption

  private def linked(id: BPlaceRow.Id)(using DbCon): List[String] =
    sql"SELECT finding_id FROM finding_places WHERE place_id = $id".query[String].run().toList

  /** Apply `edit` to place `id` and re-match its findings. `Left` when there is no such place, or a reset finds no
    * override.
    */
  def set(
      id: BPlaceRow.Id,
      edit: Place.OverrideEdit,
      toleranceKm: FindingPlacement.Implementation.ToleranceKm
  )(using DbCon): Either[Api.Error, Place.OverrideChanged] =
    rowOf(id)
      .toRight(Api.Error(api.Problem(s"No place '${id.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
      .flatMap { before =>
        val had = linked(id)
        val done: Either[Api.Error, Unit] = edit match
          case Place.OverrideEdit.Reset =>
            // the override goes first: while it exists the trigger would put it back
            // (a point override's source geometry is put back as it was; `point IS NULL` = the geometry stays)
            val gone = sql"""DELETE FROM place_overrides WHERE place_id = $id
                           RETURNING source_kind, source_period::text, source_start_unknown,
                                     point IS NOT NULL, ST_AsText(source_geom)"""
              .query[(String, String, Boolean, Boolean, Option[String])]
              .run()
              .headOption
            gone match
              case None =>
                Left(
                  Api.Error(
                    api.Problem(s"'${before.label}' [${id.value}] has no kind, period or point set by hand"),
                    Some(Api.Error.Code(Write.CodeNotFound))
                  )
                )
              case Some((k, p, su, hadPoint, srcGeom)) =>
                val _ = sql"""UPDATE places SET kind = $k, period = $p::int8range, start_unknown = $su,
                              geom = CASE WHEN $hadPoint THEN ST_GeomFromText($srcGeom::text, 4326) ELSE geom END
                            WHERE id = $id""".update.run()
                Right(())
          case Place.OverrideEdit.Put(_, _, _, Some(_)) if before.hasShape.value =>
            Left(
              Api.Error(
                api.Problem(
                  s"'${before.label}' [${id.value}] has a shape, not a point — a point set by hand moves only a place " +
                    "known by a point"
                ),
                Some(Api.Error.Code(Write.CodeInvalid))
              )
            )
          case Place.OverrideEdit.Put(kind, period, reason, point) =>
            // a side not given keeps what the override had (open, the first time)
            val hasPeriod = period.isDefined
            val range = period.map((lo, hi) =>
              RangeSql.bounds(lo.map(y => DeepTime.SignedYear(y.value)), hi.map(y => DeepTime.SignedYear(y.value)))
            )
            val hasLo = period.flatMap(_._1).isDefined
            val hasHi = period.flatMap(_._2).isDefined
            val lon = point.map(_.lon)
            val lat = point.map(_.lat)
            // a new override remembers what the place says now (the source's); a changed one keeps that —
            // and the source's geometry is remembered the first time a point is set
            val _ = sql"""INSERT INTO place_overrides (place_id, kind, period, reason, source_kind, source_period,
                                                     source_start_unknown, point, source_geom)
                        SELECT p.id, $kind::text,
                               $range::int8range,
                               $reason::text, p.kind, p.period, p.start_unknown,
                               CASE WHEN $lon::float8 IS NULL THEN NULL
                                    ELSE ST_SetSRID(ST_MakePoint($lon::float8, $lat::float8), 4326) END,
                               CASE WHEN $lon::float8 IS NULL THEN NULL ELSE p.geom END
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
          val _ = FindingPlaceRepo.materialisePlaces(List(id), toleranceKm)
          val after = rowOf(id).getOrElse(before)
          val now = linked(id)
          val lost = sql"""SELECT count(*)::int FROM unnest($had::text[]) AS f(id)
                          WHERE NOT EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id)"""
            .query[Int]
            .run()
            .head
          def period(start: Option[Row.Start], end: Option[Row.End]) =
            Place.Overrides
              .periodText(
                start.map(s => Year(s.value)),
                end.map(e => Year(e.value))
              )
              .value
          Place.OverrideChanged(
            Place.Id(id.value),
            Place.OverrideChanged.Label(before.label.value),
            Place.OverrideChanged.Reset(edit == Place.OverrideEdit.Reset),
            Place.OverrideChanged.KindBefore(before.kind.value),
            Place.OverrideChanged.KindAfter(after.kind.value),
            Place.OverrideChanged.PeriodBefore(period(before.start, before.end)),
            Place.OverrideChanged.PeriodAfter(period(after.start, after.end)),
            Place.OverrideChanged.FindingsBefore(had.size),
            Place.OverrideChanged.FindingsAfter(now.size),
            Place.OverrideChanged.Unlocated(lost),
            before.lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
            before.lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)),
            after.lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
            after.lon.map(l => com.histmersion.api.catalogue.Longitude(l.value))
          )
        }
      }

  private def labelOf(id: BPlaceRow.Id)(using DbCon): Option[BPlaceRow.Label] =
    sql"SELECT label FROM places WHERE id = $id".query[BPlaceRow.Label].run().headOption

  private def stillLocated(ids: List[String])(using DbCon): RowCount =
    sql"""SELECT count(*)::int FROM unnest(${ids.distinct}::text[]) AS f(id)
          WHERE NOT EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id)""".query[RowCount].run().head

  /** Merge place `from` into `into` ([[PlaceReviewRepo.mergeIn]]: findings, links, children, events, attestations and
    * asset links move, `from` is deleted and stays an old id of `into`, which no seed import re-creates) and match
    * `into`'s findings again.
    */
  def merge(from: BPlaceRow.Id, into: BPlaceRow.Id, toleranceKm: FindingPlacement.Implementation.ToleranceKm)(using
      DbCon
  ): Either[Api.Error, Place.Gone] =
    (labelOf(from), labelOf(into)) match
      case (None, _) =>
        Left(Api.Error(api.Problem(s"No place '${from.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
      case (_, None) =>
        Left(Api.Error(api.Problem(s"No place '${into.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
      case _ if from == into =>
        Left(
          Api.Error(api.Problem("a place cannot be merged into itself"), Some(Api.Error.Code(Write.CodeInvalid)))
        )
      case (Some(fl), Some(il)) =>
        val had = (linked(from) ++ linked(into)).distinct
        val _ = PlaceReviewRepo.mergeIn(
          com.histmersion.domain.catalogue.Place.Id.assume(from.value),
          com.histmersion.domain.catalogue.Place.Id.assume(into.value)
        )
        val _ = FindingPlaceRepo.materialisePlaces(List(into), toleranceKm)
        Right(
          Place.Gone(
            Place.Id(from.value),
            Place.Gone.Label(fl.value),
            Some(Place.Gone.Into(into.value)),
            Some(Place.Gone.IntoLabel(il.value)),
            Place.Gone.FindingsBefore(had.size),
            Place.Gone.FindingsAfter(linked(into).size),
            Place.Gone.Unlocated(stillLocated(had).value)
          )
        )

  /** Remove place `id` when nothing refers to it — no event, attestation, asset link, child place, name added by hand,
    * nor a finding it is the ONLY place of — and remember the removal, so no seed import brings it back
    * (`place_removals`, V080).
    */
  def remove(id: BPlaceRow.Id, reason: Option[Problem])(using DbCon): Either[Api.Error, Place.Gone] =
    labelOf(id)
      .toRight(Api.Error(api.Problem(s"No place '${id.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
      .flatMap { label =>
        def n(q: Frag) = q.query[Int].run().head
        val refs = List(
          "event(s)" -> n(sql"SELECT count(*)::int FROM events WHERE place_id = $id"),
          "attestation(s)" -> n(
            sql"SELECT count(*)::int FROM attestations WHERE target_type = 'place' AND target_id = $id"
          ),
          "asset link(s)" -> n(
            sql"SELECT count(*)::int FROM asset_links WHERE target_type = 'place' AND target_id = $id"
          ),
          "child place(s)" -> n(sql"SELECT count(*)::int FROM place_relations WHERE parent_id = $id"),
          "name(s) added by hand" -> n(sql"SELECT count(*)::int FROM place_names WHERE place_id = $id AND manual"),
          "finding(s) it is the only place of" -> n(
            sql"""SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = $id
              AND NOT EXISTS (SELECT 1 FROM finding_places o WHERE o.finding_id = fp.finding_id AND o.place_id <> $id)"""
          )
        ).filter(_._2 > 0)
        if refs.nonEmpty then
          Left(
            Api.Error(
              api.Problem(
                s"'${label.value}' [${id.value}] is referred to by ${refs.map((w, c) => s"$c $w").mkString(", ")} — " +
                  "merge it into the place it is the same as instead, or move those first"
              ),
              Some(Api.Error.Code(Write.CodeInvalid))
            )
          )
        else
          val had = linked(id)
          val _ = sql"""INSERT INTO place_removals (id, label, reason) VALUES ($id, $label, $reason::text)
                      ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, reason = EXCLUDED.reason,
                        removed_at = now()""".update.run()
          val _ = sql"DELETE FROM places WHERE id = $id".update.run()
          Right(
            Place.Gone(
              Place.Id(id.value),
              Place.Gone.Label(label.value),
              None,
              None,
              Place.Gone.FindingsBefore(had.size),
              Place.Gone.FindingsAfter(0),
              Place.Gone.Unlocated(stillLocated(had).value)
            )
          )
      }

  /** The overrides, by place id; `id` narrows to one place. */
  def list(id: Option[BPlaceRow.Id])(using DbCon): List[Place.Override] =
    sql"""SELECT o.place_id, p.label, o.kind,
                 ${SqlLiteral(RangeSql.cols("o.period"))},
                 o.period IS NOT NULL, o.reason, o.source_kind,
                 CASE WHEN o.source_start_unknown THEN NULL ELSE ${SqlLiteral(
        RangeSql.start("o.source_period")
      )} END,
                 ${SqlLiteral(RangeSql.end("o.source_period"))},
                 to_char(o.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                 ST_Y(o.point), ST_X(o.point),
                 CASE WHEN GeometryType(o.source_geom) = 'POINT' THEN ST_Y(o.source_geom) END,
                 CASE WHEN GeometryType(o.source_geom) = 'POINT' THEN ST_X(o.source_geom) END
          FROM place_overrides o JOIN places p ON p.id = o.place_id
          WHERE $id::text IS NULL OR o.place_id = $id::text
          ORDER BY o.place_id""".query[Place.Override].run().toList
