package com.histmersion
package domain.catalogue.places

import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.given

import com.histmersion.util.database.LikePattern
import com.histmersion.util.database.RangeSql

import com.histmersion.api.{Dataset, SpatialHierarchyResult, Write}
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import scala.util.chaining.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.Param
import com.histmersion.util.database.Sql
import com.histmersion.api.catalogue.{Geocode, GeoTime, ManualPlaceName, Period, SiteTypes}
import com.histmersion.api.catalogue.Place
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.library.Citekey

/** The gazetteer store (plan §1.6a): import, search, hierarchy, and the MATERIALISATION of `finding_places` — the only
  * place that decides which findings belong to which places.
  *
  * Temporal rule, applied identically everywhere: place/name/relation periods are NOT NULL — a timeless entry carries
  * `int8range(-infinity, infinity)` (V015). `asOf` is a single signed year and uses containment (`period @> asOf`);
  * `Filter.period` is a range and uses overlap (`&&`).
  *
  * Geometry crosses the boundary as **GeoJSON text** (`ST_AsGeoJSON` / `ST_GeomFromGeoJSON`), the same reasoning as the
  * WKT used for findings: Postgres does all the spatial work and the native image never needs a binary PostGIS JDBC
  * type.
  */
private[places] object PlaceRepo:
  type AccuracyMetres = AccuracyMetres.T
  object AccuracyMetres extends Newtype[Float, Pure]
  type ArrayKey = ArrayKey.T
  object ArrayKey extends Newtype[String, Pure]
  type Certainty = Certainty.T
  object Certainty extends Newtype[String, Pure]
  type Depth = Depth.T
  object Depth extends Newtype[Int, Pure]
  type FootprintRole = FootprintRole.T
  object FootprintRole extends Newtype[String, Pure]
  type GeoJson = GeoJson.T
  object GeoJson extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Lang = Lang.T
  object Lang extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type PairsByIndex = PairsByIndex.T
  object PairsByIndex extends Newtype[Boolean, Pure]
  type RankLevel = RankLevel.T
  object RankLevel extends Newtype[String, Pure]
  type SearchText = SearchText.T
  object SearchText extends Newtype[String, Pure]
  type SiteType = SiteType.T
  object SiteType extends Newtype[String, Pure]


  export com.histmersion.domain.catalogue.Place.Kind.{Wire as GeoJsonQueryKind}
  type GeoJsonQueryLimit = Limit
  val GeoJsonQueryLimit: Limit.type = Limit
  export com.histmersion.domain.DeepTime.{SignedYear as GeoJsonQueryModernFrom}
  type ScopeForValues = ScopeForValues.T
  object ScopeForValues extends Newtype[String, Pure]

  given DbCodec[Place.Summary] = DbCodec.derived
  given DbCodec[Place.Hit] = DbCodec.derived
  given DbCodec[Place.TreeRow] = DbCodec.derived
  given DbCodec[ManualPlaceName] = DbCodec.derived
  given DbCodec[Place.Description] = DbCodec.derived
  given DbCodec[Place.Link] = DbCodec.derived

  export PlaceSql.{
    datingCol,
    importedSql,
    inTimeSql,
    inWindowSql,
    modernSql,
    normLabelSql,
    sourceTierSql,
    startCol,
    timeBasisSql,
    timeWindowCte,
    windowCte,
    BaseLayerSql,
    CuratedSql,
    DatedImportSql,
    DefaultModernFrom,
    ImportedList,
    ModernSql,
    SameStateOverlap,
    ShownBeforeModernSql,
    UndatedImportSql,
    UndatedPolityPointSql,
    UninformativeLabel
  }

  /** Every alternate name of `p`, comma-joined — the `names` column of a [[PlaceSummary]]. */
  private def raw(text: String): Sql = Sql.raw(Sql.Text(text))

  private def of(text: String, ps: Param*): Sql = Sql.of(Sql.Text(text), ps*)

  private def signed(y: Year): DeepTime.SignedYear = DeepTime.SignedYear(y.value)

  private def bounds(b: (Option[Year], Option[Year])): String =
    RangeSql.bounds(b._1.map(signed), b._2.map(signed))

  private val namesCol =
    "coalesce((SELECT string_agg(DISTINCT n.name, ', ' ORDER BY n.name) FROM place_names n" +
      " WHERE n.place_id = p.id), '')"

  /** `properties.contested` (plan §1.6a / stream 2.2): a hand-traced shape whose extent is disputed. It lives in the
    * jsonb blob — the importer keeps every property it does not model — and is projected out here so every surface can
    * mark it without parsing JSON.
    */
  private val contestedCol = "coalesce((p.properties->>'contested')::boolean, false)"

  /** The [[PlaceSummary]] projection, over a `places` row aliased `p`. One definition, reused. */
  private val summaryCols =
    s"p.id, p.label, p.kind, $startCol, ${RangeSql.end("p.period")}, p.source, $namesCol, $contestedCol, $datingCol, p.import_source"

  // ── import ───────────────────────────────────────────────────────────────────────────────────

  /** Upsert a batch of places with their names and parent edges, in ONE transaction and in three passes — rows, then
    * names, then relations — because a file routinely names a parent that only appears later in it, and the foreign key
    * would reject the edge otherwise.
    *
    * Names and relations are REPLACED for every id in the batch (an import is the whole truth about the places it
    * mentions) — except the names a person added (`manual`, V068, #987), which no import deletes, so a re-seed never
    * loses them; a relation whose parent is in neither the batch nor the store is reported in `failed` rather than
    * aborting the import.
    */
  def upsertPlaces(batch: List[Place.Input], dataset: Dataset = Dataset.Seed)(using
      DbCon
  ): Place.ImportResult =
    if batch.isEmpty then Place.ImportResult(Place.ImportResult.Places(0), Place.ImportResult.Names(0), Place.ImportResult.Relations(0), Nil)
    else upsertIn(batch, dataset)

  /** The ids of `ids` a seed import must not create (V080): a place a person removed (`place_removals`), and an old id
    * of another place (`place_aliases`: a merged duplicate, a renamed place) — else the next `places seed` would bring
    * back what a person merged or removed.
    */
  private def suppressed(ids: List[String])(using DbCon): Set[String] =
    sql"""SELECT id FROM place_removals WHERE id = ANY($ids::text[])
          UNION
          SELECT alias FROM place_aliases a WHERE a.alias = ANY($ids::text[])
            AND NOT EXISTS (SELECT 1 FROM places p WHERE p.id = a.alias)""".query[String].run().toSet

  /** [[upsertPlaces]] in the CALLER's transaction. A place a person removed or merged away is left out of the batch
    * ([[suppressed]]).
    */
  private def upsertIn(all: List[Place.Input], dataset: Dataset)(using
      DbCon
  ): Place.ImportResult =
    val skip = suppressed(all.map(_.place.id.value).distinct)
    val batch = if skip.isEmpty then all else all.filterNot(in => skip.contains(in.place.id.value))
    val ids = batch.map(_.place.id).distinct
    val _ = batchUpdate(batch) { in =>
      val g = in.place.geometry
      val p = bounds(in.place.bounds)
      sql"""INSERT INTO places (id, label, kind, geom, period, source, properties, dataset)
            VALUES (${in.place.id}, ${in.place.label}, ${in.place.kind},
                    CASE WHEN $g::text IS NULL THEN NULL
                         ELSE ST_SetSRID(ST_GeomFromGeoJSON($g::text), 4326) END,
                    $p::int8range, ${in.place.source}, ${in.place.properties}::jsonb, ${dataset.wire})
            ON CONFLICT (id) DO UPDATE SET
              label = EXCLUDED.label, kind = EXCLUDED.kind, geom = EXCLUDED.geom,
              period = EXCLUDED.period, source = EXCLUDED.source,
              properties = EXCLUDED.properties, dataset = EXCLUDED.dataset""".update
    }

    val idList = ids.toList
    val _ = GazetteerRepo.linkSeedIds(idList.map(i => BPlaceRow.Id(i.value)))
    // only the seed's own names (source ''): what GeoNames, Wikidata or an LPF dataset gave a seed
    // place (V071) is theirs to replace, at their next import
    val _ =
      sql"DELETE FROM place_names     WHERE place_id = ANY($idList::text[]) AND NOT manual AND import_source = ''".update
        .run()
    val _ = sql"DELETE FROM place_relations WHERE child_id = ANY($idList::text[])".update.run()

    val names = batch.flatMap(in => in.names.map(in.place.id -> _))
    val _ = batchUpdate(names) { (pid, n) =>
      val p = bounds(n.bounds)
      sql"""INSERT INTO place_names (place_id, name, lang, period)
            VALUES ($pid, ${n.name}, ${n.lang}, $p::int8range)
            ON CONFLICT (place_id, name, lang, import_source, import_ref) DO UPDATE SET period = EXCLUDED.period""".update
    }

    // relations: only those whose parent actually exists once every row of the batch is in
    val wanted = batch.flatMap(_.relations)
    val parents = wanted.map(_.parentId).distinct.toList
    val existing = sql"SELECT id FROM places WHERE id = ANY($parents::text[])"
      .query[String]
      .run()
      .toSet
    val (ok, dangling) = wanted.partition(r => existing.contains(r.parentId.value))
    val _ = batchUpdate(ok) { r =>
      val p = bounds(r.bounds)
      sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
            VALUES (${r.childId}, ${r.parentId}, ${r.kind}, $p::int8range)
            ON CONFLICT (child_id, parent_id, kind) DO UPDATE SET period = EXCLUDED.period""".update
    }
    Place.ImportResult(
      places = Place.ImportResult.Places(ids.size),
      names = Place.ImportResult.Names(names.size),
      relations = Place.ImportResult.Relations(ok.size),
      failed = dangling
        .map(r => s"${r.childId}: no such parent '${r.parentId}'")
        .distinct
        .take(20)
        .map(Place.ImportResult.Failed(_))
    )

  // ── reads ────────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/places?q=&asOf=` — name/label search. A place matches when one of its names (valid at `asOf`) contains
    * `q`, or its label does — for a `q` of one or two characters, when a name or the label IS `q` (by the name key);
    * exact hits sort first, then prefixes, then the bundled seed before the imported sources (#1064), then the places
    * carrying the most findings. `q` empty lists the gazetteer by size.
    */
  def search(
      q: Option[SearchText],
      asOf: Option[DeepTime.SignedYear],
      limit: Limit,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  )(using DbCon): Vector[Place.Hit] =
    val text = q.map(_.value.trim).filter(_.nonEmpty)
    val cont = text.map(LikePattern.contains).getOrElse("%")
    val pre = text.map(LikePattern.startsWith).getOrElse("%")
    val exact = text.getOrElse("")
    val long = text.filter(_.length >= 3)
    val short = text.filter(_.length < 3).map(Geocode.key)
    val n = limit.value.max(1)
    // Before `modern-from` the time rule never shows a present-day GeoNames/Wikidata place — 1.15 M of
    // the 1.28 M places. Said in so many words ([[ShownBeforeModernSql]], implied by [[inTimeSql]]
    // then), it lets every pass read the 132 000 others through their own indexes (V076): at 2500 BCE
    // "Tro" checked 8 500 candidates by primary key, nearly all of them imported, and found its label
    // matches in all 1.28 M labels — 57 ms; through the partial indexes, 34 (2026-10-09).
    val pm = SqlLiteral(if asOf.exists(_ < modernFrom) then s" AND $ShownBeforeModernSql" else "")
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
        (Sql.raw(Sql.RawText("WITH ")) ++ timeWindowCte(asOf, modernFrom) ++ sql""" SELECT p.id FROM places p
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
        (Sql.raw(Sql.RawText("WITH ")) ++ timeWindowCte(asOf, modernFrom) ++ sql""", cand AS MATERIALIZED (
                SELECT p.id FROM places p WHERE $long::text IS NOT NULL AND p.label ILIKE $cont ESCAPE '\\'$pm
                UNION
                SELECT place_id FROM place_names WHERE $long::text IS NOT NULL AND name ILIKE $cont ESCAPE '\\'
                  AND ($asOf::bigint IS NULL OR period @> $asOf::bigint)
                UNION
                SELECT p.id FROM places p WHERE $short::text IS NOT NULL AND histmersion_place_key(p.label) = $short::text$pm
                UNION
                SELECT place_id FROM place_names WHERE $short::text IS NOT NULL AND histmersion_place_key(name) = $short::text)
              SELECT p.id FROM places p
              LEFT JOIN LATERAL (
                SELECT count(*)::int AS n FROM finding_places fp WHERE fp.place_id = p.id
              ) fc ON true
              WHERE ($text::text IS NULL OR p.id = ANY(ARRAY(SELECT id FROM cand)))$pm
                AND ${SqlLiteral(inTimeSql("p", asOf))}
              ORDER BY (lower(p.label) = lower($exact)) DESC,
                       (p.label ILIKE $pre ESCAPE '\\') DESC,
                       ${SqlLiteral(Geocoder.tierSql)},
                       fc.n DESC, length(p.label), p.id
              LIMIT $n""").query[String].run()
    if ranked.isEmpty then Vector.empty
    else
      val ids = ranked.toList
      val rows = sql"""SELECT p.id, p.label, p.kind, ${SqlLiteral(startCol)}, ${SqlLiteral(
          RangeSql.end("p.period")
        )}, p.source,
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
                        AND ($asOf::bigint IS NULL OR m.period @> $asOf::bigint)
                      ORDER BY (lower(m.name) = lower($exact)) DESC, length(m.name), m.name
                      LIMIT 1) AS matched
              FROM place_names n
              WHERE n.place_id = p.id
                AND ($asOf::bigint IS NULL OR n.period @> $asOf::bigint)
            ) nm ON true
            WHERE p.id = ANY($ids::text[])""".query[Place.Hit].run()
      val at = rows.map(h => h.id -> h).toMap
      ranked.flatMap(id => at.get(Place.Id(id)))

  /** `GET /api/places/{id}?asOf=` — the row, its geometry as GeoJSON, and the hierarchy AS OF `asOf`. A place with no
    * shape of its own (a continent is a hierarchy node, not a polygon) reports the UNION of its children's shapes,
    * which is what the web UI draws as its overlay.
    */
  def get(
      id: com.histmersion.domain.catalogue.Place.Id,
      asOf: Option[DeepTime.SignedYear] = None,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  )(using DbCon): Option[Place.Entry] =
    sql"""WITH g AS (
            SELECT COALESCE(p.geom, (
              SELECT ST_Union(c.geom) FROM place_relations r JOIN places c ON c.id = r.child_id
              WHERE r.parent_id = p.id AND c.geom IS NOT NULL
                AND ($asOf::bigint IS NULL OR r.period @> $asOf::bigint))) AS shape
            FROM places p WHERE p.id = $id)
          SELECT ${SqlLiteral(summaryCols)},
                 ST_AsGeoJSON(g.shape),
                 (SELECT count(*)::int FROM finding_places fp WHERE fp.place_id = p.id),
                 coalesce(ST_Dimension(g.shape) = 2, false),
                 ${SqlLiteral(timeBasisSql("p"))}, p.location_precision
          FROM places p, g WHERE p.id = $id""".query[PlaceDetailRow].run().headOption.map { r =>
      val fps = footprintsIn(id, asOf).toList
      val lks = sql"""SELECT place_id, scheme, external_id, relation FROM place_links
                      WHERE place_id = $id ORDER BY scheme, external_id"""
        .query[Place.Link]
        .run()
        .toList
      val tps = typesIn(id, asOf)
      Place.Entry(
        r.toSummary,
        r.geometry.map(g => Place.Entry.Geometry(g.value)),
        parents(id, asOf).toList,
        kids(id, asOf, Limit(Int.MaxValue), modernFrom).toList,
        Place.Counts.Findings(r.findings.value),
        asOf.map(y => Year(y.value)),
        Place.Entry.Contested(r.contested.value),
        fps,
        lks,
        tps,
        hasBoundary = Place.Entry.HasBoundary(r.hasBoundary.value),
        descriptions = descriptionsOf(id),
        timeBasis = r.timeBasis,
        locationPrecision = r.precision.transformInto[Place.Precision],
        siteType = siteTypeOf(id).map(t => Place.Entry.SiteType(t.value)),
        record = recordOf(id)
      )
    }

  /** What place `id`'s own record says beyond its names and dates ([[PlaceRecord]]): `properties.date_source`, the
    * labels of `periods`, `heritage` and `registers` — whatever shape they have, anything unexpected read as none.
    */
  private def recordOf(id: com.histmersion.domain.catalogue.Place.Id)(using DbCon): Place.Record =
    val dateSource =
      sql"SELECT properties->>'date_source' FROM places WHERE id = $id".query[Option[String]].run().headOption.flatten
    def array(key: ArrayKey) =
      s"CASE WHEN jsonb_typeof(p.properties->'${key.value}') = 'array' THEN p.properties->'${key.value}' ELSE '[]'::jsonb END"
    val periods = Sql
      .of(
        Sql.Text(
          s"SELECT coalesce(x->>'label', x->>'id') FROM places p, jsonb_array_elements(${array(ArrayKey("periods"))}) x" +
            " WHERE p.id = ? AND jsonb_typeof(x) = 'object'"
        ),
        Param.S(Param.S.V(id.value))
      )
      .frag
      .query[Option[String]]
      .run()
      .toList
      .flatten
    val designations = Sql
      .of(
        Sql.Text(
          s"SELECT x->>'id', x->>'label', x->>'start', x->>'end' FROM places p, jsonb_array_elements(${array(ArrayKey("heritage"))}) x" +
            " WHERE p.id = ? AND jsonb_typeof(x) = 'object' AND x->>'id' IS NOT NULL"
        ),
        Param.S(Param.S.V(id.value))
      )
      .frag
      .query[(
        Place.Designation.Id,
        Option[Place.Designation.Label],
        Option[Place.Designation.Start],
        Option[Place.Designation.End]
      )]
      .run()
      .toList
      .map(Place.Designation.apply)
    val registers = sql"""SELECT r.key, v FROM places p,
            jsonb_each(CASE WHEN jsonb_typeof(p.properties->'registers') = 'object' THEN p.properties->'registers' ELSE '{}'::jsonb END) r,
            jsonb_array_elements_text(CASE WHEN jsonb_typeof(r.value) = 'array' THEN r.value ELSE jsonb_build_array(r.value) END) v
          WHERE p.id = $id ORDER BY r.key, v"""
      .query[(String, String)]
      .run()
      .toList
      .map((property, value) =>
        Place.Register(
          Place.Register.Property(property),
          Place.Register.Id(value)
        )
      )
    Place.Record(
      dateSource.map(Place.Record.DateSource(_)),
      periods.map(Place.Record.Periods(_)),
      designations,
      registers
    )

  /** What site `id` is (#1449), read as the map's layer reads it ([[SiteTypes.of]]): a stored refinement, else its
    * gazetteer's own fields — so its card shows the icon its marker has.
    */
  private def siteTypeOf(id: com.histmersion.domain.catalogue.Place.Id)(using DbCon): Option[SiteTypes.SiteType.Id] =
    sql"""SELECT kind, site_type, properties->>'feature_types', properties->>'fcode', properties->>'class',
                 properties->>'p31'
          FROM places WHERE id = $id"""
      .query[(String, Option[String], Option[String], Option[String], Option[String], Option[String])]
      .run()
      .headOption
      .flatMap((kind, stored, ft, fcode, wd, p31) =>
        SiteTypes.of(
          Place.Id(id.value),
          Place.Kind.Wire(kind),
          stored.map(SiteTypes.SiteType.Id(_)),
          ft.map(SiteTypes.Pleiades.FeatureType(_)),
          fcode.map(SiteTypes.Geonames.Fcode(_)),
          wd.map(CatalogRecord.Wikidata.Class(_)),
          SiteTypes.p31Of(p31.map(CatalogRecord.Wikidata.P31Json(_)))
        )
      )

  /** What place `id` IS, as its sources describe it (V074): English first, then the other languages; within one
    * language the best source first (an LPF dataset, then Wikidata, then GeoNames).
    */
  private def descriptionsOf(id: com.histmersion.domain.catalogue.Place.Id)(using DbCon): List[Place.Description] =
    sql"""SELECT text, lang, import_source FROM place_descriptions WHERE place_id = $id
          ORDER BY (lang = 'en') DESC, lang,
                   CASE WHEN import_source LIKE 'lpf:%' THEN 1 WHEN import_source = 'wikidata' THEN 2
                        WHEN import_source = 'tgn' THEN 3 ELSE 4 END,
                   import_ref""".query[Place.Description].run().toList

  /** Parents valid at `asOf` (both the edge and the parent itself must be). */
  private def parents(id: com.histmersion.domain.catalogue.Place.Id, asOf: Option[DeepTime.SignedYear])(using
      DbCon
  ): Vector[Place.Summary] =
    sql"""SELECT ${SqlLiteral(summaryCols)} FROM place_relations r JOIN places p ON p.id = r.parent_id
          WHERE r.child_id = $id
            AND ($asOf::bigint IS NULL OR r.period @> $asOf::bigint)
            AND ($asOf::bigint IS NULL OR p.period @> $asOf::bigint)
          ORDER BY p.label, p.id""".query[Place.Summary].run()

  /** `GET /api/places/{id}/children?asOf=` — with a time, only the children the time rule shows ([[inTimeSql]]).
    */
  def children(
      id: com.histmersion.domain.catalogue.Place.Id,
      asOf: Option[DeepTime.SignedYear],
      limit: Limit,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  )(using DbCon): Vector[Place.Summary] =
    kids(id, asOf, limit, modernFrom)

  private def kids(id: com.histmersion.domain.catalogue.Place.Id, asOf: Option[DeepTime.SignedYear], limit: Limit, modernFrom: DeepTime.SignedYear)(using
      DbCon
  ): Vector[Place.Summary] =
    (Sql.raw(Sql.RawText("WITH ")) ++ timeWindowCte(asOf, modernFrom) ++
      sql""" SELECT ${SqlLiteral(summaryCols)} FROM place_relations r JOIN places p ON p.id = r.child_id
          WHERE r.parent_id = $id AND NOT ${SqlLiteral(importedSql("p"))}
            AND ($asOf::bigint IS NULL OR r.period @> $asOf::bigint)
            AND ${SqlLiteral(inTimeSql("p", asOf))}
          ORDER BY p.label, p.id LIMIT ${limit.value.max(1)}""").query[Place.Summary].run()

  /** SQL twin of [[com.histmersion.api.catalogue.RegionNode.rootEligible]]: a place that may head the hierarchy.
    * Region, polity or continent; not a Pleiades `withdrawn` record (marked as a property in part of the export and
    * only in the label in the rest); not an uncertain identification (`Theren? (river)`) or a reconstructed name
    * (`*Granouas`). Applies ONLY to root selection — a river still appears under the region that contains it.
    */
  private val rootEligibleSql =
    "p.kind IN ('region', 'polity', 'continent')" +
      " AND NOT coalesce((p.properties->>'withdrawn')::boolean, false)" +
      " AND p.label NOT ILIKE 'withdrawn%' AND p.label NOT LIKE '*%' AND p.label NOT LIKE '%?%'"

  /** The hierarchy AS OF one year, as a depth-tagged pre-order walk (a recursive CTE over `place_relations` whose own
    * `period` covers `asOf`). `root` empty starts from every ELIGIBLE place that has no parent — the world level;
    * without the eligibility filter that level is 25 Pleiades rivers and mountains with the five continents lost among
    * them. Bounded by `maxDepth` (which is also what makes a cyclic edge harmless) and by `limit`.
    */
  def hierarchy(
      asOf: Option[DeepTime.SignedYear],
      root: Option[com.histmersion.domain.catalogue.Place.Id],
      maxDepth: Depth,
      limit: Limit,
      depthCap: Depth = Depth(Int.MaxValue),
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  )(using DbCon): Vector[Place.TreeRow] =
    // `maxDepth` is a caller's number; `depthCap` is the configured ceiling (`places.tree-depth`).
    // A caller asking for depth 10 000 walked ten thousand levels of a recursive CTE.
    val depth = math.max(0, math.min(maxDepth.value, depthCap.value))
    (Sql.raw(Sql.RawText("WITH RECURSIVE ")) ++ timeWindowCte(asOf, modernFrom) ++ sql""", edges AS (
            SELECT r.child_id, r.parent_id FROM place_relations r
            WHERE $asOf::bigint IS NULL OR r.period @> $asOf::bigint
          ), roots AS (
            SELECT p.id FROM places p
            WHERE ($root::text IS NOT NULL AND p.id = $root::text)
               OR ($root::text IS NULL
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
            WHERE t.depth + 1 <= $depth AND NOT ${SqlLiteral(importedSql("c"))}
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
          ORDER BY t.path, t.depth LIMIT ${limit.value.max(1)}""").query[Place.TreeRow].run()

  /** The gazetteer places one finding was materialised into. */
  def placesOf(findingId: EventMatcher.NearFinding.Id)(using DbCon): Vector[Place.Summary] =
    sql"""SELECT ${SqlLiteral(summaryCols)} FROM finding_places fp JOIN places p ON p.id = fp.place_id
          WHERE fp.finding_id = $findingId ORDER BY p.label, p.id"""
      .query[Place.Summary]
      .run()

  // ── the materialisation ──────────────────────────────────────────────────────────────────────

  /** Rebuild `finding_places` from scratch (`histmersion places rebuild` / `POST /api/places/rebuild`).
    *
    * A finding belongs to a place when
    *   - it has geometry and the place COVERS it (`ST_CoveredBy`, not `ST_Within`: a finding exactly on a shared border
    *     must count in BOTH polygons — plan §1.6a), or the place is a POINT — a Pleiades site has no extent — and the
    *     finding is within `toleranceKm` of it; **and**
    *   - their periods overlap, where a NULL period on either side means "always" (so an undated finding still gets its
    *     geography, and a modern country contains everything);
    *   - or, for a finding with NO geometry, its `region_name` equals a place name exactly (case-insensitively) and the
    *     periods overlap.
    * A GeoNames or Wikidata place ([[importedSql]]) takes part in none of these: it gets exactly the findings whose
    * point IS its point (the geocoder chose it for the finding's place name), when the periods overlap.
    *
    * The point test is written as an index-usable `&& ST_Expand(...)` pre-filter followed by the exact geography
    * distance: `ST_DWithin` on a `::geography` cast alone cannot use the GiST index on `places.geom`, and a cross join
    * of 15 000 sites × 1 000 findings is not free. The expansion is in DEGREES, widened by `1/cos(latitude)` so it is
    * always a superset of the true circle.
    */
  /** The SQL of [[geoJson]], as a value — pure, so the de-duplication rules are unit-tested. */
  private[places] def geoJsonQuery(
      kind: Option[PlaceRepo.GeoJsonQueryKind],
      asOf: Option[DeepTime.SignedYear],
      limit: GeoJsonQueryLimit,
      modernFrom: PlaceRepo.GeoJsonQueryModernFrom = DefaultModernFrom
  ): Sql =
    geoJsonTimeQuery(kind, asOf.fold(GeoTime.Always)(y => GeoTime.At(Year(y.value))), limit, modernFrom)

  /** [[geoJsonQuery]] for any [[GeoTime]]: across all time, at a year, or over a whole range. `pairsByIndex = false`
    * makes a range of areas pair its shapes the plain way ([[rangeShadowedSql]]) — the reference `PlaceGeoSuite` holds
    * the indexed pairing to.
    */
  private[places] def geoJsonTimeQuery(
      kind: Option[com.histmersion.domain.catalogue.Place.Kind.Wire],
      time: GeoTime,
      limit: Limit,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom,
      pairsByIndex: PairsByIndex = PairsByIndex(true)
  ): Sql =
    import Param.*
    val k = kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
    val window = time.window.map((a, b) => (signed(a), signed(b)))
    val within = time match
      case w: GeoTime.Within => Some(w)
      case _                 => None
    val where = List(
      Some(Sql.raw(Sql.RawText("p.geom IS NOT NULL"))),
      Some(Sql.of(Sql.OfText("p.label !~* ?"), S(Param.S.V(UninformativeLabel)))),
      // across all time a present-day place is never drawn; at a year, the time rule decides
      // (physical features always, present-day places from `modern-from`, undated ones by findings)
      Some(raw(if window.isEmpty then s"NOT $ModernSql" else inWindowSql("p", window, k.map(com.histmersion.domain.catalogue.Place.Kind.Wire(_))))),
      k.map(v => Sql.of(Sql.OfText("p.kind = ?"), S(Param.S.V(v)))),
      // an undated import is never on a time-sliced map, findings or not (its open range is unknown)
      window.map(_ => raw(s"NOT $UndatedImportSql")),
      // nor is a polity known only by a point and no date (a late Roman province Pleiades does not
      // date, `lint --rule place-kind`): a state is not "always"
      window.map(_ => raw(UndatedPolityPointSql))
    ).flatten
    // `live` carries no geometry — copying 138 large polygons into a CTE's tuplestore cost ~2 s;
    // the full shapes are read from `places` only for candidate pairs and for the rows returned.
    //
    // [[BaseLayerSql]] in its two disjoint halves, read apart: the curated places through
    // `places_base_layer` (V075), and the imported ones a finding is attached to through
    // `finding_places`. As one OR the sites layer read all 930 000 sites alive in a year to keep
    // 6 400 (0.4 s); apart, 58 ms for the same rows. Each half carries its time basis (the window
    // `tw` heads the statement), so the ranking below sees why each place is shown. Over a range
    // each row also carries its period (`per`): which shapes coexist, and which version comes first.
    //
    // What the ranking and the collapse read of a shape — its area, box and normalised label — is
    // stored with it (V090): computed here, each decompressed the polygon of every row alive, ~70 ms
    // apiece over a whole range.
    val cols = raw(
      "SELECT p.id, p.kind, " +
        s"$sourceTierSql AS tier, p.label_norm AS norm, p.shape_area AS area, " +
        s"p.shape_box AS bb, ${timeBasisSql("p")} AS basis${if within.isDefined then ", p.period AS per" else ""}" +
        " FROM places p WHERE "
    )
    val rest = Sql(Sql.Text(where.map(_.text.value).mkString(" AND ")), where.flatMap(_.params))
    // At a year both halves are read through a (kind, period) GiST (V076): `places_base_layer_time`
    // (the curated predicate) and `places_dated_import` ([[DatedImportSql]], implied there by "not
    // curated" and "not an undated import" — stated so the planner can use the index). The few
    // hundred dated imports alive then are each probed for a finding (`OFFSET 0` keeps the EXISTS a
    // per-row probe): as a semi-join the planner, which cannot estimate the year's overlap within a
    // partial index, read every imported place with a finding instead — 9 500 lookups, 35 ms a year.
    // Across all time the semi-join is the right shape and stays.
    val imported =
      if window.isEmpty then s" AND NOT $CuratedSql AND p.id IN (SELECT fp.place_id FROM finding_places fp)"
      else
        s" AND NOT $CuratedSql AND $DatedImportSql" +
          " AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id OFFSET 0)"
    val live = Sql.raw(Sql.RawText("WITH ")) ++ windowCte(window, modernFrom) ++ raw(", live AS (") ++
      cols ++ rest ++ raw(s" AND $CuratedSql") ++ raw(" UNION ALL ") ++
      cols ++ rest ++ raw(imported) ++ raw(")")
    // only a time view collapses duplicates: across all time, successive shapes are all real
    val shadowed =
      if window.isEmpty then Sql.raw(Sql.RawText(", shadowed AS (SELECT NULL::text AS id WHERE false)"))
      else
        within.fold(
          raw(
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
              s" > $SameStateOverlap * least(hi.area, lo.area)))"
          )
        )(_ =>
          // over a range a layer of areas probes its few sketches and OHM shapes for partners through
          // `places_area_time_geom` (V090); a kindless or site layer keeps the plain pairing
          k.filterNot(_ == Place.Kind.Site)
            .filter(_ => pairsByIndex.value)
            .fold(raw(rangeShadowedSql))(_ =>
              raw(", shadowed AS (SELECT DISTINCT pr.lid AS id FROM (") ++
                rangePairsSql(rest, small = 3, partner = "c.tier < 3", smallIsLo = true) ++ raw(" UNION ALL ") ++
                rangePairsSql(rest, small = 1, partner = "c.tier = 2", smallIsLo = false) ++
                raw(") pr" + rangeShadowTestSql + ")")
            )
        )
    // what is drawn: every unshadowed live shape; over a range, one VERSION of each state —
    // [[pickVersionSql]]'s `shown`
    val (shown, drawn) = within.fold((Sql.raw(Sql.RawText("")), "live WHERE id NOT IN (SELECT id FROM shadowed)"))(w =>
      (raw(pickVersionSql(w)), "shown")
    )
    // what was there then before what is merely shown then: among equal areas (every point) a dated
    // place outranks one dated by its findings, a physical feature and a present-day one — at 1600 the
    // 4 000 Natural Earth cities would otherwise take the whole site limit by id
    def rank(l: String) =
      s"${l}area DESC, CASE ${l}basis WHEN 'dated' THEN 0 WHEN 'findings' THEN 1 WHEN 'timeless' THEN 2 ELSE 3 END, ${l}id"
    // RANK FIRST, then read the rows shown: the limit is taken over `live` alone, and only those rows
    // are joined back to `places` for their label, dates and shape — not all 14 700 sites alive in
    // 500 CE, one index lookup each, to keep 200 (2026-10-09)
    live ++ shadowed ++ shown ++ raw(
      s" SELECT l.id, p.label, l.kind, $startCol AS ps, ${RangeSql.end("p.period")} AS pe, $datingCol AS dating," +
        " l.basis," +
        " coalesce(p.shape_dim = 2, false) AS has_boundary," +
        // a polity is drawn on land when the land is loaded (V025), an area as its stored web outline
        // (V090: simplified to the map, written once); `geom` stays the published shape
        " coalesce(p.web_geojson, ST_AsGeoJSON(coalesce(p.geom_clipped, p.geom), 5)) AS geojson," +
        // what a site is (#1449): a stored refinement, else its gazetteer's own fields (core.SiteTypes)
        " p.site_type, p.properties->>'feature_types', p.properties->>'fcode', p.properties->>'class'," +
        " p.properties->>'p31'" +
        s" FROM (SELECT * FROM $drawn ORDER BY ${rank("")}"
    ) ++
      of(" LIMIT ?", I(Param.I.V(limit.value.max(1)))) ++
      raw(s") l JOIN places p ON p.id = l.id ORDER BY ${rank("l.")}")

  /** The test of a candidate pair `pr` (`lid` under `hid`, `same_name`, `small` the smaller area, `lbb` and `hbb` their
    * boxes): the same normalised name, or the shapes' intersection covering more than [[SameStateOverlap]] of the
    * smaller. The intersection lies within the intersection of the two boxes, so a pair whose boxes overlap by no more
    * than that cannot pass and skips the polygon intersection — an exact shortcut. Each shape is fetched by id for its
    * pair, and repaired only when it is invalid (`shape_valid`, V090): ST_MakeValid returns a valid shape as it is, and
    * checking that again was half the cost of the test (13 of 25 ms over -3000..2000).
    */
  private[places] val rangeShadowTestSql: String =
    def overlap(lo: String, hi: String) =
      s"greatest(0, least($hi(pr.lbb), $hi(pr.hbb)) - greatest($lo(pr.lbb), $lo(pr.hbb)))"
    def valid(g: String) = s"CASE WHEN $g.valid THEN $g.geom ELSE ST_MakeValid($g.geom) END"
    " CROSS JOIN LATERAL (SELECT geom, shape_valid AS valid FROM places WHERE id = pr.hid OFFSET 0) gh" +
      " CROSS JOIN LATERAL (SELECT geom, shape_valid AS valid FROM places WHERE id = pr.lid OFFSET 0) gl" +
      s" WHERE pr.same_name OR (${overlap("ST_XMin", "ST_XMax")} * ${overlap("ST_YMin", "ST_YMax")}" +
      s" > $SameStateOverlap * pr.small AND ST_Intersects(gh.geom, gl.geom)" +
      s" AND ST_Area(ST_Intersection(${valid("gh")}, ${valid("gl")}))" +
      s" > $SameStateOverlap * pr.small)"

  /** The CTE `shadowed` over a whole range ([[GeoTime.Within]]): the rule of a year — a shape is shadowed by a LOWER
    * tier's shape of the same normalised name or covering [[SameStateOverlap]] of the smaller — among shapes that
    * COEXIST (`per && per`; at a year every live row does, and two periods that overlap each other overlap the range
    * there too). The pairs are read as the two small tiers against the rest — the sketches (3) against every better
    * shape, OHM (1) against the datasets (2), [[sourceTierSql]] — because as one `tier <` join the 13 000 shapes of all
    * time met each other: 170 M comparisons, past the statement timeout; split, 970 pairs in 0.3 s for the whole of
    * -3000..2000 (2026-10-16). Each shape is fetched by id for its pair, as at a year.
    */
  private[places] val rangeShadowedSql: String =
    def pairs(lo: String, hi: String) =
      "SELECT lo.id AS lid, hi.id AS hid, lo.norm = hi.norm AS same_name, least(hi.area, lo.area) AS small," +
        " lo.bb AS lbb, hi.bb AS hbb" +
        s" FROM (SELECT * FROM live WHERE $lo) lo JOIN (SELECT * FROM live WHERE $hi) hi" +
        " ON hi.tier < lo.tier AND hi.kind = lo.kind AND hi.bb && lo.bb AND hi.per && lo.per"
    ", shadowed AS (SELECT DISTINCT pr.lid AS id FROM (" + pairs("tier = 3", "tier < 3") + " UNION ALL " +
      pairs("tier = 2", "tier = 1") + ") pr" + rangeShadowTestSql + ")"

  /** One half of the range's candidate pairs for a layer of AREAS ([[rangeShadowedSql]] reads the same pairs by
    * comparing all of `live` with itself): each `live` shape of the small tier `small` (3, the sketches, ~50; or 1,
    * OpenHistoricalMap, ~20) finds its partners — the shapes of the same kind that coexist with it and whose box meets
    * its box — through `places_area_time_geom` (V090), and a partner is kept when it is in `live` and of the `partner`
    * tier: `live`'s own predicate (`rest`, and the base layer's two halves) is evaluated on the few rows the index
    * returns. Joined back to `live` instead, the planner — which estimates a CTE's filtered rows at a few dozen — chose
    * a nested loop over all 13 000 (13 M comparisons, 0.7 s for -3000..2000); as a hash join on kind the two tiers met
    * 650 000 times (80 ms). `smallIsLo`: the small tier's shape is the one shadowed (a sketch under a real dataset) or
    * the one shadowing (OHM over Cliopatria).
    */
  private[places] def rangePairsSql(rest: Sql, small: Int, partner: String, smallIsLo: Boolean): Sql =
    val (lo, hi) = if smallIsLo then ("s", "c") else ("c", "s")
    raw(
      s"SELECT $lo.id AS lid, $hi.id AS hid, s.norm = c.norm AS same_name, least(c.area, s.area) AS small," +
        s" $lo.bb AS lbb, $hi.bb AS hbb FROM (SELECT * FROM live WHERE tier = $small) s CROSS JOIN LATERAL" +
        s" (SELECT * FROM (SELECT p.id, $sourceTierSql AS tier, p.label_norm AS norm, p.shape_area AS area," +
        " p.shape_box AS bb FROM places p WHERE p.kind <> 'site' AND p.kind = s.kind AND p.period && s.per" +
        " AND p.geom && s.bb::geometry AND "
    ) ++ rest ++
      raw(
        s" AND ($CuratedSql OR ($DatedImportSql AND EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id)))" +
          s") c WHERE $partner) c"
      )

  /** The CTE `shown` over a whole range `w` ([[GeoTime.Within]]): ONE VERSION of each state alive at any time in it.
    * The unshadowed live shapes are grouped into "the same state over time" by kind and NORMALISED LABEL
    * ([[normLabelSql]]; a label that normalises to nothing stands alone) — never by overlap, because successive states
    * on one territory (the Hittites, then Phrygia; Middle and New Kingdom Egypt) overlap heavily and are different
    * states, and never by Wikidata id, which Cliopatria gives to whole families of coexisting polygons (the Eighteen
    * Kingdoms of 206 BCE share one; an alliance polygon carries its senior partner's). Each shape is clipped to the
    * range (`es..ee`, an open side read as the range's); of each group the shapes alive at its FIRST year there are
    * kept (`latest`: its LAST year) — the state as the range first (last) shows it, so two shapes of one state alive
    * together then both stay, as at a year. Cross-tier duplicates are gone already (`shadowed`, among shapes that
    * coexist), and different states may overlap: all are drawn.
    */
  private[places] def pickVersionSql(w: GeoTime.Within): String =
    val (lo, hi) = (w.from, w.to)
    val (edge, anchor) = if w.latest.value then ("ee", "max") else ("es", "min")
    ", kept AS (SELECT live.*," +
      s" greatest($lo::bigint, coalesce(${RangeSql.start("per")}, $lo::bigint)) AS es," +
      s" least($hi::bigint, coalesce(${RangeSql.end("per")}, $hi::bigint)) AS ee" +
      " FROM live WHERE id NOT IN (SELECT id FROM shadowed))" +
      ", shown AS (SELECT id, kind, tier, norm, area, bb, basis FROM (SELECT kept.*," +
      s" $anchor($edge) OVER (PARTITION BY kind, coalesce(nullif(norm, ''), id)) AS anchor FROM kept) v" +
      s" WHERE v.$edge = v.anchor)"

  /** `GET /api/places/geo` — a FeatureCollection for the map. When `asOf` is given, each state is drawn ONCE: of shapes
    * from different source tiers that share a normalised name or overlap by [[SameStateOverlap]], only the best tier
    * survives (within one source nothing is dropped — Cliopatria's overlapping vassal/allegiance polygons are real).
    * The limit applies after that, largest shapes first, so it drops the smallest rather than whatever sorts last by
    * name.
    */
  def geoJson(
      kind: Option[com.histmersion.domain.catalogue.Place.Kind.Wire],
      asOf: Option[DeepTime.SignedYear],
      limit: Limit,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  )(using DbCon): GeoJson =
    geoJsonAt(kind, asOf.fold(GeoTime.Always)(y => GeoTime.At(Year(y.value))), limit, modernFrom)

  /** [[geoJson]] for any [[GeoTime]]. Over a range ([[GeoTime.Within]]) every state alive at any time in it is drawn,
    * one version each ([[pickVersionSql]]), after the same cross-tier collapse among the shapes that coexist; the limit
    * still comes last, largest first.
    */
  def geoJsonAt(
      kind: Option[com.histmersion.domain.catalogue.Place.Kind.Wire],
      time: GeoTime,
      limit: Limit,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  )(using DbCon): GeoJson =
    val rows = geoJsonTimeQuery(kind, time, limit, modernFrom).frag
      .query[
        (
            String,
            String,
            String,
            Option[Long],
            Option[Long],
            Place.Dating,
            Place.TimeBasis,
            Boolean,
            String,
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Option[String]
        )
      ]
      .run()
    // one buffer for the whole collection: over a whole range it is ~7 MB of outlines, and building
    // a string per feature, then joining them, then wrapping the join copied it three times
    val out = java.lang.StringBuilder(rows.iterator.map(_._9.length + 256).sum + 64)
    val _ = out.append("""{"type":"FeatureCollection","features":[""")
    rows.zipWithIndex.foreach {
      case (
            (
              id,
              label,
              placeKind,
              ps,
              pe,
              dating,
              basis,
              hasBoundary,
              geojson,
              stored,
              featureTypes,
              fcode,
              wdClass,
              p31
            ),
            i
          ) =>
        val siteType = SiteTypes.of(
          Place.Id(id),
          Place.Kind.Wire(placeKind),
          stored.map(SiteTypes.SiteType.Id(_)),
          featureTypes.map(SiteTypes.Pleiades.FeatureType(_)),
          fcode.map(SiteTypes.Geonames.Fcode(_)),
          wdClass.map(CatalogRecord.Wikidata.Class(_)),
          SiteTypes.p31Of(p31.map(CatalogRecord.Wikidata.P31Json(_)))
        )
        import io.circe.Json
        val propsObj = Json
          .obj(
            "id" -> Json.fromString(id),
            "label" -> Json.fromString(label),
            "kind" -> Json.fromString(placeKind),
            // a region or polity drawn from a point has no boundary yet: the map labels it, never a dot
            "hasBoundary" -> Json.fromBoolean(hasBoundary),
            "dating" -> Json.fromString(dating.wire.value),
            // why a time view shows it ([[PlaceTimeBasis]]): `findings` is drawn as "dated by its findings"
            "basis" -> Json.fromString(basis.wire.value)
          )
          .deepMerge(
            Json.fromFields(
              ps.map(v => "periodStart" -> Json.fromLong(v)).toList ++
                pe.map(v => "periodEnd" -> Json.fromLong(v)).toList ++
                siteType.map(t => "siteType" -> Json.fromString(t.value)).toList
            )
          )
        val _ = out
          .append(if i == 0 then "" else ",")
          .append("""{"type":"Feature","properties":""")
          .append(propsObj.noSpaces)
          .append(""","geometry":""")
          .append(geojson)
          .append('}')
    }
    GeoJson(out.append("]}").toString)

  /** Build parent-child edges from spatial containment: every point place that falls inside a polygon place gets a
    * `spatial_containment` relation edge. Idempotent (`ON CONFLICT DO NOTHING`). Run after imports to wire up the
    * hierarchy that Pleiades and Cliopatria don't provide.
    */
  def buildSpatialHierarchy()(using DbCon): SpatialHierarchyResult =
    val polygons =
      sql"SELECT count(*)::int FROM places WHERE geom IS NOT NULL AND ST_GeometryType(geom) IN ('ST_Polygon', 'ST_MultiPolygon')"
        .query[Int]
        .run()
        .head
    val points = sql"SELECT count(*)::int FROM places WHERE geom IS NOT NULL AND ST_GeometryType(geom) = 'ST_Point'"
      .query[Int]
      .run()
      .head
    val inserted = sql"""INSERT INTO place_relations (child_id, parent_id, kind, period)
          SELECT site.id, region.id, 'spatial_containment', region.period
          FROM places site, places region
          WHERE ST_GeometryType(region.geom) IN ('ST_Polygon', 'ST_MultiPolygon')
            AND ST_GeometryType(site.geom) = 'ST_Point'
            AND NOT ${SqlLiteral(importedSql("site"))} AND NOT ${SqlLiteral(importedSql("region"))}
            AND ST_Contains(region.geom, site.geom)
            AND site.id != region.id
          ON CONFLICT (child_id, parent_id, kind) DO NOTHING""".update.run()
    SpatialHierarchyResult(
      edgesCreated = SpatialHierarchyResult.EdgesCreated(inserted),
      polygonPlaces = SpatialHierarchyResult.PolygonPlaces(polygons),
      pointPlaces = SpatialHierarchyResult.PointPlaces(points)
    )

  // ── resolving a Filter's `places` values ─────────────────────────────────────────────────────

  /** Which of a filter's `places` values are gazetteer ids, and what each one stands for: itself plus every descendant
    * reachable through relations whose own `period` overlaps `filter.period` (plan §1.6a — "as of the query period").
    * Values that are not place ids are absent from the map and fall back to the name ILIKE.
    *
    * Cheap by construction: no values ⇒ no query at all, and the closure is one recursive CTE per chip (there are never
    * many).
    */
  def scopeFor(values: List[ScopeForValues], period: Option[Period])(using DbCon): PlaceScope =
    val wanted = values.map(_.value.trim).filter(_.nonEmpty).distinct
    if wanted.isEmpty then PlaceScope.empty
    else
      val joined = wanted.toList
      // an OLD id (V055) stands for the place it was renamed to: a saved link, a bookmarked filter
      // or an agent's `place=<old id>` keeps matching what it matched before the rename
      val renamed = sql"""SELECT alias, place_id FROM place_aliases
                          WHERE alias = ANY($joined::text[])
                            AND NOT EXISTS (SELECT 1 FROM places WHERE id = alias)"""
        .query[(String, String)]
        .run()
        .toMap
      val target = wanted.map(v => v -> renamed.getOrElse(v, v)).toMap
      val ids = target.values.toList.distinct
      val rows = sql"SELECT id, label FROM places WHERE id = ANY($ids::text[])"
        .query[(String, String)]
        .run()
      val labelOf = rows.toMap
      val known = wanted.filter(v => labelOf.contains(target(v)))
      val labels = known.map(v => PlaceScope.LabelsKey(v) -> PlaceScope.LabelsValue(labelOf(target(v)))).toMap
      if known.isEmpty then PlaceScope.empty
      else
        val lo = period.map(_.start)
        val hi = period.map(_.end)
        PlaceScope(
          known.map { value =>
            val id = target(value)
            PlaceScope.ByValueKey(value) -> sql"""WITH RECURSIVE d(id) AS (
                        SELECT $id
                        UNION
                        SELECT r.child_id FROM place_relations r JOIN d ON r.parent_id = d.id
                        JOIN places c ON c.id = r.child_id
                        WHERE NOT ${SqlLiteral(importedSql("c"))}
                          AND ($lo::bigint IS NULL
                               OR r.period && int8range($lo::bigint, $hi::bigint, '[]'))
                      )
                      SELECT d.id FROM d JOIN places p ON p.id = d.id""".query[String].run().toList.map(PlaceScope.ByValueValue.assume(_))
          }.toMap,
          labels
        )

  // ── temporal geography (V016) ──────────────────────────────────────────────────────────────

  def footprints(placeId: com.histmersion.domain.catalogue.Place.Id, asOf: Option[DeepTime.SignedYear])(using
      DbCon
  ): Vector[Place.Footprint] =
    footprintsIn(placeId, asOf)

  /** A place's footprints valid at `asOf` (all of them without one) — [[footprints]] and the place detail read the same
    * rows.
    */
  private def footprintsIn(placeId: com.histmersion.domain.catalogue.Place.Id, asOf: Option[DeepTime.SignedYear])(using
      DbCon
  ): Vector[Place.Footprint] =
    sql"""SELECT f.id, f.place_id, f.role, ${SqlLiteral(RangeSql.cols("f.period"))},
                 f.certainty, f.accuracy_m, f.citekey
          FROM place_footprints f
          WHERE f.place_id = $placeId
            AND ($asOf::bigint IS NULL OR f.period @> $asOf::bigint)
          ORDER BY f.role, ${SqlLiteral(RangeSql.start("f.period"))}""".query[FootprintRow].run().map(_.toDto)

  def links(placeId: com.histmersion.domain.catalogue.Place.Id)(using DbCon): Vector[Place.Link] =
    sql"""SELECT place_id, scheme, external_id, relation FROM place_links
          WHERE place_id = $placeId ORDER BY scheme, external_id"""
      .query[Place.Link]
      .run()

  def types(placeId: com.histmersion.domain.catalogue.Place.Id, asOf: Option[DeepTime.SignedYear])(using DbCon): List[Place.Type] =
    typesIn(placeId, asOf)

  /** A place's types valid at `asOf` — [[types]] and the place detail read the same rows. An undated type (`(,)`, every
    * year) reads both bounds back as NULL: Postgres reads an infinite bound so.
    */
  private def typesIn(placeId: com.histmersion.domain.catalogue.Place.Id, asOf: Option[DeepTime.SignedYear])(using
      DbCon
  ): List[Place.Type] =
    sql"""SELECT place_id, type_label, ${SqlLiteral(RangeSql.cols("period"))}, citekey
          FROM place_types
          WHERE place_id = $placeId
            AND ($asOf::bigint IS NULL OR period @> $asOf::bigint)
          ORDER BY type_label""".query[PlaceTypeRow].run().map(_.toDto).toList

  // ── renames (V055) ─────────────────────────────────────────────────────────────────────────────

  /** A new id for a place, the old one kept as an alias (V055): every foreign key into `places(id)` cascades;
    * attestations and asset links name a place without a key and are moved here. Refuses an id another place has, or an
    * old id of another place; renaming back to an old id drops that alias. The caller's transaction (a bundle import
    * follows a rename made in the other collection, #888).
    */
  private[places] def renameIn(oldId: com.histmersion.domain.catalogue.Place.Id, newId: com.histmersion.domain.catalogue.Place.Id)(using DbCon): Either[Api.Error, Unit] =
    def exists(i: com.histmersion.domain.catalogue.Place.Id) = sql"SELECT 1 FROM places WHERE id = $i".query[Int].run().nonEmpty
    val aliasOf = sql"SELECT place_id FROM place_aliases WHERE alias = $newId"
      .query[com.histmersion.domain.catalogue.Place.Id]
      .run()
      .headOption
    if !exists(oldId) then
      Left(Api.Error(Api.Error.Message(s"No place '${oldId.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
    else if exists(newId) then
      Left(
        Api.Error(
          Api.Error.Message(s"'${newId.value}' is already the id of another place"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if aliasOf.exists(_ != oldId) then
      Left(
        Api.Error(
          Api.Error.Message(s"'${newId.value}' is an old id of '${aliasOf.get.value}' and still resolves to it — pick another"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      sql"DELETE FROM place_aliases WHERE alias = $newId".update.run()
      sql"UPDATE places SET id = $newId WHERE id = $oldId".update.run()
      sql"UPDATE attestations SET target_id = $newId WHERE target_type = 'place' AND target_id = $oldId".update.run()
      sql"UPDATE asset_links SET target_id = $newId WHERE target_type = 'place' AND target_id = $oldId".update.run()
      sql"""INSERT INTO place_aliases (alias, place_id) VALUES ($oldId, $newId)
            ON CONFLICT (alias) DO UPDATE SET place_id = EXCLUDED.place_id""".update.run()
      Right(())

  /** [[renameIn]] in a transaction of its own. */
  def rename(oldId: com.histmersion.domain.catalogue.Place.Id, newId: com.histmersion.domain.catalogue.Place.Id)(using DbCon): Either[Api.Error, Unit] =
    renameIn(
      com.histmersion.domain.catalogue.Place.Id.applyUnsafe(oldId.value.trim),
      com.histmersion.domain.catalogue.Place.Id.applyUnsafe(newId.value.trim)
    )

  // ── a person's edits (#987) ─────────────────────────────────────────────────────────────────────

  private def labelOf(id: com.histmersion.domain.catalogue.Place.Id)(using DbCon): Option[Label] =
    sql"SELECT label FROM places WHERE id = $id".query[String].run().headOption.map(Label(_))

  private def nameChanged(
      id: com.histmersion.domain.catalogue.Place.Id,
      label: Label,
      name: Name,
      lang: Lang,
      added: Boolean,
      changed: Boolean,
      wasImported: Boolean
  ): Place.NameChanged =
    Place.NameChanged(
      Place.Id(id.value),
      Place.NameChanged.Label(label.value),
      Place.NameChanged.Name(name.value),
      Place.NameChanged.Lang(lang.value),
      added = Place.NameChanged.Added(added),
      changed = Place.NameChanged.Changed(changed),
      wasImported = Place.NameChanged.WasImported(wasImported)
    )

  /** Give place `id` the name `name` in `lang`, as a MANUAL name (V068): no import deletes it. A name the place already
    * has from an import becomes manual (so a later seed that drops it keeps it).
    */
  def addName(id: com.histmersion.domain.catalogue.Place.Id, name: Name, lang: Lang)(using
      DbCon
  ): Either[Api.Error, Place.NameChanged] =
    labelOf(id).toRight(Api.Error(Api.Error.Message(s"No place '${id.value}'"), Some(Api.Error.Code(Write.CodeNotFound)))).map { label =>
      val before =
        sql"SELECT manual FROM place_names WHERE place_id = $id AND name = $name AND lang = $lang AND import_source = ''"
          .query[Boolean]
          .run()
          .headOption
      if !before.contains(true) then
        val _ = sql"""INSERT INTO place_names (place_id, name, lang, manual) VALUES ($id, $name, $lang, true)
                      ON CONFLICT (place_id, name, lang, import_source, import_ref) DO UPDATE SET manual = true""".update
          .run()
      nameChanged(id, label, name, lang, added = true, changed = !before.contains(true), wasImported = before.contains(false))
    }

  /** Remove a name a person added. An IMPORTED name is refused — the next seed would bring it back. */
  def removeName(id: com.histmersion.domain.catalogue.Place.Id, name: Name, lang: Lang)(using
      DbCon
  ): Either[Api.Error, Place.NameChanged] =
    labelOf(id).toRight(Api.Error(Api.Error.Message(s"No place '${id.value}'"), Some(Api.Error.Code(Write.CodeNotFound)))).flatMap { label =>
      sql"SELECT bool_or(manual) FROM place_names WHERE place_id = $id AND name = $name AND lang = $lang HAVING count(*) > 0"
        .query[Boolean]
        .run()
        .headOption match
        case None =>
          val langs = sql"SELECT lang FROM place_names WHERE place_id = $id AND name = $name AND manual"
            .query[String]
            .run()
            .map(l => if l.isEmpty then "no language" else l)
          val hint = if langs.isEmpty then "" else s" (it has it with ${langs.mkString(", ")} — pass that language)"
          Left(
            Api.Error(
              Api.Error.Message(s"'$label' [$id] has no name '$name'${if lang.value.isEmpty then "" else s" in $lang"}$hint"),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
        case Some(false) =>
          Left(
            Api.Error(
              Api.Error.Message(
                s"'$name' is a name the gazetteer seed or an imported source gives '$label' [$id], not one added by hand — " +
                  "removing it would last only until the next import"
              ),
              Some(Api.Error.Code(Write.CodeInvalid))
            )
          )
        case Some(true) =>
          val _ =
            sql"DELETE FROM place_names WHERE place_id = $id AND name = $name AND lang = $lang AND manual".update
              .run()
          Right(nameChanged(id, label, name, lang, added = false, changed = true, wasImported = false))
    }

  /** The names people added, by place then name; `id` narrows to one place. */
  def manualNames(id: Option[com.histmersion.domain.catalogue.Place.Id])(using DbCon): List[ManualPlaceName] =
    sql"""SELECT n.place_id, p.label, n.name, n.lang FROM place_names n JOIN places p ON p.id = n.place_id
          WHERE n.manual AND ($id::text IS NULL OR n.place_id = $id::text)
          ORDER BY n.place_id, n.name, n.lang""".query[ManualPlaceName].run().toList

  /** Create a person's own place (`dataset = local`, V052): refused when the id is taken — by a place or as an old id
    * of one (V055) — so `places add` can never overwrite seed data.
    */
  def addPlace(in: Place.Input)(using
      DbCon
  ): Either[Api.Error, Place.Summary] =
    val id = in.place.id
    val taken = sql"SELECT 1 FROM places WHERE id = $id".query[Int].run().nonEmpty
    val alias = sql"SELECT place_id FROM place_aliases WHERE alias = $id".query[String].run().headOption
    val gone = sql"SELECT 1 FROM place_removals WHERE id = $id".query[Int].run().nonEmpty
    if taken then
      Left(
        Api.Error(
          Api.Error.Message(s"'${id.value}' is already the id of a place — choose another id"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if gone then
      Left(
        Api.Error(
          Api.Error.Message(s"'${id.value}' is the id of a place that was removed — choose another id"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else if alias.isDefined then
      Left(
        Api.Error(
          Api.Error.Message(s"'${id.value}' is an old id of '${alias.get}' and still resolves to it — choose another id"),
          Some(Api.Error.Code(Write.CodeInvalid))
        )
      )
    else
      val _ = upsertIn(List(in), Dataset.Local)
      sql"SELECT ${SqlLiteral(summaryCols)} FROM places p WHERE p.id = $id"
        .query[Place.Summary]
        .run()
        .headOption
        .toRight(Api.Error(Api.Error.Message(s"place '${id.value}' was not stored")))

  /** How many places the gazetteer holds — 0 on a fresh install (the first-run import, #720). */
  def count()(using DbCon): RowCount =
    RowCount(sql"SELECT count(*)::int FROM places".query[Int].run().head)

  def upsertFootprint(
      placeId: com.histmersion.domain.catalogue.Place.Id,
      role: FootprintRole,
      geojson: GeoJson,
      period: Option[Period],
      certainty: Option[Certainty],
      accuracyM: Option[AccuracyMetres],
      citekey: Option[Citekey]
  )(using DbCon): RowCount =
    val p = RangeSql.always(period)
    sql"""INSERT INTO place_footprints (place_id, role, geom, period, certainty, accuracy_m, citekey)
          VALUES ($placeId, $role,
                  ST_SetSRID(ST_GeomFromGeoJSON($geojson), 4326),
                  $p::int8range, $certainty, $accuracyM, $citekey)
          RETURNING id""".query[Int].run().head.pipe(RowCount(_))

  def upsertLink(link: Place.Link)(using DbCon): Unit =
    sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
          VALUES (${link.placeId}, ${link.scheme}, ${link.externalId}, ${link.relation})
          ON CONFLICT (place_id, scheme, external_id) DO UPDATE SET
            relation = EXCLUDED.relation""".update.run()

  def upsertType(pt: Place.Type)(using DbCon): Unit =
    val p = RangeSql.always(pt.period)
    sql"""INSERT INTO place_types (place_id, type_label, period, citekey)
          VALUES (${pt.placeId}, ${pt.typeLabel}, $p::int8range, ${pt.citekey})
          ON CONFLICT (place_id, type_label) DO UPDATE SET
            period = EXCLUDED.period, citekey = EXCLUDED.citekey""".update.run()
