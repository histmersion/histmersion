package com.histmersion.domain.catalogue.places

import com.histmersion.domain.MergeSettings
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** The places as a bundle carries them (docs/research-merge-import.md §4, §7.3): this collection's own places with
  * their names, parents, links and types, their old ids and the site types refined from outside — read out in a
  * deterministic order, and written in by an import.
  */
private[places] object PlaceBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  /** The places of this collection's own (`dataset = 'local'`, V052) in scope — or, with `alsoIds`, any place with one
    * of those ids as well (what a merge compares the bundle's against).
    */
  type PlacesAlsoIds = PlacesAlsoIds.T
  object PlacesAlsoIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnersResultPart1 = OwnersResultPart1.T
  object OwnersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnersQ = OwnersQ.T
  object OwnersQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnersLabels = OwnersLabels.T
  object OwnersLabels extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesNamedResultPart1 = PlacesNamedResultPart1.T
  object PlacesNamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesNamedIds = PlacesNamedIds.T
  object PlacesNamedIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OldIdsNamedResultPart1 = OldIdsNamedResultPart1.T
  object OldIdsNamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OldIdsNamedIds = OldIdsNamedIds.T
  object OldIdsNamedIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PresentResult = PresentResult.T
  object PresentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PresentIds = PresentIds.T
  object PresentIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchPlacesResultPart2 = MatchPlacesResultPart2.T
  object MatchPlacesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceHereResultArg1 = PlaceHereResultArg1.T
  object PlaceHereResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceHereResultResult = PlaceHereResultResult.T
  object PlaceHereResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def places(scope: BundleScope, alsoIds: List[com.histmersion.domain.catalogue.places.PlaceBundleRepo.PlacesAlsoIds] = Nil)(using DbCon): Vector[Bundle.Place] =
    val own = Sql.raw("p.dataset = 'local' AND ") ++ where(scope, "p.uid", "p.updated_at")
    val sel = if alsoIds.isEmpty then own
    else Sql.raw("((") ++ own ++ Sql.of(") OR p.id = ANY(?::text[]))", Param.A(alsoIds.distinct))
    val rows = (Sql.raw(s"""SELECT p.uid, p.origin, p.id, p.label, p.kind, encode(ST_AsEWKB(p.geom), 'hex'),
                       ${rangeCols("p.period")}, p.source, p.properties::text, ${ts("p.updated_at")}
                FROM places p WHERE """) ++ sel ++ " ORDER BY p.uid").frag.query[BPlaceRow].run()
    val ids = rows.map(_.id).toList
    if ids.isEmpty then Vector.empty
    else
      val names = sql"""SELECT place_id, name, lang, ${SqlLiteral(rangeCols("period"))} FROM place_names
                        WHERE place_id = ANY($ids::text[]) AND import_source = '' ORDER BY place_id, name COLLATE "C", lang COLLATE "C""""
        .query[BPlaceNameRow]
        .run()
        .groupBy(_.placeId)
      val rels = sql"""SELECT child_id, parent_id, kind, ${SqlLiteral(rangeCols("period"))} FROM place_relations
                       WHERE child_id = ANY($ids::text[]) ORDER BY child_id, parent_id COLLATE "C", kind COLLATE "C""""
        .query[BPlaceRelRow]
        .run()
        .groupBy(_.childId)
      val links =
        sql"""SELECT place_id, scheme, external_id, relation FROM place_links WHERE place_id = ANY($ids::text[])
                        ORDER BY place_id, scheme COLLATE "C", external_id COLLATE "C""""
          .query[(String, String, String, String)]
          .run()
          .groupBy(_._1)
      val types = sql"""SELECT place_id, type_label, ${SqlLiteral(rangeCols("period"))}, citekey
                        FROM place_types WHERE place_id = ANY($ids::text[]) ORDER BY place_id, type_label COLLATE "C""""
        .query[BPlaceTypeRow]
        .run()
        .groupBy(_.placeId)
      rows.map { r =>
        Bundle.Place(
          r.uid,
          r.origin,
          r.id,
          r.label,
          r.kind,
          r.geom,
          range(r.pNull, r.pLo, r.pHi),
          r.source,
          r.properties.flatMap(Jsonb.read[io.circe.Json]),
          names
            .getOrElse(r.id, Vector.empty)
            .toList
            .map(n => Bundle.PlaceName(n.name, Option(n.lang).filter(_.nonEmpty), range(n.pNull, n.pLo, n.pHi))),
          rels
            .getOrElse(r.id, Vector.empty)
            .toList
            .map(x => Bundle.PlaceRelation(x.parentId, x.kind, range(x.pNull, x.pLo, x.pHi))),
          links.getOrElse(r.id, Vector.empty).toList.map((_, sc, ex, rel) => Bundle.PlaceLink(sc, ex, rel)),
          types
            .getOrElse(r.id, Vector.empty)
            .toList
            .map(t => Bundle.PlaceType(t.typeLabel, range(t.pNull, t.pLo, t.pHi), t.citekey)),
          r.updatedAt
        )
      }

  /** Old place ids (V055) of the places a bundle carries — this collection's own (as [[places]]): a gazetteer seed
    * place travels as the reference fingerprint, not as a row.
    */
  def placeAliases(scope: BundleScope)(using DbCon): Vector[Bundle.PlaceAlias] =
    (Sql.raw(s"""SELECT x.alias, p.uid, ${ts("x.created_at")}
                FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE p.dataset = 'local' AND """) ++
      where(scope, "p.uid", "x.created_at") ++ """ ORDER BY x.alias COLLATE "C"""").frag
      .query[(String, UUID, String)]
      .run()
      .map(Bundle.PlaceAlias.apply)

  /** The site types refined from outside (`places.site_type`, V085) — of ANY place, the gazetteer's seeds included —
    * and the places asked about with no answer (so the other side does not ask again).
    */
  def siteTypes(scope: BundleScope)(using DbCon): Vector[Bundle.SiteType] =
    (Sql.raw(s"""SELECT p.id, p.site_type, ${ts("p.site_type_checked_at")} FROM places p
                WHERE (p.site_type IS NOT NULL OR p.site_type_checked_at IS NOT NULL) AND """) ++
      where(scope, "p.uid", "p.site_type_checked_at") ++ """ ORDER BY p.id COLLATE "C"""").frag
      .query[(String, Option[String], Option[String])]
      .run()
      .map(Bundle.SiteType.apply)

  // ── what a merge plan compares ───────────────────────────────────────────────────────────────

  /** Who has each of `labels` here, by the query's (label, uid) rows. */
  private def owners(q: com.histmersion.domain.catalogue.places.PlaceBundleRepo.OwnersQ, labels: List[com.histmersion.domain.catalogue.places.PlaceBundleRepo.OwnersLabels])(using DbCon): Map[com.histmersion.domain.catalogue.places.PlaceBundleRepo.OwnersResultPart1, UUID] =
    if labels.isEmpty then Map.empty
    else Sql.of(q, Param.A(labels.distinct)).frag.query[(String, UUID)].run().toMap

  /** The places here among `ids` → their uids. */
  def placesNamed(ids: List[com.histmersion.domain.catalogue.places.PlaceBundleRepo.PlacesNamedIds])(using DbCon): Map[com.histmersion.domain.catalogue.places.PlaceBundleRepo.PlacesNamedResultPart1, UUID] =
    owners("SELECT id, uid FROM places WHERE id = ANY(?::text[])", ids)

  /** The old place ids here among `ids` → the uid of the place each names. */
  def oldIdsNamed(ids: List[com.histmersion.domain.catalogue.places.PlaceBundleRepo.OldIdsNamedIds])(using DbCon): Map[com.histmersion.domain.catalogue.places.PlaceBundleRepo.OldIdsNamedResultPart1, UUID] =
    owners(
      "SELECT x.alias, p.uid FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE x.alias = ANY(?::text[])",
      ids
    )

  /** Which of `ids` name a place here. */
  def present(ids: List[com.histmersion.domain.catalogue.places.PlaceBundleRepo.PresentIds])(using DbCon): Set[com.histmersion.domain.catalogue.places.PlaceBundleRepo.PresentResult] =
    if ids.isEmpty then Set.empty
    else sql"SELECT id FROM places WHERE id = ANY(${ids.distinct}::text[])".query[String].run().toSet

  /** A bundle place with no id here that names a place here: the same label (reduced as the gazetteer reduces it: case,
    * filler words, punctuation) and kind, and — when both have a geometry — points within `merge.place-within-km` or
    * shapes overlapping by `merge.place-overlap-at-least` (the intersection over the union); two places without a
    * geometry match on the label alone. The nearest (most overlapping) place wins; a local place is preferred to a seed
    * one.
    */
  def matchPlaces(ps: List[Bundle.Place], cfg: MergeSettings)(using DbCon): Map[UUID, com.histmersion.domain.catalogue.places.PlaceBundleRepo.MatchPlacesResultPart2] =
    val norm = PlaceRepo.normLabelSql("p.label")
    ps.flatMap { b =>
      val within = cfg.placeWithinKm * 1000.0
      Sql
        .of(
          s"""WITH b AS (SELECT ST_GeomFromEWKB(decode(nullif(?, ''), 'hex')) AS g)
            SELECT p.id FROM places p, b
            WHERE $norm = ${PlaceRepo.normLabelSql("?")} AND p.kind = ?
              AND CASE WHEN p.geom IS NULL OR b.g IS NULL THEN p.geom IS NULL AND b.g IS NULL
                       WHEN GeometryType(p.geom) = 'POINT' AND GeometryType(b.g) = 'POINT'
                         THEN ST_DWithin(p.geom::geography, b.g::geography, ?)
                       WHEN ST_Area(p.geom) > 0 AND ST_Area(b.g) > 0
                         THEN ST_Area(ST_Intersection(p.geom, b.g)) / NULLIF(ST_Area(ST_Union(p.geom, b.g)), 0) >= ?
                       ELSE false END
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END,
                     CASE WHEN p.geom IS NULL OR b.g IS NULL THEN 0 ELSE ST_Distance(ST_Centroid(p.geom), ST_Centroid(b.g)) END,
                     p.id
            LIMIT 1""",
          Param.S(b.geom.getOrElse("")),
          Param.S(b.label),
          Param.S(b.kind),
          Param.D(within),
          Param.D(cfg.placeOverlapAtLeast)
        )
        .frag
        .query[String]
        .run()
        .headOption
        .map(b.uid -> _)
    }.toMap

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** A place the other side renamed whose line is not written here (this collection's version was kept) keeps its id:
    * the bundle's events and child places that name the new id point at it.
    */
  def placeHere(w: BundleImport.Writer): com.histmersion.domain.catalogue.places.PlaceBundleRepo.PlaceHereResultArg1 => com.histmersion.domain.catalogue.places.PlaceBundleRepo.PlaceHereResultResult =
    val ours = w.local.places.map(p => p.uid -> p.id).toMap
    val kept =
      w.es.places.filterNot(p => w.writes(p.uid)).flatMap(p => ours.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap
    id => kept.getOrElse(id, id)

  /** A place renamed over there (#888): the rename here first — its names, parents, footprints, events and finding
    * assignments follow by key, and the old id stays an alias — then the row.
    */
  def placeRenames(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.followRenames(w.es.places, _.uid, _.id, w.local.places.map(p => p.uid -> p.id).toMap, "place")(PlaceRepo.renameIn)

  /** Places of the other collection's own (V052): before the events that point at them. Their sets (names, parents,
    * links, types) are replaced; a parent that is neither here nor in the bundle is left out.
    */
  def placesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val placeHere = this.placeHere(w)
    val places = w.es.places.filter(p => w.writes(p.uid))
    places.foreach { p =>
      val range = rangeOrAll(p.period)
      val props = p.properties.map(BundleFormat.printer.print)
      sql"""INSERT INTO places (uid, origin, id, label, kind, geom, period, source, properties, dataset, updated_at)
            VALUES (${p.uid}, ${p.origin}, ${p.id}, ${p.label}, ${p.kind}, ST_GeomFromEWKB(decode(${p.geom}, 'hex')),
              $range::int8range, ${p.source}, $props::jsonb, 'local',
              ${p.updatedAt}::timestamptz)
            ON CONFLICT (uid) DO UPDATE SET origin = EXCLUDED.origin, label = EXCLUDED.label, kind = EXCLUDED.kind,
              geom = EXCLUDED.geom, period = EXCLUDED.period, source = EXCLUDED.source,
              properties = EXCLUDED.properties, dataset = 'local', updated_at = EXCLUDED.updated_at""".update.run()
    }
    places.foreach { p =>
      val id = sql"SELECT id FROM places WHERE uid = ${p.uid}".query[String].run().head
      sql"DELETE FROM place_names WHERE place_id = $id AND import_source = ''".update.run()
      sql"DELETE FROM place_relations WHERE child_id = $id".update.run()
      sql"DELETE FROM place_links WHERE place_id = $id".update.run()
      sql"DELETE FROM place_types WHERE place_id = $id".update.run()
      p.names.foreach { n =>
        sql"""INSERT INTO place_names (place_id, name, lang, period)
              VALUES ($id, ${n.name}, ${n.lang.getOrElse("")},
                ${rangeLiteral(n.period)}::int8range)
              ON CONFLICT DO NOTHING""".update.run()
      }
      p.relations.foreach { r =>
        val range = rangeOrAll(r.period)
        sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
              SELECT $id, ${placeHere(r.parentId)}, ${r.kind}, $range::int8range
              WHERE EXISTS (SELECT 1 FROM places WHERE id = ${placeHere(r.parentId)})
              ON CONFLICT DO NOTHING""".update.run()
      }
      p.links.foreach { l =>
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
              VALUES ($id, ${l.scheme}, ${l.externalId}, ${l.relation}) ON CONFLICT DO NOTHING""".update.run()
      }
      p.types.foreach { t =>
        val range = rangeOrAll(t.period)
        sql"""INSERT INTO place_types (place_id, type_label, period, citekey)
              VALUES ($id, ${t.typeLabel}, $range::int8range, ${t.citekey})
              ON CONFLICT DO NOTHING""".update.run()
      }
    }

  /** Old place ids (V055), as old slugs: never one that is a place id here, never one another place has. */
  def placeAliasesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.es.placeAliases.foreach { a =>
      sql"""INSERT INTO place_aliases (alias, place_id, created_at)
            SELECT ${a.alias}, id, ${a.createdAt}::timestamptz FROM places
            WHERE uid = ${a.placeUid} AND id <> ${a.alias}
              AND NOT EXISTS (SELECT 1 FROM places WHERE id = ${a.alias})
            ON CONFLICT (alias) DO NOTHING""".update.run()
    }

  /** The site types the other side refined: the newer answer wins, as for the event-catalogue sets. */
  def siteTypesIn(w: BundleImport.Writer): Unit =
    import com.histmersion.api.exchange.Bundle.EventSets.newer
    given DbCon = w.transaction
    if w.es.siteTypes.nonEmpty then
      val placeHere = this.placeHere(w)
      val here = Sql
        .raw(s"SELECT id, ${ts("site_type_checked_at")} FROM places WHERE site_type_checked_at IS NOT NULL")
        .frag
        .query[(String, String)]
        .run()
        .toMap
      w.es.siteTypes.foreach { t =>
        val id = placeHere(t.placeId)
        if newer(here.get(id), t.checkedAt) then
          sql"""UPDATE places SET site_type = ${t.siteType}, site_type_checked_at = ${t.checkedAt}::timestamptz
                WHERE id = $id""".update.run(): Unit
      }
