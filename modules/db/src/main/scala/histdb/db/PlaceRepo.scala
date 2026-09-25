package histdb.db

import histdb.core.*

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
    source: Option[String], names: String, contested: Boolean, geometry: Option[String], findings: Int
):
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested)

given DbCodec[PlaceSummary]   = DbCodec.derived
given DbCodec[PlaceHit]       = DbCodec.derived
given DbCodec[PlaceTreeRow]   = DbCodec.derived
given DbCodec[PlaceDetailRow] = DbCodec.derived

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
    region: Option[String], parentId: Option[String]):
  def toDto: HistoricalPeriod =
    HistoricalPeriod(id, label, authority, Period(periodStart, periodEnd), region, parentId)
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

  /** Delimiter for passing a list as ONE parameter (`string_to_array`), as in [[SourceRepo]]. */
  private val Sep = "|~|"

  /** Every alternate name of `p`, comma-joined — the `names` column of a [[PlaceSummary]]. */
  private val namesCol =
    "coalesce((SELECT string_agg(n.name, ', ' ORDER BY n.name) FROM place_names n" +
      " WHERE n.place_id = p.id), '')"

  /** `properties.contested` (plan §1.6a / stream 2.2): a hand-traced shape whose extent is disputed.
    * It lives in the jsonb blob — the importer keeps every property it does not model — and is
    * projected out here so every surface can mark it without parsing JSON. */
  private val contestedCol = "coalesce((p.properties->>'contested')::boolean, false)"

  /** The [[PlaceSummary]] projection, over a `places` row aliased `p`. One definition, reused. */
  private val summaryCols =
    s"p.id, p.label, p.kind, lower(p.period), upper(p.period) - 1, p.source, $namesCol, $contestedCol"

  // ── import ───────────────────────────────────────────────────────────────────────────────────

  /** Upsert a batch of places with their names and parent edges, in ONE transaction and in three
    * passes — rows, then names, then relations — because a file routinely names a parent that only
    * appears later in it, and the foreign key would reject the edge otherwise.
    *
    * Names and relations are REPLACED for every id in the batch (an import is the whole truth about
    * the places it mentions); a relation whose parent is in neither the batch nor the store is
    * reported in `failed` rather than aborting the import. */
  def upsertPlaces(ds: DataSource, batch: List[PlaceInput]): PlaceImportResult =
    if batch.isEmpty then PlaceImportResult(0, 0, 0, Nil)
    else
      val ids = batch.map(_.place.id).distinct
      transact(ds):
        val _ = batchUpdate(batch) { in =>
          val g = in.place.geometry
          val p = in.place.period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
          sql"""INSERT INTO places (id, label, kind, geom, period, source, properties)
                VALUES (${in.place.id}, ${in.place.label}, ${in.place.kind},
                        CASE WHEN ${g}::text IS NULL THEN NULL
                             ELSE ST_SetSRID(ST_GeomFromGeoJSON(${g}::text), 4326) END,
                        ${p}::int8range, ${in.place.source}, ${in.place.properties}::jsonb)
                ON CONFLICT (id) DO UPDATE SET
                  label = EXCLUDED.label, kind = EXCLUDED.kind, geom = EXCLUDED.geom,
                  period = EXCLUDED.period, source = EXCLUDED.source,
                  properties = EXCLUDED.properties""".update
        }

        val idList = ids.mkString(Sep)
        val _ = sql"DELETE FROM place_names     WHERE place_id = ANY(string_to_array($idList, $Sep))".update.run()
        val _ = sql"DELETE FROM place_relations WHERE child_id = ANY(string_to_array($idList, $Sep))".update.run()

        val names = batch.flatMap(in => in.names.map(in.place.id -> _))
        val _ = batchUpdate(names) { (pid, n) =>
          val p = n.period.map(x => s"[${x.start},${x.end}]").getOrElse("(,)")
          sql"""INSERT INTO place_names (place_id, name, lang, period)
                VALUES ($pid, ${n.name}, ${n.lang}, ${p}::int8range)
                ON CONFLICT (place_id, name, lang) DO UPDATE SET period = EXCLUDED.period""".update
        }

        // relations: only those whose parent actually exists once every row of the batch is in
        val wanted   = batch.flatMap(_.relations)
        val parents  = wanted.map(_.parentId).distinct.mkString(Sep)
        val existing = sql"SELECT id FROM places WHERE id = ANY(string_to_array($parents, $Sep))"
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
    * `asOf`) contains `q`, or its label does; exact hits sort first, then prefixes, then the places
    * carrying the most findings. `q` empty lists the gazetteer by size. */
  def search(ds: DataSource, q: Option[String], asOf: Option[Long], limit: Int): Vector[PlaceHit] =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val pre  = text.map(FilterSql.startsWith).getOrElse("%")
    val exact = text.getOrElse("")
    connect(ds):
      sql"""SELECT p.id, p.label, p.kind, lower(p.period), upper(p.period) - 1, p.source,
                   coalesce(nm.names, ''), nm.matched, coalesce(fc.n, 0),
                   ${SqlLiteral(contestedCol)}
            FROM places p
            LEFT JOIN LATERAL (
              SELECT string_agg(n.name, ', ' ORDER BY n.name) AS names,
                     -- the name the user actually typed, not just any name that contains it:
                     -- exact first, then the shortest ("Turkey", never "Republic of Turkey")
                     (SELECT m.name FROM place_names m
                      WHERE m.place_id = p.id AND m.name ILIKE $cont ESCAPE '\'
                        AND (${asOf}::bigint IS NULL OR m.period @> ${asOf}::bigint)
                      ORDER BY (lower(m.name) = lower($exact)) DESC, length(m.name), m.name
                      LIMIT 1) AS matched
              FROM place_names n
              WHERE n.place_id = p.id
                AND (${asOf}::bigint IS NULL OR n.period @> ${asOf}::bigint)
            ) nm ON true
            LEFT JOIN LATERAL (
              SELECT count(*)::int AS n FROM finding_places fp WHERE fp.place_id = p.id
            ) fc ON true
            WHERE (${text}::text IS NULL OR nm.matched IS NOT NULL OR p.label ILIKE $cont ESCAPE '\')
              AND (${asOf}::bigint IS NULL OR p.period @> ${asOf}::bigint)
            ORDER BY (lower(p.label) = lower($exact)) DESC,
                     (p.label ILIKE $pre ESCAPE '\') DESC,
                     fc.n DESC, length(p.label), p.id
            LIMIT ${limit.max(1)}""".query[PlaceHit].run()

  /** `GET /api/places/{id}?asOf=` — the row, its geometry as GeoJSON, and the hierarchy AS OF `asOf`.
    * A place with no shape of its own (a continent is a hierarchy node, not a polygon) reports the
    * UNION of its children's shapes, which is what the atlas draws as its overlay. */
  def get(ds: DataSource, id: String, asOf: Option[Long] = None): Option[PlaceDetail] =
    connect(ds):
      sql"""SELECT ${SqlLiteral(summaryCols)},
                   ST_AsGeoJSON(COALESCE(p.geom, (
                     SELECT ST_Union(c.geom) FROM place_relations r JOIN places c ON c.id = r.child_id
                     WHERE r.parent_id = p.id AND c.geom IS NOT NULL
                       AND (${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint)))),
                   (SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = p.id)
            FROM places p WHERE p.id = $id""".query[PlaceDetailRow].run().headOption.map { r =>
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
        PlaceDetail(r.toSummary, r.geometry, parents(id, asOf).toList, kids(id, asOf, Int.MaxValue).toList,
          r.findings, asOf, r.contested, fps, lks, tps)
      }

  /** Parents valid at `asOf` (both the edge and the parent itself must be). */
  def parents(ds: DataSource, id: String, asOf: Option[Long]): Vector[PlaceSummary] =
    connect(ds)(parents(id, asOf))

  private def parents(id: String, asOf: Option[Long])(using DbCon): Vector[PlaceSummary] =
    sql"""SELECT ${SqlLiteral(summaryCols)} FROM place_relations r JOIN places p ON p.id = r.parent_id
          WHERE r.child_id = $id
            AND (${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint)
            AND (${asOf}::bigint IS NULL OR p.period @> ${asOf}::bigint)
          ORDER BY p.label, p.id""".query[PlaceSummary].run()

  /** `GET /api/places/{id}/children?asOf=`. */
  def children(ds: DataSource, id: String, asOf: Option[Long], limit: Int): Vector[PlaceSummary] =
    connect(ds)(kids(id, asOf, limit))

  private def kids(id: String, asOf: Option[Long], limit: Int)(using DbCon): Vector[PlaceSummary] =
    sql"""SELECT ${SqlLiteral(summaryCols)} FROM place_relations r JOIN places p ON p.id = r.child_id
          WHERE r.parent_id = $id
            AND (${asOf}::bigint IS NULL OR r.period @> ${asOf}::bigint)
            AND (${asOf}::bigint IS NULL OR p.period @> ${asOf}::bigint)
          ORDER BY p.label, p.id LIMIT ${limit.max(1)}""".query[PlaceSummary].run()

  /** SQL twin of [[histdb.core.Tree.rootEligible]]: a place that may head the hierarchy. Region,
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
      depthCap: Int = Int.MaxValue): Vector[PlaceTreeRow] =
    // `maxDepth` is a caller's number; `depthCap` is the configured ceiling (`places.tree-depth`).
    // A caller asking for depth 10 000 walked ten thousand levels of a recursive CTE.
    val depth = math.max(0, math.min(maxDepth, depthCap))
    connect(ds):
      sql"""WITH RECURSIVE edges AS (
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
              WHERE ${asOf}::bigint IS NULL OR p.period @> ${asOf}::bigint
              UNION ALL
              SELECT c.id, t.depth + 1, t.path || lower(c.label) || c.id, t.visited || c.id
              FROM tree t JOIN edges e ON e.parent_id = t.id JOIN places c ON c.id = e.child_id
              WHERE t.depth + 1 <= ${depth}
                -- a `part_of` cycle (A part of B part of A) is data, not an impossibility, and the
                -- depth bound alone only made it terminate — it still walked the loop N times and
                -- reported the same place at every level. Refuse to revisit a place on one path.
                AND NOT (c.id = ANY(t.visited))
                AND (${asOf}::bigint IS NULL OR c.period @> ${asOf}::bigint)
            )
            SELECT t.id, p.label, p.kind, t.depth,
                   (SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = t.id),
                   ${SqlLiteral(contestedCol)}
            FROM tree t JOIN places p ON p.id = t.id
            ORDER BY t.path, t.depth LIMIT ${limit.max(1)}""".query[PlaceTreeRow].run()

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

  /** Rebuild `finding_places` from scratch (`histdb places rebuild` / `POST /api/places/rebuild`).
    *
    * A finding belongs to a place when
    *   - it has geometry and the place COVERS it (`ST_CoveredBy`, not `ST_Within`: a finding exactly
    *     on a shared border must count in BOTH polygons — plan §1.6a), or the place is a POINT — a
    *     Pleiades site has no extent — and the finding is within `toleranceKm` of it; **and**
    *   - their periods overlap, where a NULL period on either side means "always" (so an undated
    *     finding still gets its geography, and a modern country contains everything);
    *   - or, for a finding with NO geometry, its `region_name` equals a place name exactly
    *     (case-insensitively) and the periods overlap.
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

  /** The SQL of [[geoJson]], as a value — pure, so the de-duplication rules are unit-tested. */
  private[db] def geoJsonQuery(kind: Option[String], asOf: Option[Long], limit: Int): Sql =
    import Param.*
    val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val where = List(Some(Sql.raw("p.geom IS NOT NULL")),
      k.map(v => Sql.of("p.kind = ?", S(v))),
      asOf.map(y => Sql.of("p.period @> ?::bigint", L(y)))).flatten
    // `live` carries no geometry — copying 138 large polygons into a CTE's tuplestore cost ~2 s;
    // the full shapes are read from `places` only for candidate pairs and for the rows returned.
    val live = Sql.raw(
      "WITH live AS (SELECT p.id, p.kind, " +
        s"$sourceTierSql AS tier, ${normLabelSql("p.label")} AS norm, ST_Area(p.geom) AS area, " +
        "p.geom::box2d AS bb FROM places p WHERE ") ++
      Sql(where.map(_.text).mkString(" AND "), where.flatMap(_.params)) ++ Sql.raw(")")
    // only an as-of view collapses duplicates: across all time, successive shapes are all real
    val shadowed =
      if asOf.isEmpty then Sql.raw(", shadowed AS (SELECT NULL::text AS id WHERE false)")
      else Sql.raw(
        ", shadowed AS (SELECT DISTINCT lo.id FROM live lo JOIN live hi" +
          " ON hi.tier < lo.tier AND hi.kind = lo.kind AND hi.bb && lo.bb" +
          " JOIN places gh ON gh.id = hi.id JOIN places gl ON gl.id = lo.id" +
          " WHERE hi.norm = lo.norm OR (ST_Intersects(gh.geom, gl.geom)" +
          " AND ST_Area(ST_Intersection(ST_MakeValid(gh.geom), ST_MakeValid(gl.geom)))" +
          s" > $SameStateOverlap * least(hi.area, lo.area)))")
    live ++ shadowed ++ Sql.raw(
      " SELECT l.id, p.label, l.kind, lower(p.period) AS ps, upper(p.period) - 1 AS pe," +
        " ST_AsGeoJSON(p.geom, 5) AS geojson FROM live l JOIN places p ON p.id = l.id" +
        " WHERE l.id NOT IN (SELECT id FROM shadowed)" +
        " ORDER BY l.area DESC, l.id") ++ Sql.of(" LIMIT ?", I(limit.max(1)))

  /** `GET /api/places/geo` — a FeatureCollection for the map. When `asOf` is given, each state is
    * drawn ONCE: of shapes from different source tiers that share a normalised name or overlap by
    * [[SameStateOverlap]], only the best tier survives (within one source nothing is dropped —
    * Cliopatria's overlapping vassal/allegiance polygons are real). The limit applies after that,
    * largest shapes first, so it drops the smallest rather than whatever sorts last by name. */
  def geoJson(ds: DataSource, kind: Option[String], asOf: Option[Long], limit: Int): String =
    connect(ds):
      val rows = geoJsonQuery(kind, asOf, limit).frag
        .query[(String, String, String, Option[Long], Option[Long], String)].run()
      val features = rows.map { (id, label, placeKind, ps, pe, geojson) =>
        import io.circe.Json
        val propsObj = Json.obj(
          "id" -> Json.fromString(id),
          "label" -> Json.fromString(label),
          "kind" -> Json.fromString(placeKind)
        ).deepMerge(Json.fromFields(
          ps.map(v => "periodStart" -> Json.fromLong(v)).toList ++
          pe.map(v => "periodEnd" -> Json.fromLong(v)).toList
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

  /** The THREE inserts that define "a finding belongs to a place", shared by the full rebuild and
    * the per-citekey re-materialisation — one definition, so they cannot disagree about what the
    * assignment means. `citekey = None` means the whole corpus. Returns (byGeometry, byName). */
  private def insertAssignments(citekey: Option[String], toleranceKm: Double)(using DbCon): (Int, Int) =
    val meters  = toleranceKm.max(0.0) * 1000.0
    val degrees = toleranceKm.max(0.0) / 111.32
    // TWO statements, not one CASE: a CASE around the spatial predicate hides it from the planner
    // and turns the join into a 964 x 16 700 cross product (minutes). Split, each side is an
    // index-driven nested loop over `places_geom_gist` and the whole rebuild takes a few seconds.
    val byArea =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p ON ST_CoveredBy(f.geom, p.geom)
            WHERE f.geom IS NOT NULL AND p.geom IS NOT NULL AND ST_Dimension(p.geom) > 0
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
    val byPoint =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT f.id, p.id FROM findings f JOIN places p
              ON p.geom && ST_Expand(f.geom,
                   $degrees / greatest(cos(radians(ST_Y(f.geom))), 0.01))
            WHERE f.geom IS NOT NULL AND p.geom IS NOT NULL AND ST_Dimension(p.geom) = 0
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND ST_DWithin(f.geom::geography, p.geom::geography, $meters)
              AND (f.period IS NULL OR f.period && p.period)
            ON CONFLICT DO NOTHING""".update.run()
    val byName =
      sql"""INSERT INTO finding_places (finding_id, place_id)
            SELECT DISTINCT f.id, p.id
            FROM findings f
            JOIN place_names n ON lower(n.name) = lower(f.region_name)
            JOIN places p ON p.id = n.place_id
            WHERE f.geom IS NULL AND f.region_name IS NOT NULL AND f.region_name <> ''
              AND (${citekey}::text IS NULL OR f.citekey = ${citekey}::text)
              AND (f.period IS NULL OR f.period && p.period)
              AND (f.period IS NULL OR f.period && n.period)
            ON CONFLICT DO NOTHING""".update.run()
    (byArea + byPoint, byName)

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
        val joined = wanted.mkString(Sep)
        val rows = sql"SELECT id, label FROM places WHERE id = ANY(string_to_array($joined, $Sep))"
          .query[(String, String)].run()
        val known  = rows.map(_._1).toSet
        val labels = rows.toMap
        if known.isEmpty then PlaceScope.empty
        else
          val lo = period.map(_.start)
          val hi = period.map(_.end)
          PlaceScope(wanted.filter(known.contains).map { id =>
            id -> sql"""WITH RECURSIVE d(id) AS (
                          SELECT $id
                          UNION
                          SELECT r.child_id FROM place_relations r JOIN d ON r.parent_id = d.id
                          WHERE ${lo}::bigint IS NULL
                             OR r.period && int8range(${lo}::bigint, ${hi}::bigint, '[]')
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

  def listPeriods(ds: DataSource, q: Option[String], region: Option[String],
      from: Option[Long], to: Option[Long]): Vector[HistoricalPeriod] =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val reg  = region.map(_.trim).filter(_.nonEmpty)
    connect(ds):
      sql"""SELECT id, label, authority, lower(period), upper(period) - 1, region, parent_id
            FROM periods
            WHERE (${text}::text IS NULL OR label ILIKE $cont ESCAPE '\')
              AND (${reg}::text IS NULL OR lower(region) = lower(${reg}))
              AND (${from}::bigint IS NULL OR period && int8range(${from}::bigint, ${to}::bigint, '[]'))
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
