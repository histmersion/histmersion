package com.histmersion
package domain.catalogue.places
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.domain.MergeSettings
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.database.Jsonb
import com.histmersion.util.data.exchange.{BundleFormat, BundleScope}
import com.histmersion.util.data.exchange.BundleSql.*

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.library.Citekey
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.Timestamps

/** The places as a bundle carries them (docs/research-merge-import.md §4, §7.3): this collection's own places with
  * their names, parents, links and types, their old ids and the site types refined from outside — read out in a
  * deterministic order, and written in by an import.
  */
private[places] object PlaceBundleRepo:

  // ── out ──────────────────────────────────────────────────────────────────────────────────────

  private def rangeOf(isNull: Boolean, lo: Option[Long], hi: Option[Long]): Option[Bundle.Range] =
    range(
      util.data.exchange.Bundle.RangeIsNull(isNull),
      lo.map(util.data.exchange.Bundle.RangeLower(_)),
      hi.map(util.data.exchange.Bundle.RangeUpper(_))
    )

  /** The places of this collection's own (`dataset = 'local'`, V052) in scope — or, with `alsoIds`, any place with one
    * of those ids as well (what a merge compares the bundle's against).
    */

  def places(scope: BundleScope, alsoIds: List[BPlaceRow.Id] = Nil)(using DbCon): Vector[Bundle.Place] =
    val own = sql"p.dataset = 'local' AND ${where(scope, sql"p.uid", sql"p.updated_at")}"
    val sel = if alsoIds.isEmpty then own
    else sql"(($own) OR p.id = ANY(${alsoIds.distinct}::text[]))"
    val rows = sql"""SELECT p.uid, p.origin, p.id, p.label, p.kind, encode(ST_AsEWKB(p.geom), 'hex'),
                       ${rangeCols(sql"p.period")}, p.source, p.properties::text, ${Timestamps.iso(sql"p.updated_at")}
                FROM places p WHERE $sel ORDER BY p.uid""".query[BPlaceRow].run()
    val ids = rows.map(_.id).toList
    if ids.isEmpty then Vector.empty
    else
      val names = sql"""SELECT place_id, name, lang, ${rangeCols(sql"period")} FROM place_names
                        WHERE place_id = ANY($ids::text[]) AND import_source = '' ORDER BY place_id, name COLLATE "C", lang COLLATE "C", import_ref COLLATE "C""""
        .query[BPlaceNameRow]
        .run()
        .groupBy(n => BPlaceRow.Id(n.placeId.value))
      val rels = sql"""SELECT child_id, parent_id, kind, ${rangeCols(sql"period")} FROM place_relations
                       WHERE child_id = ANY($ids::text[]) ORDER BY child_id, parent_id COLLATE "C", kind COLLATE "C""""
        .query[BPlaceRelRow]
        .run()
        .groupBy(_.childId.value)
      val links =
        sql"""SELECT place_id, scheme, external_id, relation FROM place_links WHERE place_id = ANY($ids::text[])
                        ORDER BY place_id, scheme COLLATE "C", external_id COLLATE "C""""
          .query[(String, String, String, String)]
          .run()
          .groupBy(_._1)
      val types = sql"""SELECT place_id, type_label, ${rangeCols(sql"period")}, citekey
                        FROM place_types WHERE place_id = ANY($ids::text[]) ORDER BY place_id, type_label COLLATE "C""""
        .query[BPlaceTypeRow]
        .run()
        .groupBy(t => BPlaceRow.Id(t.placeId.value))
      rows.map { r =>
        Bundle.Place(
          r.uid,
          r.origin,
          Bundle.Place.Id(r.id.value),
          Bundle.Place.Label(r.label.value),
          Bundle.Place.Kind(r.kind.value),
          r.geom.map(g => Bundle.Place.Geom(g.value)),
          rangeOf(r.pNull.value, r.pLo.map(_.value), r.pHi.map(_.value)),
          r.source.map(s => Bundle.Place.Source(s.value)),
          r.properties.flatMap(p => Jsonb.read[Json](Jsonb.JsonText(p.value))),
          names
            .getOrElse(r.id, Vector.empty)
            .toList
            .map(n =>
              Bundle.PlaceName(
                Bundle.PlaceName.Name(n.name.value),
                Option(n.lang.value).filter(_.nonEmpty).map(Bundle.PlaceName.Lang(_)),
                rangeOf(n.pNull.value, n.pLo.map(_.value), n.pHi.map(_.value))
              )
            ),
          rels
            .getOrElse(r.id.value, Vector.empty)
            .toList
            .map(x =>
              Bundle.PlaceRelation(
                Bundle.PlaceRelation.ParentId(x.parentId.value),
                Bundle.PlaceRelation.Kind(x.kind.value),
                rangeOf(x.pNull.value, x.pLo.map(_.value), x.pHi.map(_.value))
              )
            ),
          links
            .getOrElse(r.id.value, Vector.empty)
            .toList
            .map((_, sc, ex, rel) =>
              Bundle
                .PlaceLink(Bundle.PlaceLink.Scheme(sc), Bundle.PlaceLink.ExternalId(ex), Bundle.PlaceLink.Relation(rel))
            ),
          types
            .getOrElse(r.id, Vector.empty)
            .toList
            .map(t =>
              Bundle.PlaceType(
                Bundle.PlaceType.TypeLabel(t.typeLabel.value),
                rangeOf(t.pNull.value, t.pLo.map(_.value), t.pHi.map(_.value)),
                t.citekey.map(c => Citekey(c.value))
              )
            ),
          Bundle.Place.UpdatedAt(r.updatedAt.value)
        )
      }

  /** Old place ids (V055) of the places a bundle carries — this collection's own (as [[places]]): a gazetteer seed
    * place travels as the reference fingerprint, not as a row.
    */
  def placeAliases(scope: BundleScope)(using DbCon): Vector[Bundle.PlaceAlias] =
    sql"""SELECT x.alias, p.uid, ${Timestamps.iso(sql"x.created_at")}
                FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE p.dataset = 'local' AND ${where(
        scope,
        sql"p.uid",
        sql"x.created_at"
      )} ORDER BY x.alias COLLATE "C\""""
      .query[(String, UUID, String)]
      .run()
      .map((a, u, c) => Bundle.PlaceAlias(Bundle.PlaceAlias.Alias(a), u, Bundle.PlaceAlias.CreatedAt(c)))

  /** The site types refined from outside (`places.site_type`, V085) — of ANY place, the gazetteer's seeds included —
    * and the places asked about with no answer (so the other side does not ask again).
    */
  def siteTypes(scope: BundleScope)(using DbCon): Vector[Bundle.SiteType] =
    sql"""SELECT p.id, p.site_type, ${Timestamps.iso(sql"p.site_type_checked_at")} FROM places p
                WHERE (p.site_type IS NOT NULL OR p.site_type_checked_at IS NOT NULL) AND ${where(
        scope,
        sql"p.uid",
        sql"p.site_type_checked_at"
      )} ORDER BY p.id COLLATE "C\""""
      .query[(String, Option[String], Option[String])]
      .run()
      .map((id, v, c) =>
        Bundle
          .SiteType(Bundle.SiteType.PlaceId(id), v.map(Bundle.SiteType.Value(_)), c.map(Bundle.SiteType.CheckedAt(_)))
      )

  // ── what a merge plan compares ───────────────────────────────────────────────────────────────

  /** Who has each of `ids` here, by the (label, uid) rows of the query `q` makes of them. */
  private def owners(ids: List[BPlaceRow.Id])(q: List[BPlaceRow.Id] => Frag)(using
      DbCon
  ): Map[BPlaceRow.Id, UUID] =
    if ids.isEmpty then Map.empty
    else q(ids.distinct).query[(BPlaceRow.Id, UUID)].run().toMap

  /** The places here among `ids` → their uids. */
  def placesNamed(ids: List[BPlaceRow.Id])(using DbCon): Map[BPlaceRow.Id, UUID] =
    owners(ids)(ls => sql"SELECT id, uid FROM places WHERE id = ANY($ls::text[])")

  /** The old place ids here among `ids` → the uid of the place each names. */
  def oldIdsNamed(ids: List[BPlaceRow.Id])(using DbCon): Map[BPlaceRow.Id, UUID] =
    owners(ids)(ls =>
      sql"SELECT x.alias, p.uid FROM place_aliases x JOIN places p ON p.id = x.place_id WHERE x.alias = ANY($ls::text[])"
    )

  /** Which of `ids` name a place here. */
  def present(ids: List[BPlaceRow.Id])(using DbCon): Set[BPlaceRow.Id] =
    if ids.isEmpty then Set.empty
    else
      sql"SELECT id FROM places WHERE id = ANY(${ids.distinct}::text[])"
        .query[BPlaceRow.Id]
        .run()
        .toSet

  /** A bundle place with no id here that names a place here: the same label (reduced as the gazetteer reduces it: case,
    * filler words, punctuation) and kind, and — when both have a geometry — points within `merge.place-within-km` or
    * shapes overlapping by `merge.place-overlap-at-least` (the intersection over the union); two places without a
    * geometry match on the label alone. The nearest (most overlapping) place wins; a local place is preferred to a seed
    * one.
    */
  def matchPlaces(ps: List[Bundle.Place], cfg: MergeSettings)(using DbCon): Map[UUID, BPlaceRow.Id] =
    val norm = PlaceSql.normLabel(sql"p.label")
    ps.flatMap { b =>
      val within = cfg.placeWithinKm.value * 1000.0
      sql"""WITH b AS (SELECT ST_GeomFromEWKB(decode(nullif(${b.geom.fold("")(_.value)}, ''), 'hex')) AS g)
            SELECT p.id FROM places p, b
            WHERE $norm = ${PlaceSql.normLabel(sql"${b.label}")} AND p.kind = ${b.kind}
              AND CASE WHEN p.geom IS NULL OR b.g IS NULL THEN p.geom IS NULL AND b.g IS NULL
                       WHEN GeometryType(p.geom) = 'POINT' AND GeometryType(b.g) = 'POINT'
                         THEN ST_DWithin(p.geom::geography, b.g::geography, $within)
                       WHEN ST_Area(p.geom) > 0 AND ST_Area(b.g) > 0
                         THEN ST_Area(ST_Intersection(p.geom, b.g)) / NULLIF(ST_Area(ST_Union(p.geom, b.g)), 0) >= ${cfg.placeOverlapAtLeast}
                       ELSE false END
            ORDER BY CASE WHEN p.dataset = 'local' THEN 0 ELSE 1 END,
                     CASE WHEN p.geom IS NULL OR b.g IS NULL THEN 0 ELSE ST_Distance(ST_Centroid(p.geom), ST_Centroid(b.g)) END,
                     p.id
            LIMIT 1"""
        .query[BPlaceRow.Id]
        .run()
        .headOption
        .map(b.uid -> _)
    }.toMap

  // ── in ───────────────────────────────────────────────────────────────────────────────────────

  /** A place the other side renamed whose line is not written here (this collection's version was kept) keeps its id:
    * the bundle's events and child places that name the new id point at it.
    */
  def placeHere(w: BundleImport.Writer): BPlaceRow.Id => BPlaceRow.Id =
    val ours = w.local.places.map(p => p.uid -> p.id.value).toMap
    val kept =
      w.es.places
        .filterNot(p => w.writes(p.uid))
        .flatMap(p => ours.get(p.uid).filter(_ != p.id.value).map(p.id.value -> _))
        .toMap
    id => kept.get(id.value).fold(id)(BPlaceRow.Id(_))

  /** A place renamed over there (#888): the rename here first — its names, parents, footprints, events and finding
    * assignments follow by key, and the old id stays an alias — then the row.
    */
  def placeRenames(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    w.followRenames(
      w.es.places,
      _.uid,
      _.id,
      w.local.places.map(p => p.uid -> p.id).toMap,
      PlanQuestions.LabelTakenWhat("place")
    )((from, to) =>
      PlaceRepo.renameIn(
        Place.Id.assume(from.value),
        Place.Id.assume(to.value)
      )
    )

  /** Places of the other collection's own (V052): before the events that point at them. Their sets (names, parents,
    * links, types) are replaced; a parent that is neither here nor in the bundle is left out.
    */
  def placesIn(w: BundleImport.Writer): Unit =
    given DbCon = w.transaction
    val placeHere = this.placeHere(w)
    val places = w.es.places.filter(p => w.writes(p.uid))
    places.foreach { p =>
      val range = rangeOrAll(p.period)
      val props = p.properties.map(BundleFormat.print)
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
              VALUES ($id, ${n.name}, ${n.lang.map(_.value).getOrElse("")},
                ${rangeLiteral(n.period)}::int8range)
              ON CONFLICT DO NOTHING""".update.run()
      }
      p.relations.foreach { r =>
        val range = rangeOrAll(r.period)
        sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
              SELECT $id, ${placeHere(BPlaceRow.Id(r.parentId.value))}, ${r.kind}, $range::int8range
              WHERE EXISTS (SELECT 1 FROM places WHERE id = ${placeHere(BPlaceRow.Id(r.parentId.value))})
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
      val here =
        sql"SELECT id, ${Timestamps.iso(sql"site_type_checked_at")} FROM places WHERE site_type_checked_at IS NOT NULL"
          .query[(String, String)]
          .run()
          .toMap
      w.es.siteTypes.foreach { t =>
        val id = placeHere(BPlaceRow.Id(t.placeId.value))
        if newer(here.get(id.value).map(Bundle.UpdatedAt(_)), t.checkedAt.map(c => Bundle.UpdatedAt(c.value))) then
          sql"""UPDATE places SET site_type = ${t.siteType}, site_type_checked_at = ${t.checkedAt}::timestamptz
                WHERE id = $id""".update.run(): Unit
      }
