package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** How the gazetteer resolved a [[Filter]]'s `places` values. Computing it needs the database, so it
  * is done ONCE per query by the handler ([[PlaceRepo.scopeFor]]) and passed into the pure
  * `Filter → SQL` builders; an empty scope means no value named a known place and every one of them
  * falls back to the wave-1 name ILIKE, exactly as before the gazetteer existed. */
final case class PlaceScope(byValue: Map[String, List[String]], labels: Map[String, String] = Map.empty):
  /** The place ids one filter value stands for — itself plus its descendants as of the filter's
    * period. `None` when the value is not a place id. */
  def idsFor(value: String): Option[List[String]] = byValue.get(value).filter(_.nonEmpty)
  /** What to CALL a filter value when explaining a match: the gazetteer label for an id, and the
    * value itself for a free-text name. "place: pleiades_589831" names nothing a reader knows
    * (item 15); "place: Kairatos (river)" does. */
  def labelFor(value: String): String             = labels.getOrElse(value, value)
  def isEmpty: Boolean                            = byValue.isEmpty
  def nonEmpty: Boolean                           = !isEmpty

object PlaceScope:
  val empty: PlaceScope = PlaceScope(Map.empty)

/** One row of [[PlaceRepo.get]] — a [[PlaceSummary]] plus the geometry and the finding count. */
private[db] final case class PlaceDetailRow(
    id: String, label: String, kind: String, periodStart: Option[Long], periodEnd: Option[Long],
    source: Option[String], names: String, contested: Boolean, dating: PlaceDating, importSource: Option[String],
    geometry: Option[String], findings: Int, hasBoundary: Boolean, timeBasis: PlaceTimeBasis,
    precision: LocationPrecision
):
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)

given DbCodec[PlaceSummary]   = DbCodec.derived
given DbCodec[PlaceHit]       = DbCodec.derived
given DbCodec[PlaceTreeRow]   = DbCodec.derived
given DbCodec[ManualPlaceName] = DbCodec.derived
given DbCodec[PlaceDetailRow] = DbCodec.derived
given DbCodec[PlaceDescription] = DbCodec.derived

private[db] final case class FootprintRow(
    id: Int, placeId: String, role: String, periodStart: Option[Long], periodEnd: Option[Long],
    certainty: Option[String], accuracyM: Option[Float], citekey: Option[String]):
  def toDto: PlaceFootprint =
    PlaceFootprint(id, placeId, role,
      for s <- periodStart; e <- periodEnd yield Period(s, e),
      certainty, accuracyM, citekey)
given DbCodec[FootprintRow]       = DbCodec.derived
given DbCodec[PlaceLink]          = DbCodec.derived

private[db] final case class PlaceTypeRow(placeId: String, typeLabel: String,
    periodStart: Option[Long], periodEnd: Option[Long], citekey: Option[String]):
  def toDto: PlaceType =
    PlaceType(placeId, typeLabel, for s <- periodStart; e <- periodEnd yield Period(s, e), citekey)
given DbCodec[PlaceTypeRow]       = DbCodec.derived

private[db] final case class HistPeriodRow(
    id: String, label: String, authority: Option[String],
    periodStart: Long, periodEnd: Long,
    region: Option[String], parentId: Option[String], preset: Option[String]):
  def toDto: HistoricalPeriod =
    HistoricalPeriod(id, label, authority, Period(periodStart, periodEnd), region, parentId,
      preset.flatMap(PresetRegion.fromWire))
given DbCodec[HistPeriodRow] = DbCodec.derived

/** The gazetteer store (plan §1.6a): import, search, hierarchy, and the MATERIALISATION of
  * `finding_places` — the only place that decides which findings belong to which places.
  *
  * Temporal rule, applied identically everywhere: place/name/relation periods are NOT NULL — a timeless
  * entry carries `int8range(-infinity, infinity)` (V015). `asOf` is a single signed year and uses
  * containment (`period @> asOf`); `Filter.period` is a range and uses overlap (`&&`).
  *
  * Geometry crosses the boundary as **GeoJSON text** (`ST_AsGeoJSON` / `ST_GeomFromGeoJSON`), the
  * same reasoning as the WKT used for findings: Postgres does all the spatial work and the native
  * image never needs a binary PostGIS JDBC type.
  */
object PlaceRepo:

  /** Every alternate name of `p`, comma-joined — the `names` column of a [[PlaceSummary]]. */
  private val namesCol =
    "coalesce((SELECT string_agg(DISTINCT n.name, ', ' ORDER BY n.name) FROM place_names n" +
      " WHERE n.place_id = p.id), '')"

  /** `properties.contested` (plan §1.6a / stream 2.2): a hand-traced shape whose extent is disputed.
    * It lives in the jsonb blob — the importer keeps every property it does not model — and is
    * projected out here so every surface can mark it without parsing JSON. */
  private val contestedCol = "coalesce((p.properties->>'contested')::boolean, false)"

  /** A place GeoNames or Wikidata brought in (#1064), over a `places` row aliased `alias`. Such a
    * place is for finding a name and placing a finding, and for an explicit pick: it is no part of
    * the hierarchy (a filter's scope, the tree, a place's children), gets no findings by proximity or
    * containment — only the finding the geocoder put on its very point — and is drawn on the base
    * map only with a finding (2026-10-09: 1.2 M points had made every region's subtree and hover
    * counts enormous). */
  private[db] def importedSql(alias: String): String =
    s"coalesce($alias.import_source, '') IN ('wikidata', 'geonames')"

  /** A place whose source states NO date (GeoNames, Wikidata — [[PlaceDating.Unknown]]): the open
    * range there means "unknown", not "always". Findable and placeable by name; never on a
    * time-sliced map. Over a `places` row aliased `p`. */
  private[db] val UndatedImportSql =
    "(coalesce(p.import_source, '') IN ('wikidata', 'geonames') AND lower_inf(p.period) AND upper_inf(p.period))"

  /** [[PlaceDating]] of a `places` row aliased `p` (V074). */
  private[db] val datingCol =
    s"CASE WHEN p.start_unknown THEN 'end-only' WHEN $UndatedImportSql THEN 'unknown' ELSE 'stated' END"

  /** The STATED start: `NULL` when it is unknown (the stored lower bound is then only the window the
    * time filters use, V074). */
  private[db] val startCol = "CASE WHEN p.start_unknown THEN NULL ELSE lower(p.period) END"

  /** The [[PlaceSummary]] projection, over a `places` row aliased `p`. One definition, reused. */
  private val summaryCols =
    s"p.id, p.label, p.kind, $startCol, upper(p.period) - 1, p.source, $namesCol, $contestedCol, $datingCol, p.import_source"

  // ── import ───────────────────────────────────────────────────────────────────────────────────

  /** Upsert a batch of places with their names and parent edges, in ONE transaction and in three
    * passes — rows, then names, then relations — because a file routinely names a parent that only
    * appears later in it, and the foreign key would reject the edge otherwise.
    *
    * Names and relations are REPLACED for every id in the batch (an import is the whole truth about
    * the places it mentions) — except the names a person added (`manual`, V068, #987), which no import
    * deletes, so a re-seed never loses them; a relation whose parent is in neither the batch nor the
    * store is reported in `failed` rather than aborting the import. */
  def upsertPlaces(ds: DataSource, batch: List[PlaceInput], dataset: Dataset = Dataset.Seed): PlaceImportResult =
    if batch.isEmpty then PlaceImportResult(0, 0, 0, Nil)
    else transact(ds)(upsertIn(batch, dataset))

  /** The ids of `ids` a seed import must not create (V080): a place a person removed
    * (`place_removals`), and an old id of another place (`place_aliases`: a merged duplicate, a renamed
    * place) — else the next `places seed` would bring back what a person merged or removed. */
  private def suppressed(ids: List[String])(using DbCon): Set[String] =
    sql"""SELECT id FROM place_removals WHERE id = ANY($ids::text[])
          UNION
          SELECT alias FROM place_aliases a WHERE a.alias = ANY($ids::text[])
            AND NOT EXISTS (SELECT 1 FROM places p WHERE p.id = a.alias)""".query[String].run().toSet

  /** [[upsertPlaces]] in the CALLER's transaction. A place a person removed or merged away is left out
    * of the batch ([[suppressed]]). */
  private def upsertIn(all: List[PlaceInput], dataset: Dataset)(using DbTx): PlaceImportResult =
    val skip  = suppressed(all.map(_.place.id).distinct)
    val batch = if skip.isEmpty then all else all.filterNot(in => skip.contains(in.place.id))
    val ids = batch.map(_.place.id).distinct
    val _ = batchUpdate(batch) { in =>
      val g = in.place.geometry
      val p = in.place.period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
      sql"""INSERT INTO places (id, label, kind, geom, period, source, properties, dataset)
            VALUES (${in.place.id}, ${in.place.label}, ${in.place.kind},
                    CASE WHEN ${g}::text IS NULL THEN NULL
                         ELSE ST_SetSRID(ST_GeomFromGeoJSON(${g}::text), 4326) END,
                    ${p}::int8range, ${in.place.source}, ${in.place.properties}::jsonb, ${dataset.wire})
            ON CONFLICT (id) DO UPDATE SET
              label = EXCLUDED.label, kind = EXCLUDED.kind, geom = EXCLUDED.geom,
              period = EXCLUDED.period, source = EXCLUDED.source,
              properties = EXCLUDED.properties, dataset = EXCLUDED.dataset""".update
    }

    val idList = ids.toList
    val _ = GazetteerRepo.linkSeedIds(idList)
    // only the seed's own names (source ''): what GeoNames, Wikidata or an LPF dataset gave a seed
    // place (V071) is theirs to replace, at their next import
    val _ = sql"DELETE FROM place_names     WHERE place_id = ANY($idList::text[]) AND NOT manual AND import_source = ''".update.run()
    val _ = sql"DELETE FROM place_relations WHERE child_id = ANY($idList::text[])".update.run()

    val names = batch.flatMap(in => in.names.map(in.place.id -> _))
    val _ = batchUpdate(names) { (pid, n) =>
      val p = n.period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
      sql"""INSERT INTO place_names (place_id, name, lang, period)
            VALUES ($pid, ${n.name}, ${n.lang}, ${p}::int8range)
            ON CONFLICT (place_id, name, lang, import_source, import_ref) DO UPDATE SET period = EXCLUDED.period""".update
    }

    // relations: only those whose parent actually exists once every row of the batch is in
    val wanted   = batch.flatMap(_.relations)
    val parents  = wanted.map(_.parentId).distinct.toList
    val existing = sql"SELECT id FROM places WHERE id = ANY($parents::text[])"
      .query[String].run().toSet
    val (ok, dangling) = wanted.partition(r => existing.contains(r.parentId))
    val _ = batchUpdate(ok) { r =>
      val p = r.period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
      sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
            VALUES (${r.childId}, ${r.parentId}, ${r.kind}, ${p}::int8range)
            ON CONFLICT (child_id, parent_id, kind) DO UPDATE SET period = EXCLUDED.period""".update
    }
    PlaceImportResult(
      places = ids.size, names = names.size, relations = ok.size,
      failed = dangling.map(r => s"${r.childId}: no such parent '${r.parentId}'").distinct.take(20))

  // ── reads ────────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/places?q=&asOf=` — name/label search. A place matches when one of its names (valid at
    * `asOf`) contains `q`, or its label does — for a `q` of one or two characters, when a name or the
    * label IS `q` (by the name key); exact hits sort first, then prefixes, then the bundled seed before
    * the imported sources (#1064), then the places carrying the most findings. `q` empty lists the
    * gazetteer by size. */
  def search(ds: DataSource, q: Option[String], asOf: Option[Long], limit: Int,
      modernFrom: Long = DefaultModernFrom): Vector[PlaceHit] =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val pre  = text.map(FilterSql.startsWith).getOrElse("%")
    val exact = text.getOrElse("")
    val long  = text.filter(_.length >= 3)
    val short = text.filter(_.length < 3).map(Geocode.key)
    val n     = limit.max(1)
    // Before `modern-from` the time rule never shows a present-day GeoNames/Wikidata place — 1.15 M of
    // the 1.28 M places. Said in so many words ([[ShownBeforeModernSql]], implied by [[inTimeSql]]
    // then), it lets every pass read the 132 000 others through their own indexes (V076): at 2500 BCE
    // "Tro" checked 8 500 candidates by primary key, nearly all of them imported, and found its label
    // matches in all 1.28 M labels — 57 ms; through the partial indexes, 34 (2026-10-09).
    val pm    = SqlLiteral(if asOf.exists(_ < modernFrom) then s" AND $ShownBeforeModernSql" else "")
    connect(ds):
      // RANK FIRST, DECORATE AFTER (2026-10-09). The ordering needs only the row itself and its
      // finding count; the alternate-name laterals (every name of the place, and the one that
      // matched) are for the `n` rows shown. Computing them for every candidate first — 23 800 for
      // "Ach" — and finding the candidates by a merge join over all 1.2 M places took 0.5–14 s a
      // keystroke on a live-sized copy.
      //
      // A LABEL PREFIX settles it on its own when it has `n` rows: an exact hit is a prefix hit, and
      // both rank above every other candidate, so the top `n` of the prefixes ARE the top `n`. (A
      // query of one or two characters is matched by key, where that does not hold — not tried.)
      val byPrefix =
        if long.isEmpty then Vector.empty
        else
          (Sql.raw("WITH ") ++ timeWindowCte(asOf, modernFrom) ++ sql""" SELECT p.id FROM places p
                LEFT JOIN LATERAL (
                  SELECT count(*)::int AS n FROM finding_places fp WHERE fp.place_id = p.id
                ) fc ON true
                WHERE p.label ILIKE $pre ESCAPE '\\'$pm
                  AND ${SqlLiteral(inTimeSql("p", asOf))}
                ORDER BY (lower(p.label) = lower($exact)) DESC,
                         ${SqlLiteral(Geocoder.tierSql)},
                         fc.n DESC, length(p.label), p.id
                LIMIT $n""").query[String].run()
      val ranked: Vector[String] =
        if byPrefix.size >= n then byPrefix
        else
          // `cand` first (#1064): with GeoNames and Wikidata in, the gazetteer holds ~1.5 M places
          // and ~8 M names; the trigram indexes (V071) find the candidates. A name candidate must be
          // valid at `asOf` — the same condition the matched name below has, so a place is a
          // candidate exactly when it passes the filter. A query of one or two characters has no
          // trigram: "ur" is in ~100 000 names, so it is matched by the name KEY (V067) instead —
          // "Ur" finds Ur(i), not every Ural and Uruk.
          (Sql.raw("WITH ") ++ timeWindowCte(asOf, modernFrom) ++ sql""", cand AS MATERIALIZED (
                  SELECT p.id FROM places p WHERE ${long}::text IS NOT NULL AND p.label ILIKE $cont ESCAPE '\\'$pm
                  UNION
                  SELECT place_id FROM place_names WHERE ${long}::text IS NOT NULL AND name ILIKE $cont ESCAPE '\\'
                    AND (${asOf}::bigint IS NULL OR period @> ${asOf}::bigint)
                  UNION
                  SELECT p.id FROM places p WHERE ${short}::text IS NOT NULL AND histmersion_place_key(p.label) = ${short}::text$pm
                  UNION
                  SELECT place_id FROM place_names WHERE ${short}::text IS NOT NULL AND histmersion_place_key(name) = ${short}::text)
                SELECT p.id FROM places p
                LEFT JOIN LATERAL (
                  SELECT count(*)::int AS n FROM finding_places fp WHERE fp.place_id = p.id
                ) fc ON true
                WHERE (${text}::text IS NULL OR p.id = ANY(ARRAY(SELECT id FROM cand)))$pm
                  AND ${SqlLiteral(inTimeSql("p", asOf))}
                ORDER BY (lower(p.label) = lower($exact)) DESC,
                         (p.label ILIKE $pre ESCAPE '\\') DESC,
                         ${SqlLiteral(Geocoder.tierSql)},
                         fc.n DESC, length(p.label), p.id
                LIMIT $n""").query[String].run()
      if ranked.isEmpty then Vector.empty
      else
        val ids  = ranked.toList
        val rows = sql"""SELECT p.id, p.label, p.kind, ${SqlLiteral(startCol)}, upper(p.period) - 1, p.source,
                     coalesce(nm.names, ''), nm.matched,
                     (SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = p.id),
                     ${SqlLiteral(contestedCol)}, ${SqlLiteral(datingCol)}, p.import_source
              FROM places p
              LEFT JOIN LATERAL (
                SELECT string_agg(DISTINCT n.name, ', ' ORDER BY n.name) AS names,
                       -- the name the user actually typed, not just any name that contains it:
                       -- exact first, then the shortest ("Turkey", never "Republic of Turkey")
                       (SELECT m.name FROM place_names m
                        WHERE m.place_id = p.id AND m.name ILIKE $cont ESCAPE '\\'
                          AND (${asOf}::bigint IS NULL OR m.period @> ${asOf}::bigint)
                        ORDER BY (lower(m.name) = lower($exact)) DESC, length(m.name), m.name
                        LIMIT 1) AS matched
                FROM place_names n
                WHERE n.place_id = p.id
                  AND (${asOf}::bigint IS NULL OR n.period @> ${asOf}::bigint)
              ) nm ON true
              WHERE p.id = ANY($ids::text[])""".query[PlaceHit].run()
        val at = rows.map(h => h.id -> h).toMap
        ranked.flatMap(at.get)

  /** `GET /api/places/{id}?asOf=` — the row, its geometry as GeoJSON, and the hierarchy AS OF `asOf`.
    * A place with no shape of its own (a continent is a hierarchy node, not a polygon) reports the
    * UNION of its children's shapes, which is what the web UI draws as its overlay. */
  def get(ds: DataSource, id: String, asOf: Option[Long] = None, modernFrom: Long = DefaultModernFrom): Option[PlaceDetail] =
    connect(ds):
      sql"""WITH g AS (
              SELECT COALESCE(p.geom, (
                SELECT ST_Union(c.geom) FROM place_relations r JOIN places c ON c.id = r.child_id
                WHERE r.parent_id = p.id AND c.geom IS NOT NULL
                  AND (${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint))) AS shape
              FROM places p WHERE p.id = $id)
            SELECT ${SqlLiteral(summaryCols)},
                   ST_AsGeoJSON(g.shape),
                   (SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = p.id),
                   coalesce(ST_Dimension(g.shape) = 2, false),
                   ${SqlLiteral(timeBasisSql("p"))}, p.location_precision
            FROM places p, g WHERE p.id = $id""".query[PlaceDetailRow].run().headOption.map { r =>
        val fps = sql"""SELECT f.id, f.place_id, f.role, lower(f.period), upper(f.period) - 1,
                               f.certainty, f.accuracy_m, f.citekey
                        FROM place_footprints f WHERE f.place_id = $id
                          AND (${asOf}::bigint IS NULL OR f.period @> ${asOf}::bigint)
                        ORDER BY f.role, lower(f.period)""".query[FootprintRow].run().map(_.toDto).toList
        val lks = sql"""SELECT place_id, scheme, external_id, relation FROM place_links
                        WHERE place_id = $id ORDER BY scheme, external_id""".query[PlaceLink].run().toList
        val tps = sql"""SELECT place_id, type_label,
                               CASE WHEN period = int8range(NULL, NULL) THEN NULL ELSE lower(period) END,
                               CASE WHEN period = int8range(NULL, NULL) THEN NULL ELSE upper(period) - 1 END,
                               citekey
                        FROM place_types WHERE place_id = $id
                          AND (${asOf}::bigint IS NULL OR period @> ${asOf}::bigint)
                        ORDER BY type_label""".query[PlaceTypeRow].run().map(_.toDto).toList
        PlaceDetail(r.toSummary, r.geometry, parents(id, asOf).toList, kids(id, asOf, Int.MaxValue, modernFrom).toList,
          r.findings, asOf, r.contested, fps, lks, tps, hasBoundary = r.hasBoundary, descriptions = descriptionsOf(id),
          timeBasis = r.timeBasis, locationPrecision = r.precision, siteType = siteTypeOf(id))
      }

  /** What site `id` is (#1449), read as the map's layer reads it ([[SiteTypes.of]]): a stored refinement,
    * else its gazetteer's own fields — so its card shows the icon its marker has. */
  private def siteTypeOf(id: String)(using DbCon): Option[String] =
    sql"""SELECT kind, site_type, properties->>'feature_types', properties->>'fcode', properties->>'class'
          FROM places WHERE id = $id""".query[(String, Option[String], Option[String], Option[String], Option[String])]
      .run().headOption.flatMap((kind, stored, ft, fcode, wd) => SiteTypes.of(id, kind, stored, ft, fcode, wd))

  /** What place `id` IS, as its sources describe it (V074): English first, then the other languages;
    * within one language the best source first (an LPF dataset, then Wikidata, then GeoNames). */
  private def descriptionsOf(id: String)(using DbCon): List[PlaceDescription] =
    sql"""SELECT text, lang, import_source FROM place_descriptions WHERE place_id = $id
          ORDER BY (lang = 'en') DESC, lang,
                   CASE WHEN import_source LIKE 'lpf:%' THEN 1 WHEN import_source = 'wikidata' THEN 2 ELSE 3 END,
                   import_ref""".query[PlaceDescription].run().toList

  /** Parents valid at `asOf` (both the edge and the parent itself must be). */
  def parents(ds: DataSource, id: String, asOf: Option[Long]): Vector[PlaceSummary] =
    connect(ds)(parents(id, asOf))

  private def parents(id: String, asOf: Option[Long])(using DbCon): Vector[PlaceSummary] =
    sql"""SELECT ${SqlLiteral(summaryCols)} FROM place_relations r JOIN places p ON p.id = r.parent_id
          WHERE r.child_id = $id
            AND (${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint)
            AND (${asOf}::bigint IS NULL OR p.period @> ${asOf}::bigint)
          ORDER BY p.label, p.id""".query[PlaceSummary].run()

  /** `GET /api/places/{id}/children?asOf=` — with a time, only the children the time rule shows
    * ([[inTimeSql]]). */
  def children(ds: DataSource, id: String, asOf: Option[Long], limit: Int,
      modernFrom: Long = DefaultModernFrom): Vector[PlaceSummary] =
    connect(ds)(kids(id, asOf, limit, modernFrom))

  private def kids(id: String, asOf: Option[Long], limit: Int, modernFrom: Long)(using DbCon): Vector[PlaceSummary] =
    (Sql.raw("WITH ") ++ timeWindowCte(asOf, modernFrom) ++
      sql""" SELECT ${SqlLiteral(summaryCols)} FROM place_relations r JOIN places p ON p.id = r.child_id
          WHERE r.parent_id = $id AND NOT ${SqlLiteral(importedSql("p"))}
            AND (${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint)
            AND ${SqlLiteral(inTimeSql("p", asOf))}
          ORDER BY p.label, p.id LIMIT ${limit.max(1)}""").query[PlaceSummary].run()

  /** SQL twin of [[com.histmersion.core.Tree.rootEligible]]: a place that may head the hierarchy. Region,
    * polity or continent; not a Pleiades `withdrawn` record (marked as a property in part of the
    * export and only in the label in the rest); not an uncertain identification (`Theren? (river)`)
    * or a reconstructed name (`*Granouas`). Applies ONLY to root selection — a river still appears
    * under the region that contains it. */
  private val rootEligibleSql =
    "p.kind IN ('region', 'polity', 'continent')" +
      " AND NOT coalesce((p.properties->>'withdrawn')::boolean, false)" +
      " AND p.label NOT ILIKE 'withdrawn%' AND p.label NOT LIKE '*%' AND p.label NOT LIKE '%?%'"

  /** The hierarchy AS OF one year, as a depth-tagged pre-order walk (a recursive CTE over
    * `place_relations` whose own `period` covers `asOf`). `root` empty starts from every ELIGIBLE
    * place that has no parent — the world level; without the eligibility filter that level is 25
    * Pleiades rivers and mountains with the five continents lost among them. Bounded by `maxDepth`
    * (which is also what makes a cyclic edge harmless) and by `limit`. */
  def hierarchy(ds: DataSource, asOf: Option[Long], root: Option[String], maxDepth: Int, limit: Int,
      depthCap: Int = Int.MaxValue, modernFrom: Long = DefaultModernFrom): Vector[PlaceTreeRow] =
    // `maxDepth` is a caller's number; `depthCap` is the configured ceiling (`places.tree-depth`).
    // A caller asking for depth 10 000 walked ten thousand levels of a recursive CTE.
    val depth = math.max(0, math.min(maxDepth, depthCap))
    connect(ds):
      (Sql.raw("WITH RECURSIVE ") ++ timeWindowCte(asOf, modernFrom) ++ sql""", edges AS (
              SELECT r.child_id, r.parent_id FROM place_relations r
              WHERE ${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint
            ), roots AS (
              SELECT p.id FROM places p
              WHERE (${root}::text IS NOT NULL AND p.id = ${root}::text)
                 OR (${root}::text IS NULL
                     AND ${SqlLiteral(rootEligibleSql)}
                     AND NOT EXISTS (SELECT 1 FROM edges e WHERE e.child_id = p.id)
                     AND EXISTS (SELECT 1 FROM edges e WHERE e.parent_id = p.id))
            ), tree AS (
              SELECT p.id, 0 AS depth, ARRAY[lower(p.label), p.id] AS path,
                     ARRAY[p.id] AS visited
              FROM roots r JOIN places p ON p.id = r.id
              WHERE ${SqlLiteral(inTimeSql("p", asOf))}
              UNION ALL
              SELECT c.id, t.depth + 1, t.path || lower(c.label) || c.id, t.visited || c.id
              FROM tree t JOIN edges e ON e.parent_id = t.id JOIN places c ON c.id = e.child_id
              WHERE t.depth + 1 <= ${depth} AND NOT ${SqlLiteral(importedSql("c"))}
                -- a `part_of` cycle (A part of B part of A) is data, not an impossibility, and the
                -- depth bound alone only made it terminate — it still walked the loop N times and
                -- reported the same place at every level. Refuse to revisit a place on one path.
                AND NOT (c.id = ANY(t.visited))
                AND ${SqlLiteral(inTimeSql("c", asOf))}
            )
            SELECT t.id, p.label, p.kind, t.depth,
                   (SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = t.id),
                   ${SqlLiteral(contestedCol)}
            FROM tree t JOIN places p ON p.id = t.id
            ORDER BY t.path, t.depth LIMIT ${limit.max(1)}""").query[PlaceTreeRow].run()

  /** The gazetteer places one finding was materialised into. */
  def placesOf(ds: DataSource, findingId: String): Vector[PlaceSummary] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(summaryCols)} FROM finding_places fp JOIN places p ON p.id = fp.place_id
            WHERE fp.finding_id = $findingId ORDER BY p.label, p.id""".query[PlaceSummary].run()

  /** How many findings matching `filter` landed in NO place at all — the "unlocated" bucket of the
    * region tree (plan §1.6a). Built from the SHARED [[FilterSql]] fragments, so it is narrowed by
    * exactly the same criteria as the map feed. */
  def unlocated(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    val q = Sql.raw("SELECT count(*)::int FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ") ++
      FilterSql.sourceWhere(filter) ++ Sql.raw(" AND ") ++ FilterSql.findingWhere(filter, scope = scope) ++
      Sql.raw(" AND NOT EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id)")
    connect(ds)(q.frag.query[Int].run().headOption.getOrElse(0))

  // ── the materialisation ──────────────────────────────────────────────────────────────────────

  /** Rebuild `finding_places` from scratch (`histmersion places rebuild` / `POST /api/places/rebuild`).
    *
    * A finding belongs to a place when
    *   - it has geometry and the place COVERS it (`ST_CoveredBy`, not `ST_Within`: a finding exactly
    *     on a shared border must count in BOTH polygons — plan §1.6a), or the place is a POINT — a
    *     Pleiades site has no extent — and the finding is within `toleranceKm` of it; **and**
    *   - their periods overlap, where a NULL period on either side means "always" (so an undated
    *     finding still gets its geography, and a modern country contains everything);
    *   - or, for a finding with NO geometry, its `region_name` equals a place name exactly
    *     (case-insensitively) and the periods overlap.
    * A GeoNames or Wikidata place ([[importedSql]]) takes part in none of these: it gets exactly the
    * findings whose point IS its point (the geocoder chose it for the finding's place name), when
    * the periods overlap.
    *
    * The point test is written as an index-usable `&& ST_Expand(...)` pre-filter followed by the
    * exact geography distance: `ST_DWithin` on a `::geography` cast alone cannot use the GiST index
    * on `places.geom`, and a cross join of 15 000 sites × 1 000 findings is not free. The expansion
    * is in DEGREES, widened by `1/cos(latitude)` so it is always a superset of the true circle. */
  /** Which source wins when two describe the same state at the same time: 1 = OpenHistoricalMap,
    * 2 = every other real dataset (Cliopatria/Seshat, Pleiades, Natural Earth), 3 = the agent-drawn
    * sketches of `db/places/polities` — shown only where nothing better covers them. */
  private[db] val sourceTierSql: String =
    "CASE WHEN p.properties->>'method' = 'ohm-import' THEN 1" +
      " WHEN p.properties->>'method' = 'agent-drawn' THEN 3 ELSE 2 END"

  /** A label reduced to what identifies the state: lower case, no parentheses, no filler words
    * (of/the/and), no punctuation — so "Old Kingdom of Egypt" and "Old Kingdom Egypt" meet. */
  private[db] def normLabelSql(col: String): String =
    s"regexp_replace(regexp_replace(lower($col), '\\m(of|the|and)\\M', '', 'g'), '[^a-z0-9]+', '', 'g')"

  /** Share of the SMALLER outline covered by the other above which two same-kind shapes from
    * different sources are taken to be the same state (different names, e.g. "Palatial Minoan Crete"
    * vs "Minoan civilization"). */
  private[db] val SameStateOverlap = 0.5

  /** Labels that name nothing: Pleiades records a site with no known ancient name as "Untitled",
    * "Unnamed place", "Unnamed villa"… Drawn on the map they are noise ("Unnamed place" beside
    * Misrata), so the map skips them; the records stay (findings may be assigned to them). A label
    * that says more — "Unnamed Gate of the Themistoklean Wall" — is kept. */
  private[db] val UninformativeLabel = """^\s*(untitled|unnamed(\s+\w+)?)\s*$"""

  /** A PRESENT-DAY place (`properties.modern`, the Natural Earth cities and physical features, #987):
    * undated, so valid at every year — kept for finding a name and placing a finding. A view at a
    * time shows a city only from `places.modern-from` ("Kraków" would sit among the sites of 1400
    * BCE) and a physical feature at any time ([[inTimeSql]]); the map across all time draws neither. */
  private[db] val ModernSql = modernSql("p")

  /** [[ModernSql]] over a `places` row aliased `alias`. */
  private[db] def modernSql(alias: String): String = s"coalesce(($alias.properties->>'modern')::boolean, false)"

  // ── the time rule (2026-10-09): what a view AT A TIME shows ─────────────────────────────────────

  /** The configured `places.modern-from` for callers that hold no config (the tests). It MUST equal
    * `reference.conf`; `PlaceTimeSuite` asserts it. Every production read passes the configured value. */
  val DefaultModernFrom: Long = 1500L

  /** A stored period open on both sides: the source states no date. An unknown start (V074) is not —
    * it stores a window. */
  private def undatedSql(alias: String): String = s"(lower_inf($alias.period) AND upper_inf($alias.period))"

  /** A physical feature of the seed: there at any time ([[PlaceTimeBasis.Timeless]]). */
  private def timelessSql(alias: String): String =
    s"(NOT ${importedSql(alias)} AND ($alias.kind = 'feature' OR (${modernSql(alias)} AND $alias.kind = 'region')))"

  /** [[PlaceTimeBasis.of]] in SQL, over a `places` row aliased `alias`: the basis's wire name. */
  private[db] def timeBasisSql(alias: String): String =
    s"CASE WHEN ${timelessSql(alias)} THEN 'timeless' WHEN ${modernSql(alias)} THEN 'modern'" +
      s" WHEN ${undatedSql(alias)} AND $alias.geom IS NULL THEN 'grouping'" +
      s" WHEN ${undatedSql(alias)} THEN 'findings' ELSE 'dated' END"

  /** The window of a time view as a one-row CTE `tw(w, modern_on)` for the year `asOf`: `w` the
    * window (`NULL` = no time, nothing is filtered), `modern_on` whether it reaches `modern-from`.
    * [[inTimeSql]] reads it; every time-filtered place query starts with it. */
  private[db] def timeWindowCte(asOf: Option[Long], modernFrom: Long): Sql =
    windowCte(asOf.map(y => (y, y)), modernFrom)

  /** [[timeWindowCte]] for the inclusive window `lo..hi` (the map's area layer over a whole range,
    * [[GeoTime.Within]]): `modern_on` when its END reaches `modern-from`. A year is the window `y..y`. */
  private[db] def windowCte(window: Option[(Long, Long)], modernFrom: Long): Sql =
    import Param.*
    window.fold(Sql.raw("tw AS (SELECT NULL::int8range AS w, false AS modern_on)"))((lo, hi) =>
      Sql.of("tw AS (SELECT int8range(?::bigint, ?::bigint, '[]') AS w, ?::bigint >= ?::bigint AS modern_on)",
        L(lo), L(hi), L(hi), L(modernFrom)))

  /** [[PlaceTimeBasis.visible]] in SQL, over a `places` row aliased `alias`, at the year `asOf`
    * against the window of the `tw` CTE ([[timeWindowCte]]); no time filters nothing. A dated place
    * overlaps the window; a physical feature is always there; a present-day place only from
    * `modern-from`; an undated node with no shape is kept (a tree goes on through it); any other
    * undated place only when a finding or an event DATED within the window is attached to it — in
    * [[timeBasisSql]]'s order.
    *
    * Led by an overlap with the year as a LITERAL (a `Long`, so nothing to escape): every place the
    * rule shows overlaps the window (an undated one is open on both sides) except a physical feature
    * dated outside it, so `period && [y, y]` — OR'ed with "physical" only where `kind` can be one —
    * is implied by the rule. Read from the CTE (`(SELECT tw.w FROM tw)`, an init-plan whose value the
    * planner cannot see) the overlap was estimated at 13 000 rows and `places_period_gist` was AND'ed
    * into every candidate scan — 1.19 M rows, 40 ms of a place search's 47, because every undated
    * import overlaps every year (2026-10-09). With the year visible the planner knows the overlap
    * keeps nearly everything and finds the candidates by what does narrow them: the trigram index of
    * a search, the (kind, period) GiSTs of a map layer (V076). */
  private[db] def inTimeSql(alias: String, asOf: Option[Long], kind: Option[String] = None): String =
    inWindowSql(alias, asOf.map(y => (y, y)), kind)

  /** [[inTimeSql]] over the inclusive window `lo..hi` ([[windowCte]]): shown at ANY year of it. */
  private[db] def inWindowSql(alias: String, window: Option[(Long, Long)], kind: Option[String] = None): String =
    window.fold("true") { (lo, hi) =>
      val w        = "(SELECT tw.w FROM tw)"
      val modern   = modernSql(alias)
      val undated  = undatedSql(alias)
      val timeless = timelessSql(alias)
      val overlap  = s"$alias.period && int8range(${lo}::bigint, ${hi}::bigint, '[]')"
      val physicalKind = kind.forall(k => k == PlaceKind.Feature || k == PlaceKind.Region)
      val lead     = if physicalKind then s"($overlap OR $timeless)" else overlap
      s"($lead AND ($timeless" +
        s" OR ($modern AND (SELECT tw.modern_on FROM tw))" +
        s" OR (NOT $modern AND NOT $undated)" +
        s" OR (NOT $modern AND $undated AND $alias.geom IS NULL)" +
        s" OR (NOT $modern AND $undated AND $alias.geom IS NOT NULL AND (" +
        s"EXISTS (SELECT 1 FROM finding_places fp JOIN findings f ON f.id = fp.finding_id" +
        s" WHERE fp.place_id = $alias.id AND f.period && $w AND NOT (lower_inf(f.period) AND upper_inf(f.period)))" +
        s" OR EXISTS (SELECT 1 FROM events e WHERE e.place_id = $alias.id AND e.outer_span && $w" +
        s" AND NOT (lower_inf(e.outer_span) AND upper_inf(e.outer_span)))))))"
    }

  /** The SQL of [[geoJson]], as a value — pure, so the de-duplication rules are unit-tested. */
  /** The BASE layers (polity, region, site, feature) draw the curated gazetteer: the bundled seed, a
    * person's own places and LPF datasets. A GeoNames or Wikidata place is drawn only once a finding
    * is attached to it (`finding_places`) — a picked or searched place is drawn by the place API
    * instead. Explicit here, not left to ranking: 1.2 M imported points would otherwise fill the map
    * with dots that are neither sites nor boundaries. Over a `places` row aliased `p`. */
  private[db] val BaseLayerSql =
    "(p.import_source IS NULL OR p.import_source NOT IN ('wikidata', 'geonames')" +
      " OR EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id))"

  /** The curated half of [[BaseLayerSql]] — word for word the predicate of the partial index
    * `places_base_layer` (V075), which is what lets the planner use it. */
  private[db] val CuratedSql = "(p.import_source IS NULL OR p.import_source NOT IN ('wikidata', 'geonames'))"

  /** NOT a present-day GeoNames/Wikidata place — word for word the predicate of the partial indexes
    * `places_before_modern` and `places_before_modern_label_trgm` (V076). A view at a year before
    * `places.modern-from` never shows such a place ([[inTimeSql]]: it is neither a physical feature,
    * being imported, nor shown by findings, being present-day), so there this is implied by the time
    * rule and only tells the planner which indexes hold the candidates. */
  private[db] val ShownBeforeModernSql = s"NOT (${importedSql("p")} AND ${modernSql("p")})"

  /** A DATED GeoNames/Wikidata place — word for word the predicate of the partial index
    * `places_dated_import` (V076). At a year the imported half of a base layer is exactly these (an
    * undated import is never drawn then), and saying so lets the planner read the few hundred alive
    * that year instead of every imported place a finding is attached to. */
  private[db] val DatedImportSql =
    "(p.import_source IN ('wikidata', 'geonames') AND NOT (lower_inf(p.period) AND upper_inf(p.period)))"

  /** Not an undated polity known only by a point, over a `places` row aliased `p`: a map at a year
    * never draws one, findings or not (region-cleanup, 2026-10-09) — a state is not "always". */
  private[db] val UndatedPolityPointSql =
    "NOT (p.kind = 'polity' AND ST_Dimension(p.geom) = 0 AND lower_inf(p.period) AND upper_inf(p.period))"

  private[db] def geoJsonQuery(kind: Option[String], asOf: Option[Long], limit: Int,
      modernFrom: Long = DefaultModernFrom): Sql =
    geoJsonTimeQuery(kind, asOf.fold(GeoTime.Always)(GeoTime.At(_)), limit, modernFrom)

  /** [[geoJsonQuery]] for any [[GeoTime]]: across all time, at a year, or over a whole range. */
  private[db] def geoJsonTimeQuery(kind: Option[String], time: GeoTime, limit: Int,
      modernFrom: Long = DefaultModernFrom): Sql =
    import Param.*
    val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val window = time.window
    val within = time match
      case w: GeoTime.Within => Some(w)
      case _                 => None
    val where = List(Some(Sql.raw("p.geom IS NOT NULL")),
      Some(Sql.of("p.label !~* ?", S(UninformativeLabel))),
      // across all time a present-day place is never drawn; at a year, the time rule decides
      // (physical features always, present-day places from `modern-from`, undated ones by findings)
      Some(Sql.raw(if window.isEmpty then s"NOT $ModernSql" else inWindowSql("p", window, k))),
      k.map(v => Sql.of("p.kind = ?", S(v))),
      // an undated import is never on a time-sliced map, findings or not (its open range is unknown)
      window.map(_ => Sql.raw(s"NOT $UndatedImportSql")),
      // nor is a polity known only by a point and no date (a late Roman province Pleiades does not
      // date, `lint --rule place-kind`): a state is not "always"
      window.map(_ => Sql.raw(UndatedPolityPointSql))).flatten
    // `live` carries no geometry — copying 138 large polygons into a CTE's tuplestore cost ~2 s;
    // the full shapes are read from `places` only for candidate pairs and for the rows returned.
    //
    // [[BaseLayerSql]] in its two disjoint halves, read apart: the curated places through
    // `places_base_layer` (V075), and the imported ones a finding is attached to through
    // `finding_places`. As one OR the sites layer read all 930 000 sites alive in a year to keep
    // 6 400 (0.4 s); apart, 58 ms for the same rows. Each half carries its time basis (the window
    // `tw` heads the statement), so the ranking below sees why each place is shown. Over a range
    // each row also carries its period (`per`): which shapes coexist, and which version comes first.
    val cols = Sql.raw("SELECT p.id, p.kind, " +
      s"$sourceTierSql AS tier, ${normLabelSql("p.label")} AS norm, ST_Area(p.geom) AS area, " +
      s"p.geom::box2d AS bb, ${timeBasisSql("p")} AS basis${if within.isDefined then ", p.period AS per" else ""}" +
      " FROM places p WHERE ")
    val rest = Sql(where.map(_.text).mkString(" AND "), where.flatMap(_.params))
    // At a year both halves are read through a (kind, period) GiST (V076): `places_base_layer_time`
    // (the curated predicate) and `places_dated_import` ([[DatedImportSql]], implied there by "not
    // curated" and "not an undated import" — stated so the planner can use the index). The few
    // hundred dated imports alive then are each probed for a finding (`OFFSET 0` keeps the EXISTS a
    // per-row probe): as a semi-join the planner, which cannot estimate the year's overlap within a
    // partial index, read every imported place with a finding instead — 9 500 lookups, 35 ms a year.
    // Across all time the semi-join is the right shape and stays.
    val imported =
      if window.isEmpty then s" AND NOT $CuratedSql AND p.id IN (SELECT fp.place_id FROM finding_places fp)"
      else s" AND NOT $CuratedSql AND $DatedImportSql" +
        " AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id OFFSET 0)"
    val live = Sql.raw("WITH ") ++ windowCte(window, modernFrom) ++ Sql.raw(", live AS (") ++
      cols ++ rest ++ Sql.raw(s" AND $CuratedSql") ++ Sql.raw(" UNION ALL ") ++
      cols ++ rest ++ Sql.raw(imported) ++ Sql.raw(")")
    // only a time view collapses duplicates: across all time, successive shapes are all real
    val shadowed =
      if window.isEmpty then Sql.raw(", shadowed AS (SELECT NULL::text AS id WHERE false)")
      else within.fold(Sql.raw(
        // a shape is shadowed only by a LOWER tier: a lone tier (every site is tier 2) has no pairs,
        // and pairing its 6 400 rows with each other first took 3 s a year (2026-10-09)
        ", shadowed AS (SELECT DISTINCT lo.id" +
          " FROM (SELECT * FROM live WHERE tier > (SELECT min(tier) FROM live)) lo" +
          " JOIN (SELECT * FROM live WHERE tier < (SELECT max(tier) FROM live)) hi" +
          " ON hi.tier < lo.tier AND hi.kind = lo.kind AND hi.bb && lo.bb" +
          // each shape fetched by id for its candidate pair (`OFFSET 0` keeps the planner from
          // flattening it back into a join): `live` is a few dozen rows but estimated at thousands,
          // and as a plain join the planner merged it with ALL 1.2 M places by key — 200 of the
          // 240 ms a polity layer took per year (2026-10-09)
          " CROSS JOIN LATERAL (SELECT geom FROM places WHERE id = hi.id OFFSET 0) gh" +
          " CROSS JOIN LATERAL (SELECT geom FROM places WHERE id = lo.id OFFSET 0) gl" +
          " WHERE hi.norm = lo.norm OR (ST_Intersects(gh.geom, gl.geom)" +
          " AND ST_Area(ST_Intersection(ST_MakeValid(gh.geom), ST_MakeValid(gl.geom)))" +
          s" > $SameStateOverlap * least(hi.area, lo.area)))"))(_ => Sql.raw(rangeShadowedSql))
    // what is drawn: every unshadowed live shape; over a range, one VERSION of each state —
    // [[pickVersionSql]]'s `shown`
    val (shown, drawn) = within.fold((Sql.raw(""), "live WHERE id NOT IN (SELECT id FROM shadowed)"))(w =>
      (Sql.raw(pickVersionSql(w)), "shown"))
    // what was there then before what is merely shown then: among equal areas (every point) a dated
    // place outranks one dated by its findings, a physical feature and a present-day one — at 1600 the
    // 4 000 Natural Earth cities would otherwise take the whole site limit by id
    def rank(l: String) =
      s"${l}area DESC, CASE ${l}basis WHEN 'dated' THEN 0 WHEN 'findings' THEN 1 WHEN 'timeless' THEN 2 ELSE 3 END, ${l}id"
    // RANK FIRST, then read the rows shown: the limit is taken over `live` alone, and only those rows
    // are joined back to `places` for their label, dates and shape — not all 14 700 sites alive in
    // 500 CE, one index lookup each, to keep 200 (2026-10-09)
    live ++ shadowed ++ shown ++ Sql.raw(
      s" SELECT l.id, p.label, l.kind, $startCol AS ps, upper(p.period) - 1 AS pe, $datingCol AS dating," +
        " l.basis," +
        " coalesce(ST_Dimension(p.geom) = 2, false) AS has_boundary," +
        // a polity is drawn on land when the land is loaded (V025); `geom` stays the published shape
        " ST_AsGeoJSON(coalesce(p.geom_clipped, p.geom), 5) AS geojson," +
        // what a site is (#1449): a stored refinement, else its gazetteer's own fields (core.SiteTypes)
        " p.site_type, p.properties->>'feature_types', p.properties->>'fcode', p.properties->>'class'" +
        s" FROM (SELECT * FROM $drawn ORDER BY ${rank("")}") ++
      Sql.of(" LIMIT ?", I(limit.max(1))) ++
      Sql.raw(s") l JOIN places p ON p.id = l.id ORDER BY ${rank("l.")}")

  /** The CTE `shadowed` over a whole range ([[GeoTime.Within]]): the rule of a year — a shape is
    * shadowed by a LOWER tier's shape of the same normalised name or covering [[SameStateOverlap]] of
    * the smaller — among shapes that COEXIST (`per && per`; at a year every live row does, and two
    * periods that overlap each other overlap the range there too). The pairs are read as the two small
    * tiers against the rest — the sketches (3) against every better shape, OHM (1) against the
    * datasets (2), [[sourceTierSql]] — because as one `tier <` join the 13 000 shapes of all time met
    * each other: 170 M comparisons, past the statement timeout; split, 970 pairs in 0.3 s for the whole
    * of -3000..2000 (2026-10-16). Each shape is fetched by id for its pair, as at a year. */
  private[db] val rangeShadowedSql: String =
    def pairs(lo: String, hi: String) =
      "SELECT lo.id AS lid, hi.id AS hid, lo.norm = hi.norm AS same_name, least(hi.area, lo.area) AS small" +
        s" FROM (SELECT * FROM live WHERE $lo) lo JOIN (SELECT * FROM live WHERE $hi) hi" +
        " ON hi.tier < lo.tier AND hi.kind = lo.kind AND hi.bb && lo.bb AND hi.per && lo.per"
    ", shadowed AS (SELECT DISTINCT pr.lid AS id FROM (" + pairs("tier = 3", "tier < 3") + " UNION ALL " +
      pairs("tier = 2", "tier = 1") + ") pr" +
      " CROSS JOIN LATERAL (SELECT geom FROM places WHERE id = pr.hid OFFSET 0) gh" +
      " CROSS JOIN LATERAL (SELECT geom FROM places WHERE id = pr.lid OFFSET 0) gl" +
      " WHERE pr.same_name OR (ST_Intersects(gh.geom, gl.geom)" +
      " AND ST_Area(ST_Intersection(ST_MakeValid(gh.geom), ST_MakeValid(gl.geom)))" +
      s" > $SameStateOverlap * pr.small))"

  /** The CTE `shown` over a whole range `w` ([[GeoTime.Within]]): ONE VERSION of each state alive at
    * any time in it. The unshadowed live shapes are grouped into "the same state over time" by kind
    * and NORMALISED LABEL ([[normLabelSql]]; a label that normalises to nothing stands alone) — never
    * by overlap, because successive states on one territory (the Hittites, then Phrygia; Middle and
    * New Kingdom Egypt) overlap heavily and are different states, and never by Wikidata id, which
    * Cliopatria gives to whole families of coexisting polygons (the Eighteen Kingdoms of 206 BCE share
    * one; an alliance polygon carries its senior partner's). Each shape is clipped to the range
    * (`es..ee`, an open side read as the range's); of each group the shapes alive at its FIRST year
    * there are kept (`latest`: its LAST year) — the state as the range first (last) shows it, so two
    * shapes of one state alive together then both stay, as at a year. Cross-tier duplicates are gone
    * already (`shadowed`, among shapes that coexist), and different states may overlap: all are drawn. */
  private[db] def pickVersionSql(w: GeoTime.Within): String =
    val (lo, hi) = (w.from, w.to)
    val (edge, anchor) = if w.latest then ("ee", "max") else ("es", "min")
    ", kept AS (SELECT live.*," +
      s" greatest(${lo}::bigint, coalesce(lower(per), ${lo}::bigint)) AS es," +
      s" least(${hi}::bigint, coalesce(upper(per) - 1, ${hi}::bigint)) AS ee" +
      " FROM live WHERE id NOT IN (SELECT id FROM shadowed))" +
      ", shown AS (SELECT id, kind, tier, norm, area, bb, basis FROM (SELECT kept.*," +
      s" $anchor($edge) OVER (PARTITION BY kind, coalesce(nullif(norm, ''), id)) AS anchor FROM kept) v" +
      s" WHERE v.$edge = v.anchor)"

  /** `GET /api/places/geo` — a FeatureCollection for the map. When `asOf` is given, each state is
    * drawn ONCE: of shapes from different source tiers that share a normalised name or overlap by
    * [[SameStateOverlap]], only the best tier survives (within one source nothing is dropped —
    * Cliopatria's overlapping vassal/allegiance polygons are real). The limit applies after that,
    * largest shapes first, so it drops the smallest rather than whatever sorts last by name. */
  def geoJson(ds: DataSource, kind: Option[String], asOf: Option[Long], limit: Int,
      modernFrom: Long = DefaultModernFrom): String =
    geoJsonAt(ds, kind, asOf.fold(GeoTime.Always)(GeoTime.At(_)), limit, modernFrom)

  /** [[geoJson]] for any [[GeoTime]]. Over a range ([[GeoTime.Within]]) every state alive at any time
    * in it is drawn, one version each ([[pickVersionSql]]), after the same cross-tier collapse among
    * the shapes that coexist; the limit still comes last, largest first. */
  def geoJsonAt(ds: DataSource, kind: Option[String], time: GeoTime, limit: Int,
      modernFrom: Long = DefaultModernFrom): String =
    connect(ds):
      val rows = geoJsonTimeQuery(kind, time, limit, modernFrom).frag
        .query[(String, String, String, Option[Long], Option[Long], PlaceDating, PlaceTimeBasis, Boolean, String,
          Option[String], Option[String], Option[String], Option[String])].run()
      val features = rows.map { (id, label, placeKind, ps, pe, dating, basis, hasBoundary, geojson,
          stored, featureTypes, fcode, wdClass) =>
        val siteType = SiteTypes.of(id, placeKind, stored, featureTypes, fcode, wdClass)
        import io.circe.Json
        val propsObj = Json.obj(
          "id" -> Json.fromString(id),
          "label" -> Json.fromString(label),
          "kind" -> Json.fromString(placeKind),
          // a region or polity drawn from a point has no boundary yet: the map labels it, never a dot
          "hasBoundary" -> Json.fromBoolean(hasBoundary),
          "dating" -> Json.fromString(dating.wire),
          // why a time view shows it ([[PlaceTimeBasis]]): `findings` is drawn as "dated by its findings"
          "basis" -> Json.fromString(basis.wire)
        ).deepMerge(Json.fromFields(
          ps.map(v => "periodStart" -> Json.fromLong(v)).toList ++
          pe.map(v => "periodEnd" -> Json.fromLong(v)).toList ++
          siteType.map(t => "siteType" -> Json.fromString(t)).toList
        ))
        s"""{"type":"Feature","properties":${propsObj.noSpaces},"geometry":$geojson}"""
      }
      s"""{"type":"FeatureCollection","features":[${features.mkString(",")}]}"""

  def rebuildFindingPlaces(ds: DataSource, toleranceKm: Double): PlaceRebuildResult =
    transact(ds):
      // Lock in a FIXED order — `findings` first, then `finding_places` — before touching anything.
      // Without it the INSERT…SELECT can see (in its snapshot) a finding another transaction has
      // just deleted and then fail the foreign key; and two rebuilds, or a rebuild and a
      // `put_source`, could take the two tables in opposite orders and deadlock. A rebuild therefore
      // holds up source writes for its (few seconds') duration — it is an explicit admin action.
      // (`materialiseFor` touches the same two tables in the same order, for the same reason.)
      val _ = sql"LOCK TABLE findings IN SHARE MODE".update.run()
      val _ = sql"LOCK TABLE finding_places IN EXCLUSIVE MODE".update.run()
      val _ = sql"DELETE FROM finding_places".update.run()
      val (byGeometry, byName) = insertAssignments(None, toleranceKm)
      val links   = sql"SELECT count(*)::int FROM finding_places".query[Int].run().head
      val located = sql"SELECT count(DISTINCT finding_id)::int FROM finding_places".query[Int].run().head
      val total   = sql"SELECT count(*)::int FROM findings".query[Int].run().head
      val places  = sql"SELECT count(*)::int FROM places".query[Int].run().head
      PlaceRebuildResult(links, byGeometry, byName, located, total - located, places)

  /** Build parent-child edges from spatial containment: every point place that falls inside a polygon
    * place gets a `spatial_containment` relation edge. Idempotent (`ON CONFLICT DO NOTHING`).
    * Run after imports to wire up the hierarchy that Pleiades and Cliopatria don't provide. */
  def buildSpatialHierarchy(ds: DataSource): SpatialHierarchyResult =
    transact(ds):
      val polygons = sql"SELECT count(*)::int FROM places WHERE geom IS NOT NULL AND ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')".query[Int].run().head
      val points   = sql"SELECT count(*)::int FROM places WHERE geom IS NOT NULL AND ST_GeometryType(geom) = 'ST_Point'".query[Int].run().head
      val inserted = sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
            SELECT site.id, region.id, 'spatial_containment', region.period
            FROM places site, places region
            WHERE ST_GeometryType(region.geom) IN ('ST_Polygon', 'ST_MultiPolygon')
              AND ST_GeometryType(site.geom) = 'ST_Point'
              AND NOT ${SqlLiteral(importedSql("site"))} AND NOT ${SqlLiteral(importedSql("region"))}
              AND ST_Contains(region.geom, site.geom)
              AND site.id != region.id
            ON CONFLICT (child_id, parent_id, kind) DO NOTHING""".update.run()
      SpatialHierarchyResult(edgesCreated = inserted, polygonPlaces = polygons, pointPlaces = points)

  /** Fallback point tolerance for callers that hold no config — the legacy `SourceRepo.upsert`
    * shape and the tests. It MUST equal `places.point-tolerance-km` in `reference.conf`;
    * `PlaceRepoSuite` asserts exactly that, so the two cannot drift. Every production write passes
    * the configured value explicitly. */
  val DefaultToleranceKm: Double = 10.0

  /** Re-materialise `finding_places` for ONE citekey, inside the CALLER's transaction.
    *
    * This exists because every source write used to `DELETE FROM findings`, and `finding_places`
    * cascades off `findings(id)` — so `patch_source {notes}` silently un-located every finding of
    * that source until somebody remembered to run `places rebuild`. The write path now upserts the
    * findings and calls this, in the same transaction, so the gazetteer assignment is never
    * observable as missing. Same table order as [[rebuildFindingPlaces]]: findings, then
    * finding_places. */
  def materialiseFor(citekey: String, toleranceKm: Double)(using DbCon): Int =
    val _ = sql"""DELETE FROM finding_places fp
                  USING findings f
                  WHERE f.id = fp.finding_id AND f.citekey = $citekey""".update.run()
    val (byGeometry, byName) = insertAssignments(Some(citekey), toleranceKm)
    byGeometry + byName

  /** Re-materialise the `finding_places` rows of the given PLACES, inside the CALLER's transaction —
    * after a place's kind, point or identity changed (`lint --rule place-kind --fix`,
    * `--rule place-grid-point --fix`): the same inserts as a rebuild, restricted to those places, so a
    * fix takes seconds instead of a whole rebuild's half minute (live 2026-10-09). Same table order as
    * [[rebuildFindingPlaces]]. Returns the rows written. */
  def materialisePlaces(placeIds: List[String], toleranceKm: Double)(using DbCon): Int =
    val ids = placeIds.distinct
    if ids.isEmpty then 0
    else
      val _ = sql"LOCK TABLE findings IN SHARE MODE".update.run()
      val _ = sql"DELETE FROM finding_places WHERE place_id = ANY($ids::text[])".update.run()
      val (byGeometry, byName) = insertAssignments(None, toleranceKm, Some(ids))
      byGeometry + byName

  /** A seed place whose POINT stands for something larger than a point (2026-10-09), over a `places`
    * row aliased `alias`: a region, polity or people with no boundary (`hasBoundary` false — all 1 617
    * Pleiades and 75 curated regions), or any Pleiades point that is only the centre of an atlas grid
    * square ([[LocationPrecision.GridCell]]). Such a point is no evidence of nearness: by distance the
    * 13 Regiones of Rome each took the same 59 findings, Indo-European language spread among them, and
    * the Athenian tribe Aiantis 108 — Achilles' name, the Presocratics, steppe ancestry. A finding
    * belongs to one only by an EXPLICIT match ([[insertAssignments]]). Imported places have their own,
    * stricter rule. */
  private[db] def explicitOnlySql(alias: String): String =
    s"coalesce(ST_Dimension($alias.geom) = 0 AND (coalesce($alias.kind, '') NOT IN ('site', 'feature')" +
      s" OR coalesce($alias.location_precision = 'grid-cell', false)), false)"

  /** The placeholder minimum the assignment's geocoder pass ranks with ([[Geocode.isPlaceholder]]).
    * It MUST equal `places.geocode-placeholder-min` in `reference.conf`; `PlaceRepoSuite` asserts it. */
  val DefaultPlaceholderMin: Int = 4

  /** The share of a placeholder finding's named place an area must cover to take the finding. It MUST
    * equal `places.placeholder-area-share` in `reference.conf`; `PlaceholderPointSuite` asserts it. */
  val DefaultPlaceholderAreaShare: Double = 0.25

  /** A finding whose POINT is a PLACEHOLDER (2026-10-09), over a `findings` row aliased `f` — a point
    * that stands for "somewhere in this area", not "here":
    *   - the centre of an atlas grid square ([[LocationPrecision.gridCentre]]): 87 findings, every one
    *     a broad name ("Greece", "Aegean", "Korean Peninsula", "Pontic-Caspian steppe") at round
    *     half-degrees — 12 of them on "Greece" at 37.5 N 22.5 E, the point Pleiades gives six places;
    *   - the representative point (`ST_PointOnSurface`) of an area or line of the curated gazetteer
    *     that is no site — what the geocoder gives a region's, sea's or river's name;
    *   - the point of an explicit-only place ([[explicitOnlySql]]: a region, polity or people known
    *     only by a point).
    * Such a point is no evidence of nearness: by the 10 km tolerance the "Greece" findings had joined
    * Tegea, Hysiai and the Temple of Athena Alea, and by containment the Peloponnese. A placeholder
    * finding belongs to what its NAME says ([[insertAssignments]]). Within 1e-6° (0.1 m) of the point. */
  private[db] def placeholderSql(f: String): String =
    // the two EXISTS read the partial indexes `places_rep_point` and `places_explicit_point` (V080),
    // whose predicates these are word for word
    s"(CASE WHEN $f.geom IS NULL OR GeometryType($f.geom) <> 'POINT' THEN false" +
      s" WHEN histmersion_grid_centre(ST_X($f.geom), ST_Y($f.geom)) THEN true" +
      s" ELSE EXISTS (SELECT 1 FROM places q WHERE ST_PointOnSurface(q.geom) && ST_Expand($f.geom, 1e-6)" +
      s" AND ST_Dimension(q.geom) > 0 AND q.kind <> 'site' AND NOT ${importedSql("q")}" +
      s" AND ST_DWithin(ST_PointOnSurface(q.geom), $f.geom, 1e-6))" +
      s" OR EXISTS (SELECT 1 FROM places q WHERE q.geom && ST_Expand($f.geom, 1e-6)" +
      s" AND NOT ${importedSql("q")} AND ${explicitOnlySql("q")} AND ST_DWithin(q.geom, $f.geom, 1e-6)) END)"

  private final case class Placeholder(id: String, name: Option[String], from: Option[Long], to: Option[Long],
      citekey: String, lon: Double, lat: Double)
  private given DbCodec[Placeholder] = DbCodec.derived

  /** Every placeholder finding: id, place name, period, citekey and point (`lint --rule finding-placeholder`). */
  val placeholderFindingsSql: String =
    "SELECT f.id, nullif(btrim(f.region_name), ''), lower(f.period), upper(f.period) - 1, f.citekey," +
      s" ST_X(f.geom), ST_Y(f.geom) FROM findings f WHERE f.geom IS NOT NULL AND ${placeholderSql("f")}"

  /** The placeholder findings whose place name the geocoder does not resolve, or that have none — what
    * `lint --rule finding-placeholder` lists, as (citekey, line), by citekey and finding id. */
  def unresolvedPlaceholdersIn()(using DbCon): List[(String, String)] =
    val ph = (Sql.raw(placeholderFindingsSql) ++ Sql.raw(" ORDER BY f.citekey, f.id")).frag.query[Placeholder].run().toList
    val resolved = resolve(ph).map(_._1).toSet
    ph.filterNot(p => resolved.contains(p.id)).map { p =>
      val at = String.format(java.util.Locale.ROOT, "%.4f N %.4f E", p.lat, p.lon)
      p.citekey -> p.name.fold(s"${p.id}: no place name, placeholder point at $at")(n =>
        s"${p.id}: '$n' names no gazetteer place; placeholder point at $at")
    }

  /** Finding id → the place its name resolves to, for the placeholders that have a name the geocoder
    * resolves (as a region). One resolution per (name, period). */
  private def resolve(ph: List[Placeholder])(using DbCon): List[(String, String)] =
    ph.flatMap(p => p.name.map(n => (n, p))).groupBy((n, p) => (n, Geocode.periodOf(p.from, p.to))).toList
      .flatMap { case ((name, at), fs) =>
        Geocoder.resolveIn(name, Geocode.KindHint.Region, at, DefaultPlaceholderMin).toList
          .flatMap(r => fs.map((_, f) => f.id -> r.placeId))
      }

  /** The placeholder findings ([[placeholderSql]]) — of `citekey`, or all — and, for those whose place
    * NAME the geocoder resolves ([[Geocoder.resolveIn]], as a region), the place it resolves to:
    * (every placeholder id, finding id → place id). */
  private def placeholders(citekey: Option[String])(using DbCon): (List[String], List[(String, String)]) =
    val ph = (Sql.raw(placeholderFindingsSql) ++
      citekey.fold(Sql.raw(""))(ck => Sql.of(" AND f.citekey = ?", Param.S(ck)))).frag.query[Placeholder].run().toList
    (ph.map(_.id), resolve(ph))

  /** The inserts that define "a finding belongs to a place", shared by the full rebuild and the
    * per-citekey re-materialisation — one definition, so they cannot disagree about what the
    * assignment means. `citekey = None` means the whole corpus. Returns (byGeometry, byName).
    *
    *   - an AREA (a polygon or line) holds the findings it covers;
    *   - a SITE or FEATURE point holds the findings within the point tolerance;
    *   - an EXPLICIT-ONLY point ([[explicitOnlySql]]: a point-only region, polity or people, a grid
    *     square's centre) holds the findings whose place name the geocoder resolves to that place and —
    *     unless it is a grid square's centre, which many places share — the findings placed ON that
    *     very point; an imported point only the latter;
    *   - a finding with no point is matched by its name to any other place's name;
    *   - a finding whose point is a PLACEHOLDER ([[placeholderSql]]) takes no site or feature by the
    *     tolerance; when its name resolves, it belongs to that place and to the areas covering at least
    *     [[DefaultPlaceholderAreaShare]] of it (or its point, when it has no shape) — "Greece" to Greece,
    *     the Balkan Peninsula and Mycenaean Greece, not to the Peloponnese — and not to areas merely
    *     covering the point; when it does not resolve, areas still take it by containment
    *     (`lint --rule finding-placeholder` lists those for a person). */
  private def insertAssignments(citekey: Option[String], toleranceKm: Double, places: Option[List[String]] = None)(
      using DbCon): (Int, Int) =
    // `places`: only the links of these places (a reclassified, merged or moved place, [[materialisePlaces]])
    val anyPlace = places.isEmpty
    val pids     = places.getOrElse(Nil)
    val meters  = toleranceKm.max(0.0) * 1000.0
    val degrees = toleranceKm.max(0.0) / 111.32
    // placeholder points: no site by distance; a resolved name decides the areas ([[byPlaceholder]])
    val (phIds, phNamed) = placeholders(citekey)
    val phResolved       = phNamed.map(_._1).distinct
    // TWO statements, not one CASE: a CASE around the spatial predicate hides it from the planner
    // and turns the join into a 964 x 16 700 cross product (minutes). Split, each side is an
    // index-driven nested loop over `places_geom_gist` and the whole rebuild takes a few seconds.
    val byArea =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p ON ST_CoveredBy(f.geom, p.geom)
            WHERE f.geom IS NOT NULL AND p.geom IS NOT NULL AND ST_Dimension(p.geom) > 0
              AND NOT ${SqlLiteral(importedSql("p"))}
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND (f.period IS NULL OR f.period && p.period)
              AND NOT (f.id = ANY($phResolved::text[]))
            ON CONFLICT DO NOTHING""".update.run()
    val byPoint =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p
              ON p.geom && ST_Expand(f.geom,
                   $degrees / greatest(cos(radians(ST_Y(f.geom))), 0.01))
            WHERE f.geom IS NOT NULL AND p.geom IS NOT NULL AND ST_Dimension(p.geom) = 0
              AND NOT ${SqlLiteral(importedSql("p"))}
              AND NOT ${SqlLiteral(explicitOnlySql("p"))}
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND NOT (f.id = ANY($phIds::text[]))
              AND ST_DWithin(f.geom::geography, p.geom::geography, $meters)
              AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
    // a GeoNames/Wikidata point, and an explicit-only seed point, get the findings the geocoder put ON
    // it (the point it chose for the finding's place name), never its neighbours': a 10 km circle round
    // a modern village is no evidence (live 2026-10-09: 192 k such rows), nor one round a tribe's
    // representative point. A grid square's centre not even that: Pleiades puts six places on
    // 37.5 N 22.5 E, and 12 findings a model placed on "Greece" at those round numbers had joined all
    // six — such a place takes a finding only by its name ([[byResolution]])
    // (the CASE: ST_X of a polygon the tiny box also hits is an error, and only a CASE orders the
    // evaluation; the plain coordinate test is 3x cheaper than ST_DWithin over 1.2 M imported points)
    val byGeocode =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p ON p.geom && ST_Expand(f.geom, 1e-7)
            WHERE f.geom IS NOT NULL
              AND CASE WHEN GeometryType(p.geom) = 'POINT'
                       THEN abs(ST_X(p.geom) - ST_X(f.geom)) < 1e-7 AND abs(ST_Y(p.geom) - ST_Y(f.geom)) < 1e-7
                       ELSE false END
              AND (${SqlLiteral(importedSql("p"))}
                   OR (${SqlLiteral(explicitOnlySql("p"))} AND NOT coalesce(p.location_precision = 'grid-cell', false)))
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
    val byName =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT DISTINCT f.id, p.id
            FROM findings f
            JOIN place_names n ON lower(n.name) = lower(f.region_name)
            JOIN places p ON p.id = n.place_id
            WHERE f.geom IS NULL AND f.region_name IS NOT NULL AND f.region_name <> ''
              AND NOT ${SqlLiteral(importedSql("p"))}
              AND NOT ${SqlLiteral(explicitOnlySql("p"))}
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND ($anyPlace OR p.id = ANY($pids::text[]))
              AND (f.period IS NULL OR f.period && p.period)
              AND (f.period IS NULL OR f.period && n.period)
            ON CONFLICT DO NOTHING""".update.run()
    (byArea + byPoint + byGeocode + byPlaceholder(phNamed, anyPlace, pids), byName + byResolution(citekey, anyPlace, pids))

  /** The links of the placeholder findings whose name resolved (`named`: finding id → place id): the
    * place itself (unless imported — a GeoNames/Wikidata point takes only the findings on it) and every
    * curated AREA covering at least [[DefaultPlaceholderAreaShare]] of that place's shape, or, when the
    * place has no shape, its point — when the periods overlap. The shares are computed once per place.
    * Returns the rows inserted. */
  private def byPlaceholder(named: List[(String, String)], anyPlace: Boolean, pids: List[String])(using DbCon): Int =
    if named.isEmpty then 0
    else
      val fids  = named.map(_._1)
      val rids  = named.map(_._2)
      val share = DefaultPlaceholderAreaShare
      // Each named place's shape is made valid once (`rs`); an area is tested only when a finding naming
      // that place overlaps it in time, and is clipped to the place's box before the intersection — at
      // 3 200 candidate areas for "Italy", the overlay of whole Cliopatria empires was most of the cost.
      // (Nested CASEs: only a CASE fixes the order of evaluation.)
      sql"""WITH r AS (SELECT * FROM unnest($fids::text[], $rids::text[]) AS r(fid, rid)),
                 rs AS MATERIALIZED (
                   SELECT rp.id AS rid, rp.geom,
                          CASE WHEN ST_Dimension(rp.geom) = 2 THEN ST_MakeValid(rp.geom) END AS g
                   FROM (SELECT DISTINCT rid FROM r) x JOIN places rp ON rp.id = x.rid
                   WHERE rp.geom IS NOT NULL),
                 cover AS MATERIALIZED (
                   SELECT rs.rid, a.id AS aid FROM rs JOIN places a ON a.geom && rs.geom
                   WHERE ST_Dimension(a.geom) = 2 AND a.id <> rs.rid AND NOT ${SqlLiteral(importedSql("a"))}
                     AND ($anyPlace OR a.id = ANY($pids::text[]))
                     AND CASE WHEN NOT EXISTS (SELECT 1 FROM r JOIN findings f ON f.id = r.fid
                                               WHERE r.rid = rs.rid AND (f.period IS NULL OR f.period && a.period))
                              THEN false
                              WHEN rs.g IS NOT NULL
                              THEN ST_Area(ST_Intersection(ST_MakeValid(ST_ClipByBox2D(a.geom, rs.geom::box2d)), rs.g))
                                   >= $share * ST_Area(rs.g)
                              ELSE ST_Covers(a.geom, ST_PointOnSurface(rs.geom)) END
                   UNION ALL
                   SELECT rs.rid, rp.id FROM rs JOIN places rp ON rp.id = rs.rid
                   WHERE NOT ${SqlLiteral(importedSql("rp"))} AND ($anyPlace OR rp.id = ANY($pids::text[])))
            INSERT INTO finding_places (finding_id, place_id)
            SELECT DISTINCT r.fid, c.aid FROM r JOIN cover c ON c.rid = r.rid
            JOIN findings f ON f.id = r.fid JOIN places a ON a.id = c.aid
            WHERE f.period IS NULL OR f.period && a.period
            ON CONFLICT DO NOTHING""".update.run()

  private final case class Named(id: String, name: String, from: Option[Long], to: Option[Long])
  private given DbCodec[Named] = DbCodec.derived

  /** The explicit NAME match for explicit-only points: a finding whose place name the geocoder
    * ([[Geocoder.resolveIn]]) resolves to such a place belongs to it — whatever point the finding was
    * given by hand. Only the names one of whose readings ([[Geocode.attempts]]) is the name key of an
    * explicit-only place are resolved at all, so a rebuild resolves a few hundred names, not every
    * finding's. Returns the rows inserted. */
  private def byResolution(citekey: Option[String], anyPlace: Boolean, pids: List[String])(using DbCon): Int =
    val named = sql"""SELECT f.id, btrim(f.region_name), lower(f.period), upper(f.period) - 1
                      FROM findings f
                      WHERE btrim(coalesce(f.region_name, '')) <> ''
                        AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)"""
      .query[Named].run().toList
    if named.isEmpty then 0
    else
      val keysOf = named.map(_.name).distinct.map(n =>
        n -> Geocode.attempts(n).flatMap(a => a.key :: a.within.map(Geocode.key).toList).distinct).toMap
      val allKeys = keysOf.values.flatten.toList.distinct
      val hits = sql"""SELECT DISTINCT k FROM unnest($allKeys::text[]) AS k
                       WHERE EXISTS (SELECT 1 FROM places p WHERE histmersion_place_key(p.label) = k
                                       AND ($anyPlace OR p.id = ANY($pids::text[]))
                                       AND NOT ${SqlLiteral(importedSql("p"))} AND ${SqlLiteral(explicitOnlySql("p"))})
                          OR EXISTS (SELECT 1 FROM place_names n JOIN places p ON p.id = n.place_id
                                     WHERE histmersion_place_key(n.name) = k
                                       AND ($anyPlace OR p.id = ANY($pids::text[]))
                                       AND NOT ${SqlLiteral(importedSql("p"))} AND ${SqlLiteral(explicitOnlySql("p"))})"""
        .query[String].run().toSet
      val wanted = named.filter(f => keysOf(f.name).exists(hits.contains))
      val resolved = wanted.groupBy(f => (f.name, Geocode.periodOf(f.from, f.to))).toList.flatMap { case ((name, at), fs) =>
        Geocoder.resolveIn(name, Geocode.KindHint.Unknown, at, DefaultPlaceholderMin).toList
          .flatMap(r => fs.map(f => (f.id, r.placeId)))
      }
      if resolved.isEmpty then 0
      else
        val fids = resolved.map(_._1)
        val pids = resolved.map(_._2)
        sql"""INSERT INTO finding_places (finding_id, place_id)
              SELECT r.fid, r.pid FROM unnest($fids::text[], $pids::text[]) AS r(fid, pid)
              JOIN findings f ON f.id = r.fid JOIN places p ON p.id = r.pid
              WHERE NOT ${SqlLiteral(importedSql("p"))} AND ${SqlLiteral(explicitOnlySql("p"))}
                AND ($anyPlace OR p.id = ANY($pids::text[]))
                AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()

  // ── resolving a Filter's `places` values ─────────────────────────────────────────────────────

  /** Which of a filter's `places` values are gazetteer ids, and what each one stands for: itself plus
    * every descendant reachable through relations whose own `period` overlaps `filter.period` (plan
    * §1.6a — "as of the query period"). Values that are not place ids are absent from the map and
    * fall back to the name ILIKE.
    *
    * Cheap by construction: no values ⇒ no query at all, and the closure is one recursive CTE per
    * chip (there are never many). */
  def scopeFor(ds: DataSource, values: List[String], period: Option[Period]): PlaceScope =
    val wanted = values.map(_.trim).filter(_.nonEmpty).distinct
    if wanted.isEmpty then PlaceScope.empty
    else
      connect(ds):
        val joined = wanted.toList
        // an OLD id (V055) stands for the place it was renamed to: a saved link, a bookmarked filter
        // or an agent's `place=<old id>` keeps matching what it matched before the rename
        val renamed = sql"""SELECT alias, place_id FROM place_aliases
                            WHERE alias = ANY($joined::text[])
                              AND NOT EXISTS (SELECT 1 FROM places WHERE id = alias)"""
          .query[(String, String)].run().toMap
        val target  = wanted.map(v => v -> renamed.getOrElse(v, v)).toMap
        val ids     = target.values.toList.distinct
        val rows = sql"SELECT id, label FROM places WHERE id = ANY($ids::text[])"
          .query[(String, String)].run()
        val labelOf = rows.toMap
        val known   = wanted.filter(v => labelOf.contains(target(v)))
        val labels  = known.map(v => v -> labelOf(target(v))).toMap
        if known.isEmpty then PlaceScope.empty
        else
          val lo = period.map(_.start)
          val hi = period.map(_.end)
          PlaceScope(known.map { value =>
            val id = target(value)
            value -> sql"""WITH RECURSIVE d(id) AS (
                          SELECT $id
                          UNION
                          SELECT r.child_id FROM place_relations r JOIN d ON r.parent_id = d.id
                          JOIN places c ON c.id = r.child_id
                          WHERE NOT ${SqlLiteral(importedSql("c"))}
                            AND (${lo}::bigint IS NULL
                                 OR r.period && int8range(${lo}::bigint, ${hi}::bigint, '[]'))
                        )
                        SELECT d.id FROM d JOIN places p ON p.id = d.id""".query[String].run().toList
          }.toMap, labels)

  // ── temporal geography (V016) ──────────────────────────────────────────────────────────────

  def footprints(ds: DataSource, placeId: String, asOf: Option[Long]): Vector[PlaceFootprint] =
    connect(ds):
      sql"""SELECT f.id, f.place_id, f.role, lower(f.period), upper(f.period) - 1,
                   f.certainty, f.accuracy_m, f.citekey
            FROM place_footprints f
            WHERE f.place_id = $placeId
              AND (${asOf}::bigint IS NULL OR f.period @> ${asOf}::bigint)
            ORDER BY f.role, lower(f.period)""".query[FootprintRow].run().map(_.toDto)

  def links(ds: DataSource, placeId: String): Vector[PlaceLink] =
    connect(ds):
      sql"""SELECT place_id, scheme, external_id, relation FROM place_links
            WHERE place_id = $placeId ORDER BY scheme, external_id""".query[PlaceLink].run()

  def types(ds: DataSource, placeId: String, asOf: Option[Long]): List[PlaceType] =
    connect(ds):
      sql"""SELECT place_id, type_label,
                   CASE WHEN period = int8range(NULL, NULL) THEN NULL ELSE lower(period) END,
                   CASE WHEN period = int8range(NULL, NULL) THEN NULL ELSE upper(period) - 1 END,
                   citekey
            FROM place_types
            WHERE place_id = $placeId
              AND (${asOf}::bigint IS NULL OR period @> ${asOf}::bigint)
            ORDER BY type_label""".query[PlaceTypeRow].run().map(_.toDto).toList

  // ── renames (V055) ─────────────────────────────────────────────────────────────────────────────

  /** A new id for a place, the old one kept as an alias (V055): every foreign key into `places(id)`
    * cascades; attestations and asset links name a place without a key and are moved here. Refuses an
    * id another place has, or an old id of another place; renaming back to an old id drops that alias.
    * The caller's transaction (a bundle import follows a rename made in the other collection, #888). */
  private[db] def renameIn(oldId: String, newId: String)(using DbCon): Either[ApiError, Unit] =
    def exists(i: String) = sql"SELECT 1 FROM places WHERE id = $i".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT place_id FROM place_aliases WHERE alias = $newId".query[String].run().headOption
    if !exists(oldId) then Left(ApiError(s"No place '$oldId'", Some(Write.CodeNotFound)))
    else if exists(newId) then Left(ApiError(s"'$newId' is already the id of another place", Some(Write.CodeInvalid)))
    else if aliasOf.exists(_ != oldId) then
      Left(ApiError(s"'$newId' is an old id of '${aliasOf.get}' and still resolves to it — pick another", Some(Write.CodeInvalid)))
    else
      sql"DELETE FROM place_aliases WHERE alias = $newId".update.run()
      sql"UPDATE places SET id = $newId WHERE id = $oldId".update.run()
      sql"UPDATE attestations SET target_id = $newId WHERE target_type = 'place' AND target_id = $oldId".update.run()
      sql"UPDATE asset_links SET target_id = $newId WHERE target_type = 'place' AND target_id = $oldId".update.run()
      sql"""INSERT INTO place_aliases (alias, place_id) VALUES ($oldId, $newId)
            ON CONFLICT (alias) DO UPDATE SET place_id = EXCLUDED.place_id""".update.run()
      Right(())

  /** [[renameIn]] in a transaction of its own. */
  def rename(ds: DataSource, oldId: String, newId: String): Either[ApiError, Unit] =
    transact(ds)(renameIn(oldId.trim, newId.trim))

  /** The id an OLD place id was renamed to — `None` when it is a live id, or nothing. */
  def renamedTo(ds: DataSource, id: String): Option[String] =
    connect(ds):
      sql"SELECT place_id FROM place_aliases WHERE alias = $id AND NOT EXISTS (SELECT 1 FROM places WHERE id = $id)"
        .query[String].run().headOption

  // ── a person's edits (#987) ─────────────────────────────────────────────────────────────────────

  private def labelOf(id: String)(using DbCon): Option[String] =
    sql"SELECT label FROM places WHERE id = $id".query[String].run().headOption

  /** Give place `id` the name `name` in `lang`, as a MANUAL name (V068): no import deletes it. A name
    * the place already has from an import becomes manual (so a later seed that drops it keeps it). */
  def addName(ds: DataSource, id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
    transact(ds):
      labelOf(id).toRight(ApiError(s"No place '$id'", Some(Write.CodeNotFound))).map { label =>
        val before = sql"SELECT manual FROM place_names WHERE place_id = $id AND name = $name AND lang = $lang AND import_source = ''"
          .query[Boolean].run().headOption
        if !before.contains(true) then
          val _ = sql"""INSERT INTO place_names (place_id, name, lang, manual) VALUES ($id, $name, $lang, true)
                        ON CONFLICT (place_id, name, lang, import_source, import_ref) DO UPDATE SET manual = true""".update.run()
        PlaceNameChanged(id, label, name, lang, added = true, changed = !before.contains(true),
          wasImported = before.contains(false))
      }

  /** Remove a name a person added. An IMPORTED name is refused — the next seed would bring it back. */
  def removeName(ds: DataSource, id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
    transact(ds):
      labelOf(id).toRight(ApiError(s"No place '$id'", Some(Write.CodeNotFound))).flatMap { label =>
        sql"SELECT bool_or(manual) FROM place_names WHERE place_id = $id AND name = $name AND lang = $lang HAVING count(*) > 0"
          .query[Boolean].run().headOption match
          case None =>
            val langs = sql"SELECT lang FROM place_names WHERE place_id = $id AND name = $name AND manual"
              .query[String].run().map(l => if l.isEmpty then "no language" else l)
            val hint = if langs.isEmpty then "" else s" (it has it with ${langs.mkString(", ")} — pass that language)"
            Left(ApiError(s"'$label' [$id] has no name '$name'${if lang.isEmpty then "" else s" in $lang"}$hint",
              Some(Write.CodeNotFound)))
          case Some(false) =>
            Left(ApiError(s"'$name' is a name the gazetteer seed or an imported source gives '$label' [$id], not one added by hand — " +
              "removing it would last only until the next import", Some(Write.CodeInvalid)))
          case Some(true) =>
            val _ = sql"DELETE FROM place_names WHERE place_id = $id AND name = $name AND lang = $lang AND manual".update.run()
            Right(PlaceNameChanged(id, label, name, lang, added = false, changed = true))
      }

  /** The names people added, by place then name; `id` narrows to one place. */
  def manualNames(ds: DataSource, id: Option[String]): List[ManualPlaceName] =
    connect(ds):
      sql"""SELECT n.place_id, p.label, n.name, n.lang FROM place_names n JOIN places p ON p.id = n.place_id
            WHERE n.manual AND (${id}::text IS NULL OR n.place_id = ${id}::text)
            ORDER BY n.place_id, n.name, n.lang""".query[ManualPlaceName].run().toList

  /** Create a person's own place (`dataset = local`, V052): refused when the id is taken — by a place
    * or as an old id of one (V055) — so `places add` can never overwrite seed data. */
  def addPlace(ds: DataSource, in: PlaceInput): Either[ApiError, PlaceSummary] =
    val id = in.place.id
    transact(ds):
      val taken = sql"SELECT 1 FROM places WHERE id = $id".query[Int].run().nonEmpty
      val alias = sql"SELECT place_id FROM place_aliases WHERE alias = $id".query[String].run().headOption
      val gone  = sql"SELECT 1 FROM place_removals WHERE id = $id".query[Int].run().nonEmpty
      if taken then Left(ApiError(s"'$id' is already the id of a place — choose another id", Some(Write.CodeInvalid)))
      else if gone then Left(ApiError(s"'$id' is the id of a place that was removed — choose another id", Some(Write.CodeInvalid)))
      else if alias.isDefined then
        Left(ApiError(s"'$id' is an old id of '${alias.get}' and still resolves to it — choose another id", Some(Write.CodeInvalid)))
      else
        val _ = upsertIn(List(in), Dataset.Local)
        sql"SELECT ${SqlLiteral(summaryCols)} FROM places p WHERE p.id = $id".query[PlaceSummary].run().headOption
          .toRight(ApiError(s"place '$id' was not stored"))

  /** How many places the gazetteer holds — 0 on a fresh install (the first-run import, #720). */
  def count(ds: DataSource): Int =
    connect(ds)(sql"SELECT count(*)::int FROM places".query[Int].run().head)

  def listPeriods(ds: DataSource, q: Option[String], region: Option[String],
      from: Option[Long], to: Option[Long]): Vector[HistoricalPeriod] =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val reg  = region.map(_.trim).filter(_.nonEmpty)
    connect(ds):
      sql"""SELECT id, label, authority, lower(period), upper(period) - 1, region, parent_id, preset
            FROM periods
            WHERE (${text}::text IS NULL OR label ILIKE $cont ESCAPE '\\')
              AND (${reg}::text IS NULL OR lower(region) = lower(${reg}))
              AND ((${from}::bigint IS NULL AND ${to}::bigint IS NULL)
                   OR period && int8range(${from}::bigint, ${to}::bigint, '[]'))
            ORDER BY lower(period), label""".query[HistPeriodRow].run().map(_.toDto)

  def upsertPeriod(ds: DataSource, p: HistoricalPeriod): Unit =
    transact(ds):
      sql"""INSERT INTO periods (id, label, authority, period, region, parent_id)
            VALUES (${p.id}, ${p.label}, ${p.authority},
                    int8range(${p.period.start}::bigint, ${p.period.end}::bigint, '[]'),
                    ${p.region}, ${p.parentId})
            ON CONFLICT (id) DO UPDATE SET
              label = EXCLUDED.label, authority = EXCLUDED.authority, period = EXCLUDED.period,
              region = EXCLUDED.region, parent_id = EXCLUDED.parent_id""".update.run()

  def upsertFootprint(ds: DataSource, placeId: String, role: String, geojson: String,
      period: Option[Period], certainty: Option[String], accuracyM: Option[Float],
      citekey: Option[String]): Int =
    val p = period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
    transact(ds):
      sql"""INSERT INTO place_footprints (place_id, role, geom, period, certainty, accuracy_m, citekey)
            VALUES ($placeId, $role,
                    ST_SetSRID(ST_GeomFromGeoJSON($geojson), 4326),
                    ${p}::int8range, $certainty, $accuracyM, $citekey)
            RETURNING id""".query[Int].run().head

  def upsertLink(ds: DataSource, link: PlaceLink): Unit =
    transact(ds):
      sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
            VALUES (${link.placeId}, ${link.scheme}, ${link.externalId}, ${link.relation})
            ON CONFLICT (place_id, scheme, external_id) DO UPDATE SET
              relation = EXCLUDED.relation""".update.run()

  def upsertType(ds: DataSource, pt: PlaceType): Unit =
    val p = pt.period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
    transact(ds):
      sql"""INSERT INTO place_types (place_id, type_label, period, citekey)
            VALUES (${pt.placeId}, ${pt.typeLabel}, ${p}::int8range, ${pt.citekey})
            ON CONFLICT (place_id, type_label) DO UPDATE SET
              period = EXCLUDED.period, citekey = EXCLUDED.citekey""".update.run()
