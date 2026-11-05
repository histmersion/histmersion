package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.places.PointAreas.{Dating, Group, Listed}

import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.{Gazetteer, Geocode}
import com.histmersion.api.checks.Lint

/** `lint --rule place-imported-point-area` (2026-10-09): the GeoNames and Wikidata places of an area kind stored with
  * only a point — what the importer no longer creates ([[Gazetteer.dropsPointArea]]). The listing reads; [[remove]]
  * deletes them with every row that hangs off them, in bounded transactions, and matches the findings of the affected
  * sources to places again.
  *
  * A place an attestation or an asset link names, or one a person curated (`place_overrides`, `places set`), is listed
  * but never removed: that is a person's evidence or decision, and it is theirs to move or reset first.
  */
private[places] object PointAreaRepo:

  /** `kinds` is `places.sources.point-area-kinds`; `batch` places per transaction; `budgetMs` the time one call may
    * spend before it returns what is left (an HTTP request must end well inside netty's idle timeout); `toleranceKm`
    * the point tolerance the re-match uses.
    */
  final case class Settings(kinds: List[Settings.Kinds], batch: Settings.Batch, budgetMs: Settings.BudgetMs, toleranceKm: Settings.ToleranceKm)

  object Settings:
    type Kinds = Kinds.T
    object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Batch = Batch.T
    object Batch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetMs = BudgetMs.T
    object BudgetMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToleranceKm = ToleranceKm.T
    object ToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What one [[remove]] call did. */
  final case class Removal(removed: Removal.Removed, remaining: Removal.Remaining, findings: Removal.Findings, sources: Removal.Sources):
    /** The line `lint --fix` reports it with. */
    def line: String = PointAreas.fixed(removed, remaining, findings, sources)

  object Removal:
    type Removed = Removed.T
    object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Remaining = Remaining.T
    object Remaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sources = Sources.T
    object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val sources: List[String] = Gazetteer.PointAreaSources

  final private case class Row(
      id: Row.Id,
      label: Row.Label,
      source: Row.Source,
      kind: Row.Kind,
      startUnknown: Row.StartUnknown,
      lowerInf: Row.LowerInf,
      upperInf: Row.UpperInf,
      findings: Row.Findings,
      alone: Row.Alone,
      pinned: Row.Pinned
  ) derives DbCodec:
    def listed: Listed =
      Listed(id, label, source, kind, Dating.of(startUnknown, lowerInf, upperInf), findings, alone, pinned)

  object Row:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartUnknown = StartUnknown.T
    object StartUnknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LowerInf = LowerInf.T
    object LowerInf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpperInf = UpperInf.T
    object UpperInf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Alone = Alone.T
    object Alone extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pinned = Pinned.T
    object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The counts by source, kind and dating. */
  def groupsIn(kinds: List[String])(using DbCon): List[Group] =
    sql"""SELECT p.import_source, p.kind, coalesce(p.start_unknown, false),
                 coalesce(lower_inf(p.period), true), coalesce(upper_inf(p.period), true), count(*)::int
          FROM places p
          WHERE p.import_source = ANY($sources::text[]) AND p.kind = ANY($kinds::text[])
            AND (p.geom IS NULL OR ST_Dimension(p.geom) = 0)
          GROUP BY 1, 2, 3, 4, 5"""
      .query[(String, String, Boolean, Boolean, Boolean, Int)]
      .run()
      .toList
      .groupMapReduce((src, kind, su, li, ui, _) => (src, kind, Dating.of(su, li, ui)))(_._6)(_ + _)
      .toList
      .map { case ((src, kind, d), n) => Group(src, kind, d, n) }

  /** Every such place that holds a finding or that evidence names. `alone`: the findings it holds that hold no OTHER
    * place that would survive the removal.
    */
  def holdersIn(kinds: List[String])(using DbCon): List[Listed] =
    sql"""WITH a AS MATERIALIZED (
            SELECT p.id, p.label, p.import_source, p.kind, coalesce(p.start_unknown, false) AS su,
                   coalesce(lower_inf(p.period), true) AS li, coalesce(upper_inf(p.period), true) AS ui
            FROM places p
            WHERE p.import_source = ANY($sources::text[]) AND p.kind = ANY($kinds::text[])
              AND (p.geom IS NULL OR ST_Dimension(p.geom) = 0)),
          held AS (
            SELECT fp.place_id, count(*)::int AS findings,
                   count(*) FILTER (WHERE NOT EXISTS (
                     SELECT 1 FROM finding_places o JOIN places q ON q.id = o.place_id
                     WHERE o.finding_id = fp.finding_id AND o.place_id <> fp.place_id
                       AND NOT (coalesce(q.import_source, '') = ANY($sources::text[]) AND q.kind = ANY($kinds::text[])
                                AND (q.geom IS NULL OR ST_Dimension(q.geom) = 0))))::int AS alone
            FROM finding_places fp JOIN a ON a.id = fp.place_id GROUP BY fp.place_id),
          pinned AS (
            SELECT target_id AS place_id, count(*)::int AS n FROM (
              SELECT target_id FROM attestations WHERE target_type = 'place'
              UNION ALL SELECT target_id FROM asset_links WHERE target_type = 'place'
              UNION ALL SELECT place_id FROM place_overrides) t
            JOIN a ON a.id = t.target_id GROUP BY target_id)
          SELECT a.id, a.label, a.import_source, a.kind, a.su, a.li, a.ui,
                 coalesce(h.findings, 0), coalesce(h.alone, 0), coalesce(n.n, 0)
          FROM a LEFT JOIN held h ON h.place_id = a.id LEFT JOIN pinned n ON n.place_id = a.id
          WHERE h.place_id IS NOT NULL OR n.place_id IS NOT NULL
          ORDER BY a.id""".query[Row].run().toList.map(_.listed)

  /** The first `n` such places by id — the sample the listing ends with. */
  def sampleIn(kinds: List[String], n: Int)(using DbCon): List[Listed] =
    sql"""SELECT p.id, p.label, p.import_source, p.kind, coalesce(p.start_unknown, false),
                 coalesce(lower_inf(p.period), true), coalesce(upper_inf(p.period), true), 0, 0, 0
          FROM places p
          WHERE p.import_source = ANY($sources::text[]) AND p.kind = ANY($kinds::text[])
            AND (p.geom IS NULL OR ST_Dimension(p.geom) = 0)
          ORDER BY p.id LIMIT ${math.max(0, n)}""".query[Row].run().toList.map(_.listed)

  /** The rule's report. */
  def reportIn(rule: Lint.Rule, kinds: List[String], maxItems: Int)(using DbCon): Lint.LintRuleReport =
    if kinds.isEmpty then Lint.LintRuleReport(rule.name, rule.description, 0, Nil)
    else PointAreas.report(rule, groupsIn(kinds), holdersIn(kinds), sampleIn(kinds, maxItems), maxItems)

  /** The ids `--fix` may remove: every listed place no evidence names. */
  private[places] def removableIn(kinds: List[String])(using DbCon): List[String] =
    sql"""SELECT p.id FROM places p
          WHERE p.import_source = ANY($sources::text[]) AND p.kind = ANY($kinds::text[])
            AND (p.geom IS NULL OR ST_Dimension(p.geom) = 0)
            AND NOT EXISTS (SELECT 1 FROM attestations t WHERE t.target_type = 'place' AND t.target_id = p.id)
            AND NOT EXISTS (SELECT 1 FROM asset_links t WHERE t.target_type = 'place' AND t.target_id = p.id)
            AND NOT EXISTS (SELECT 1 FROM place_overrides o WHERE o.place_id = p.id)
          ORDER BY p.id""".query[String].run().toList

  /** Delete these places and everything that hangs off them, set-wise (one statement per table, so the foreign keys'
    * per-row cascades find nothing left to do): finding matches, names, links, descriptions, types, footprints,
    * relations on either side, aliases, overrides (V080: the callers never pass an overridden place, the statement
    * keeps the cascade idle); an event naming one keeps its row and loses its place (`SET NULL`). Returns the places
    * deleted. Shared with the importer's prune ([[GazetteerImport.Session.prune]]).
    */
  private[places] def deletePlacesIn(ids: List[String])(using DbCon): Int =
    if ids.isEmpty then 0
    else
      val _ = sql"DELETE FROM finding_places WHERE place_id = ANY($ids::text[])".update.run()
      val _ = sql"DELETE FROM place_names WHERE place_id = ANY($ids::text[])".update.run()
      val _ = sql"DELETE FROM place_links WHERE place_id = ANY($ids::text[])".update.run()
      val _ = sql"DELETE FROM place_descriptions WHERE place_id = ANY($ids::text[])".update.run()
      val _ = sql"DELETE FROM place_types WHERE place_id = ANY($ids::text[])".update.run()
      val _ = sql"DELETE FROM place_footprints WHERE place_id = ANY($ids::text[])".update.run()
      val _ =
        sql"DELETE FROM place_relations WHERE child_id = ANY($ids::text[]) OR parent_id = ANY($ids::text[])".update
          .run()
      val _ = sql"DELETE FROM place_aliases WHERE place_id = ANY($ids::text[])".update.run()
      val _ = sql"DELETE FROM place_overrides WHERE place_id = ANY($ids::text[])".update.run()
      sql"DELETE FROM places WHERE id = ANY($ids::text[])".update.run()

  /** The ids of `source`'s places the importer no longer keeps: an area kind, only a point, and no evidence naming it
    * ([[GazetteerImport.Session.prune]]).
    */
  private[places] def ofSourceIn(source: String, kinds: List[String])(using DbCon): List[String] =
    sql"""SELECT p.id FROM places p
          WHERE p.import_source = $source AND p.kind = ANY($kinds::text[])
            AND (p.geom IS NULL OR ST_Dimension(p.geom) = 0)
            AND NOT EXISTS (SELECT 1 FROM attestations t WHERE t.target_type = 'place' AND t.target_id = p.id)
            AND NOT EXISTS (SELECT 1 FROM asset_links t WHERE t.target_type = 'place' AND t.target_id = p.id)
            AND NOT EXISTS (SELECT 1 FROM place_overrides o WHERE o.place_id = p.id)
          ORDER BY p.id""".query[String].run().toList

  /** The name keys by which a removal can change a finding's match, → the citekeys whose findings carry one. A
    * finding's place name matters only when one of its readings ([[Geocode.attempts]], as `PlaceRepo.byResolution`
    * reads them) is the name key of an explicit-only seed place — a point-only seed region that takes findings by name:
    * the geocoder may have chosen an imported place for ANY of its readings over it ("Eastern Bohemia", a GeoNames
    * point region, before "Bohemia"), and chooses the seed place once that is gone. Every other match an imported point
    * has is a finding ON it, which the place's own rows say.
    */
  private[places] def nameKeysIn()(using DbCon): Map[String, Set[String]] =
    val findings =
      sql"""SELECT DISTINCT citekey, btrim(region_name) FROM findings WHERE btrim(coalesce(region_name, '')) <> ''"""
        .query[(String, String)]
        .run()
        .toList
        .map((ck, name) => ck -> Geocode.attempts(name).flatMap(a => a.key :: a.within.map(Geocode.key).toList).toSet)
    val keys = findings.flatMap(_._2).distinct
    if keys.isEmpty then Map.empty
    else
      val seedOnly = s"NOT ${PlaceRepo.importedSql("p")} AND ${FindingPlaceRepo.explicitOnlySql("p")}"
      val hits =
        sql"""SELECT DISTINCT k FROM unnest($keys::text[]) AS k
              WHERE EXISTS (SELECT 1 FROM places p WHERE histmersion_place_key(p.label) = k AND ${SqlLiteral(seedOnly)})
                 OR EXISTS (SELECT 1 FROM place_names n JOIN places p ON p.id = n.place_id
                            WHERE histmersion_place_key(n.name) = k AND ${SqlLiteral(seedOnly)})"""
          .query[String]
          .run()
          .toSet
      findings
        .filter(_._2.exists(hits.contains))
        .flatMap((ck, ks) => ks.map(_ -> ck))
        .groupMap(_._1)(_._2)
        .view
        .mapValues(_.toSet)
        .toMap

  /** One batch, in the caller's transaction: (places removed, findings re-matched, their citekeys). The findings
    * matched again are those the places held, and those of every source with a finding whose place name reads as one of
    * the places' names AND as an explicit-only seed place's ([[nameKeysIn]]): the geocoder may have chosen the removed
    * place for it, and now chooses the seed region, which takes the finding by its name ("Thrace").
    */
  private[places] def removeIn(
      ids0: List[String],
      kinds: List[String],
      toleranceKm: Double,
      named: Map[String, Set[String]]
  )(using
      DbCon
  ): (Int, Int, Set[String]) =
    val ids = sql"""SELECT p.id FROM places p
                    WHERE p.id = ANY($ids0::text[])
                      AND p.import_source = ANY($sources::text[]) AND p.kind = ANY($kinds::text[])
                      AND (p.geom IS NULL OR ST_Dimension(p.geom) = 0)
                      AND NOT EXISTS (SELECT 1 FROM attestations t WHERE t.target_type = 'place' AND t.target_id = p.id)
                      AND NOT EXISTS (SELECT 1 FROM asset_links t WHERE t.target_type = 'place' AND t.target_id = p.id)
                      AND NOT EXISTS (SELECT 1 FROM place_overrides o WHERE o.place_id = p.id)
                    FOR UPDATE OF p""".query[String].run().toList
    if ids.isEmpty then (0, 0, Set.empty)
    else
      val held = sql"""SELECT DISTINCT f.id, f.citekey FROM finding_places fp JOIN findings f ON f.id = fp.finding_id
                       WHERE fp.place_id = ANY($ids::text[])""".query[(String, String)].run().toList
      val byName =
        if named.isEmpty then Set.empty[String]
        else
          sql"""SELECT histmersion_place_key(label) FROM places WHERE id = ANY($ids::text[])
                  UNION SELECT histmersion_place_key(name) FROM place_names WHERE place_id = ANY($ids::text[])"""
            .query[String]
            .run()
            .toSet
            .flatMap(k => named.getOrElse(k, Set.empty))
      val n = deletePlacesIn(ids)
      val cks = held.map(_._2).toSet ++ byName
      cks.toList.sorted.foreach(ck => FindingPlaceRepo.materialiseFor(ck, toleranceKm))
      (n, held.size, cks)
