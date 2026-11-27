package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.places.PlaceReview.{Candidate, Evidence, LinkedItem, Proposal}

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Geocode
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.DeepTime
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Unused
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The two gazetteer-cleanup lint rules (2026-10-09): `place-kind` — what a point-only seed region really is
  * ([[PlaceReview]]) — and `place-grid-point` — a better point than an atlas grid square's centre
  * ([[LocationPrecision]]). Both read the store and list; `--fix` applies exactly what they list, compare-and-set, and
  * re-matches the changed places' findings ([[FindingPlaceRepo.materialisePlaces]]).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the place review tables and their row codecs")
private[places] object PlaceReviewRepo:

  final private case class Row(
      id: Evidence.Id,
      label: Evidence.Label,
      kind: Place.Kind.Wire,
      types: Option[String],
      periods: Option[String],
      start: Option[Row.Start],
      end: Option[Row.End]
  ) derives DbCodec

  object Row:
    export DeepTime.{SignedYear as End, SignedYear as Start}

  final private case class ItemRow(
      placeId: Evidence.Id,
      ref: LinkedItem.Ref,
      itemClass: Option[LinkedItem.ItemClass],
      from: Option[ItemRow.From],
      to: Option[ItemRow.To]
  ) derives DbCodec

  object ItemRow:
    export DeepTime.{SignedYear as From, SignedYear as To}

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Candidate] = DbCodec.derived

  private def split(s: Option[String]): List[String] =
    s.toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty)

  /** The places the rule reads: every seed (not imported) place whose point stands for a region, a polity or a people
    * ([[FindingPlaceRepo.explicitOnlySql]] without the grid clause), every seed site with an administrative or people
    * type, and every place Pleiades withdrew, whatever its kind — unless a person set its kind or period (`places set`,
    * V080).
    */
  private val reviewedSql: Frag =
    // a place whose kind or period a person set (V080) is decided: never listed again; the administrative and people
    // types bound as one text[] (the rule's own constants)
    val types = (PlaceReview.PolityTypes ++ PlaceReview.PeopleTypes).toList
    sql"p.import_source IS NULL AND NOT EXISTS (SELECT 1 FROM place_overrides o WHERE o.place_id = p.id) AND ((ST_Dimension(p.geom) = 0 AND p.kind NOT IN ('site', 'feature')) OR (ST_Dimension(p.geom) = 0 AND p.kind = 'site' AND string_to_array(p.properties->>'feature_types', ',') && $types::text[]) OR p.label ~* '^withdrawn')"

  /** The evidence of every reviewed place, and for a withdrawn duplicate the seed places its name resolves to.
    */
  def evidenceIn()(using DbCon): List[(Evidence, List[Candidate])] =
    val rows =
      sql"SELECT p.id, p.label, p.kind, p.properties->>'feature_types', p.properties->>'time_periods', CASE WHEN p.start_unknown THEN NULL ELSE ${RangeSql.start(sql"p.period")} END, ${RangeSql.end(sql"p.period")} FROM places p WHERE $reviewedSql ORDER BY p.id"
        .query[Row]
        .run()
        .toList
    val ids = rows.map(_.id)
    val items = sql"""SELECT place_id, scheme || ':' || external_id, item_class,
                             ${RangeSql.cols(sql"item_period")}
                      FROM place_links WHERE place_id = ANY($ids::text[]) AND item_class IS NOT NULL
                      ORDER BY place_id, scheme, external_id""".query[ItemRow].run().toList.groupBy(_.placeId)
    val descs = sql"""SELECT place_id, text FROM place_descriptions
                      WHERE place_id = ANY($ids::text[]) AND lang = 'en' ORDER BY place_id, import_source, import_ref"""
      .query[(Evidence.Id, Evidence.Descriptions)]
      .run()
      .toList
      .groupMap(_._1)(_._2)
    rows.map { r =>
      val e = Evidence(
        r.id,
        r.label,
        r.kind,
        split(r.types).map(Evidence.FeatureTypes(_)),
        split(r.periods).map(Evidence.TimePeriods(_)),
        r.start,
        r.end,
        items
          .getOrElse(r.id, Nil)
          .map(i =>
            LinkedItem(i.ref, i.itemClass, for a <- i.from; b <- i.to if a <= b; p <- DeepTime.span(a, b) yield p)
          ),
        descs.getOrElse(r.id, Nil)
      )
      val cands = PlaceReview.duplicateOf(r.label).toList.flatMap { name =>
        // the name, or one of the alternatives a Pleiades title joins with '/' ("Peiros/Piros (river)")
        val keys = PlaceReview.duplicateKeys(name)
        sql"""SELECT id, label, kind FROM places
              WHERE import_source IS NULL AND histmersion_place_key(label) = ANY($keys::text[])
                AND label !~* '^withdrawn'
              ORDER BY id""".query[Candidate].run().toList
      }
      (e, cands)
    }

  /** What `place-kind` lists, in place-id order. */
  def proposalsIn()(using DbCon): List[Proposal] =
    evidenceIn().flatMap((e, cs) => PlaceReview.propose(e, cs))

  def proposals()(using DbCon): List[Proposal] = proposalsIn()

  /** Apply the fixable proposals: a kind (and a polity's period) changes only while the place still has the kind the
    * proposal read; a merge moves the duplicate's findings, links, children, events, attestations and asset links to
    * the replacement, deletes it and keeps its id as an alias (V055), so a saved `place=<old id>` still resolves. The
    * changed places' `finding_places` rows are then matched again (a kind decides how a place takes findings), in the
    * same transaction. Returns (reclassified, merged).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def apply(ps: List[Proposal], toleranceKm: PointAreaRepo.Settings.ToleranceKm)(using DbCon): (RowCount, RowCount) =
    val todo = ps.filter(_.fixable)
    if todo.isEmpty then (RowCount(0), RowCount(0))
    else
      val re = todo.collect { case p: Proposal.Reclassify => p }.filter { p =>
        val range = RangeSql.literalOf(p.period)
        sql"""UPDATE places SET kind = ${p.to},
                period = coalesce($range::int8range, period),
                start_unknown = CASE WHEN $range::text IS NULL THEN start_unknown ELSE false END
              WHERE id = ${p.id} AND kind = ${p.from}""".update.run() > 0
      }
      val mg = todo
        .collect { case p: Proposal.Merge => p }
        .filter(p => mergeIn(Place.Id.assume(p.id.value), Place.Id.assume(p.into.value)))
      Unused(
        FindingPlaceRepo.materialisePlaces(
          (re.map(_.id) ++ mg.map(_.into)).map(i => BPlaceRow.Id(i.value)),
          FindingPlacement.Implementation.ToleranceKm(toleranceKm.value)
        ),
        Unused.Why.Reported
      )
      (RowCount(re.size), RowCount(mg.size))

  /** Merge place `from` into `into` in the caller's transaction. `false` when either is gone. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private[places] def mergeIn(from: Place.Id, into: Place.Id)(using DbCon): Boolean =
    val both = sql"SELECT count(*)::int FROM places WHERE id = $from OR id = $into".query[Int].run().head
    if from == into || both < 2 then false
    else
      Unused(
        sql"""INSERT INTO finding_places (finding_id, place_id)
                    SELECT finding_id, $into FROM finding_places WHERE place_id = $from
                    ON CONFLICT DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
      Unused(
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref,
                                             import_version, item_class, item_geom, item_period)
                    SELECT $into, scheme, external_id, relation, import_source, import_ref, import_version,
                           item_class, item_geom, item_period
                    FROM place_links WHERE place_id = $from
                    ON CONFLICT DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
      // the duplicate's children become the replacement's; its own parents and names are its own
      Unused(
        sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
                    SELECT child_id, $into, kind, period FROM place_relations
                    WHERE parent_id = $from AND child_id <> $into
                    ON CONFLICT DO NOTHING""".update.run(),
        Unused.Why.Idempotent
      )
      Unused(sql"UPDATE events SET place_id = $into WHERE place_id = $from".update.run(), Unused.Why.Idempotent)
      Unused(
        sql"UPDATE attestations SET target_id = $into WHERE target_type = 'place' AND target_id = $from".update.run(),
        Unused.Why.Idempotent
      )
      Unused(
        sql"UPDATE asset_links SET target_id = $into WHERE target_type = 'place' AND target_id = $from".update.run(),
        Unused.Why.Idempotent
      )
      Unused(sql"UPDATE place_aliases SET place_id = $into WHERE place_id = $from".update.run(), Unused.Why.Idempotent)
      Unused(sql"DELETE FROM places WHERE id = $from".update.run(), Unused.Why.Held)
      val _ = sql"""INSERT INTO place_aliases (alias, place_id) VALUES ($from, $into)
                    ON CONFLICT (alias) DO UPDATE SET place_id = EXCLUDED.place_id""".update.run()
      true

  // ── place-grid-point ─────────────────────────────────────────────────────────────────────────

  /** A grid-cell place and the better point one of its linked records gives. */
  final case class Relocation(
      id: BPlaceRow.Id,
      label: Relocation.Label,
      fromLon: Relocation.FromLon,
      fromLat: Relocation.FromLat,
      ref: Relocation.Ref,
      lon: Longitude,
      lat: Latitude
  ) derives DbCodec:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def km: Geocode.DistanceKm =
      Geocode.distanceKm(
        com.histmersion.api.catalogue.Longitude(fromLon.value),
        com.histmersion.api.catalogue.Latitude(fromLat.value),
        com.histmersion.api.catalogue.Longitude(lon.value),
        com.histmersion.api.catalogue.Latitude(lat.value)
      )

    /** One line: id, label, the grid point, the better one and how far. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def describe: Relocation.Description =
      Relocation.Description(s"${id.value} ${detail.value}")

    /** The line without the id — a lint item, which carries the id beside it. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def detail: Relocation.Detail =
      Relocation.Detail(
        String.format(
          java.util.Locale.ROOT,
          "%s: location approximate (atlas grid square) at %.2f N %.2f E → %s's point %.4f N %.4f E, %.0f km",
          label.value,
          fromLat.value,
          fromLon.value,
          ref.value,
          lat.value,
          lon.value,
          km.value
        )
      )

  object Relocation:
    type Description = Description.T
    object Description extends Newtype[String, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]

    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type FromLon = FromLon.T
    object FromLon extends Newtype[Double, Pure]
    type FromLat = FromLat.T
    object FromLat extends Newtype[Double, Pure]
    type Ref = Ref.T
    object Ref extends Newtype[String, Pure]

  /** Every grid-cell place a linked record (Wikidata's P625) puts somewhere that is no grid centre itself; one per
    * place, the nearest such point. A place whose point a person set (`places set --point`, V081) is decided: never
    * listed.
    */
  def relocationsIn()(using DbCon): List[Relocation] =
    sql"""SELECT DISTINCT ON (p.id) p.id, p.label, ST_X(p.geom), ST_Y(p.geom),
                 l.scheme || ':' || l.external_id, ST_X(l.item_geom), ST_Y(l.item_geom)
          FROM places p JOIN place_links l ON l.place_id = p.id
          WHERE p.location_precision = 'grid-cell' AND l.item_geom IS NOT NULL
            AND NOT histmersion_grid_centre(ST_X(l.item_geom), ST_Y(l.item_geom))
            AND NOT ST_Equals(l.item_geom, p.geom)
            AND NOT EXISTS (SELECT 1 FROM place_overrides o WHERE o.place_id = p.id AND o.point IS NOT NULL)
          ORDER BY p.id, ST_Distance(p.geom, l.item_geom), l.scheme, l.external_id""".query[Relocation].run().toList

  def relocations()(using DbCon): List[Relocation] = relocationsIn()

  /** Give each listed place the linked record's point — only while it still has the grid point the list was made from —
    * and match the moved places' findings again, in the same transaction. Returns the places moved.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def relocate(rs: List[Relocation], toleranceKm: PointAreaRepo.Settings.ToleranceKm)(using DbCon): RowCount =
    if rs.isEmpty then RowCount(0)
    else
      val moved = rs.filter { r =>
        sql"""UPDATE places SET geom = ST_SetSRID(ST_MakePoint(${r.lon}, ${r.lat}), 4326)
              WHERE id = ${r.id} AND GeometryType(geom) = 'POINT'
                AND abs(ST_X(geom) - ${r.fromLon}) < 1e-7 AND abs(ST_Y(geom) - ${r.fromLat}) < 1e-7""".update
          .run() > 0
      }
      Unused(
        FindingPlaceRepo
          .materialisePlaces(moved.map(_.id), FindingPlacement.Implementation.ToleranceKm(toleranceKm.value)),
        Unused.Why.Reported
      )
      RowCount(moved.size)
