package com.histmersion.db

import com.histmersion.core.*
import com.augustnagro.magnum.*

/** The interactive-latency rewrites (docs/research-interactive-performance.md) return EXACTLY what
  * the shapes they replaced returned, against a real Postgres (HISTMERSION_TEST_DB_URL); skipped
  * otherwise. Each test runs the new query and a REFERENCE — the old SQL, kept here verbatim, or the
  * rule computed the plain way — over the same database and compares the whole answers, so the other
  * suites' fixtures in a shared test database only make the comparison stronger. */
class InteractiveQueriesSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""

  // a region > polity > site chain far from every other fixture, plus look-alike labels for search
  private val places = List(
    PlaceInput(Place("zzi_land", "Zzia Land", PlaceKind.Region, Some(box(-130, -40, -120, -30))),
      List(PlaceName("Zzia Land"), PlaceName("Zzialand", "xx"))),
    PlaceInput(Place("zzi_realm", "Zzia Realm", PlaceKind.Polity, Some(box(-129, -39, -125, -35)), Some(Period(-1600, -1000))),
      List(PlaceName("Zzia Realm"), PlaceName("Old Zziarealm", "xx", Some(Period(-1600, -1300)))),
      List(PlaceRelation("zzi_realm", "zzi_land", PlaceRelation.PartOf, Some(Period(-1600, -1000))))),
    PlaceInput(Place("zzi_town", "Zzia Town", PlaceKind.Site, Some("""{"type":"Point","coordinates":[-128.0,-37.0]}""")),
      List(PlaceName("Zzia Town")),
      List(PlaceRelation("zzi_town", "zzi_realm"))),
    PlaceInput(Place("zzi_east", "Zzia East", PlaceKind.Region, Some(box(-122, -40, -120, -38))),
      List(PlaceName("Zzia East"))),
    // contains the query but does not START with it, and is only findable by a period-scoped name
    PlaceInput(Place("zzi_hidden", "Hidden Place", PlaceKind.Site),
      List(PlaceName("Hidden Place"), PlaceName("Zzia Hidden", "xx", Some(Period(-1500, -1200))))),
    PlaceInput(Place("zzi_inner", "The Zzia", PlaceKind.Site), List(PlaceName("The Zzia"))),
    PlaceInput(Place("zzi_exact", "Zzia", PlaceKind.Site), List(PlaceName("Zzia"))),
    // a present-day GeoNames/Wikidata place (made one in beforeAll): never in a search before modern-from
    PlaceInput(Place("zzi_wd_now", "Zzia Wikitown", PlaceKind.Site, Some("""{"type":"Point","coordinates":[-127.0,-36.0]}""")),
      List(PlaceName("Zzia Wikitown"), PlaceName("Wikizzia", "xx"))))

  private def source(ck: String, tags: List[String], fs: List[(String, Option[GeoPoint], Option[String], Period)]) =
    Source(
      citekey = ck, title = s"Zzi source $ck", authors = List("Zzi"), doi = None, year = Some(2011),
      venue = None, status = SourceStatus.Current, topics = Nil, regions = List("Zzia region"), tags = tags,
      period = None, summary = None, citedByCount = None, influentialCitations = None,
      provenance = None, calibreId = None,
      findings = fs.zipWithIndex.map { case ((claim, at, region, p), i) =>
        Finding(s"$ck#$i", ck, claim, region, at, Some(p), None, Nil, Some(Confidence.High))
      })

  override def beforeAll(): Unit =
    if testDb.isDefined then
      val _ = PlaceRepo.upsertPlaces(ds, places)
      val _ = connect(ds)(sql"""UPDATE places SET import_source = 'wikidata',
                          properties = coalesce(properties, '{}'::jsonb) || '{"modern":true}'::jsonb
                        WHERE id = 'zzi_wd_now'""".update.run())
      SourceRepo.upsert(ds, source("zzi_a", List("zzi-tag"), List(
        ("at the town", Some(GeoPoint(-128.0, -37.0)), Some("Zzia Town"), Period(-1400, -1350)),
        ("in the east", Some(GeoPoint(-121.0, -39.0)), None, Period(-900, -800)))), cfg.limits.chunkChars)
      SourceRepo.upsert(ds, source("zzi_b", Nil, List(
        ("by name only", None, Some("Zzialand"), Period(-1300, -1250)))), cfg.limits.chunkChars)
      SourceRepo.upsert(ds, source("zzi_c", List("zzi-tag"), List(
        ("far away", Some(GeoPoint(-150.0, 60.0)), Some("Elsewhere"), Period(-1400, -1300)))), cfg.limits.chunkChars)
      val _ = ArticleRepo.put(ds, Article(slug = "zzi_art_a", title = "Zzi article A", kind = "concept",
        body = "# A\n\nSee [[zzi_a]] and [[zzi_c]].", summary = None, tags = Nil, regions = Nil, period = None))
      val _ = ArticleRepo.put(ds, Article(slug = "zzi_art_b", title = "Zzi article B", kind = "concept",
        body = "# B\n\nSee [[zzi_b]].", summary = None, tags = Nil, regions = Nil, period = None))
      List(("zzi_ev_a", -1380L, Some("zzi_a")), ("zzi_ev_b", -1280L, Some("zzi_b")), ("zzi_ev_none", -1300L, None))
        .foreach((id, y, ck) => EventRepo.put(ds, id, EventPut(label = s"Zzi event $id",
          timeSpan = TimeSpan(Some(y), None, None, Some(y)), citekey = ck)))

  override def afterSuite(): Unit =
    if testDb.isDefined then
      List("zzi_art_a", "zzi_art_b").foreach(s => ArticleRepo.delete(ds, s))
      List("zzi_ev_a", "zzi_ev_b", "zzi_ev_none").foreach(e => EventRepo.delete(ds, e))

  // ── the filters a reader builds: place ids (one, two), id + name, name, + tag, ± a period ──────

  private val periods = List(None, Some(Period(-1500, -1200)), Some(Period(-1000, -700)))
  private val filters: List[Filter] =
    for
      places <- List(List("zzi_land"), List("zzi_realm"), List("zzi_town", "zzi_east"), List("zzi_land", "Zzialand"),
        List("Zzia"), List("zzi_land"))
      tags   <- List(Nil, List("zzi-tag"))
      period <- periods
    yield Filter(places = places, tags = tags, period = period, limit = 500)

  private def scope(f: Filter) = PlaceRepo.scopeFor(ds, f.places, f.period)

  /** The source predicate as it was: the place ids through a CORRELATED `EXISTS`. */
  private def correlated(sql: Sql): Sql =
    Sql(sql.text.replace(
      "s.citekey IN (SELECT f.citekey FROM findings f JOIN finding_places fp ON fp.finding_id = f.id" +
        " WHERE fp.place_id = ANY(?::text[]))",
      "EXISTS (SELECT 1 FROM findings f JOIN finding_places fp ON fp.finding_id = f.id" +
        " WHERE f.citekey = s.citekey AND fp.place_id = ANY(?::text[]))")
      .replace("f.id IN (SELECT fp.finding_id FROM finding_places fp WHERE fp.place_id = ANY(?::text[]))",
        "EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id AND fp.place_id = ANY(?::text[]))"),
      sql.params)

  private def citekeys(sql: Sql): Set[String] = connect(ds)(sql.frag.query[String].run().toSet)

  test("a place id selects the same sources and findings as the correlated EXISTS did"):
    filters.foreach { f =>
      val sc  = scope(f)
      val now = citekeys(FilterSql.sourceKeys(f, sc))
      assertEquals(now, citekeys(correlated(FilterSql.sourceKeys(f, sc))), f.toString)
      val feed = Sql.raw("SELECT f.id FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ") ++
        FilterSql.sourceWhere(f) ++ Sql.raw(" AND ") ++ FilterSql.findingWhere(f, scope = sc)
      assertEquals(citekeys(feed), citekeys(correlated(feed)), f.toString)
      assertEquals(SourceRepo.findingsFor(ds, f, sc).size, citekeys(feed).size, f.toString)
    }
    // the fixtures are actually exercised: the region reaches the town's finding through the hierarchy
    assert(citekeys(FilterSql.sourceKeys(Filter(places = List("zzi_land")), scope(Filter(places = List("zzi_land")))))
      .contains("zzi_a"))

  test("articles and events: exactly the items related to the source set, computed the plain way"):
    filters.foreach { f =>
      val sc   = scope(f)
      val keys = citekeys(FilterSql.sourceKeys(f.sourceSet, sc)).toList
      val wantArticles = connect(ds)(
        sql"""SELECT DISTINCT src_slug FROM article_links WHERE target_kind = 'source' AND target = ANY($keys::text[])"""
          .query[String].run().toSet)
      val got = ArticleRepo.list(ds, f, sc, None, None, 500, 0)
      assertEquals(got.articles.map(_.slug).toSet, wantArticles, f.toString)
      assertEquals(got.total, wantArticles.size, f.toString)
      val eventKeys = citekeys(FilterSql.sourceKeys(f.sourceSet.copy(period = None), sc)).toList
      val wantEvents = connect(ds)(
        sql"""SELECT e.id FROM events e WHERE e.citekey = ANY($eventKeys::text[])
              AND (${f.period.map(_.start)}::bigint IS NULL
                   OR e.outer_span && int8range(${f.period.map(_.start)}::bigint, ${f.period.map(_.end)}::bigint, '[]'))"""
          .query[String].run().toSet)
      val evs = EventRepo.list(ds, f, sc, None, None, None, None, 500, 0)
      assertEquals(evs.events.map(_.id).toSet, wantEvents, f.toString)
      assertEquals(evs.total, wantEvents.size, f.toString)
    }

  test("the hover card's four counts in one statement equal the four list totals"):
    List("zzi_land", "zzi_realm", "zzi_town", "zzi_east", "zzi_hidden").foreach { id =>
      val f  = Filter(places = List(id))
      val sc = scope(f)
      assertEquals(SourceRepo.selectionCounts(ds, f, sc), PlaceCounts(
        sources  = SourceRepo.count(ds, f, sc),
        findings = SourceRepo.findingsCount(ds, f, sc),
        articles = ArticleRepo.list(ds, f, sc, None, None, 1, 0).total,
        events   = EventRepo.list(ds, f, sc, None, None, None, None, 1, 0).total), id)
    }
    assertEquals(SourceRepo.selectionCounts(ds, Filter(places = List("zzi_land")), scope(Filter(places = List("zzi_land")))).sources, 2)

  // ── place search: the single statement, verbatim, as the reference ────────────────────────────
  // With the time rule (2026-10-09): the statement as the time-views branch wrote it — the window
  // CTE `tw` in front and `PlaceRepo.inTimeSql` where `p.period @> asOf` was — so the rank-first
  // rewrite is held to the rule, not to the plain period containment it replaced.

  private def referenceSearch(q: Option[String], asOf: Option[Long], limit: Int): Vector[PlaceHit] =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val pre  = text.map(FilterSql.startsWith).getOrElse("%")
    val exact = text.getOrElse("")
    val long  = text.filter(_.length >= 3)
    val short = text.filter(_.length < 3).map(Geocode.key)
    connect(ds):
      (Sql.raw("WITH ") ++ PlaceRepo.timeWindowCte(asOf, PlaceRepo.DefaultModernFrom) ++ sql""", cand AS MATERIALIZED (
              SELECT id FROM places WHERE ${long}::text IS NOT NULL AND label ILIKE $cont ESCAPE '\\'
              UNION
              SELECT place_id FROM place_names WHERE ${long}::text IS NOT NULL AND name ILIKE $cont ESCAPE '\\'
              UNION
              SELECT id FROM places WHERE ${short}::text IS NOT NULL AND histmersion_place_key(label) = ${short}::text
              UNION
              SELECT place_id FROM place_names WHERE ${short}::text IS NOT NULL AND histmersion_place_key(name) = ${short}::text)
            SELECT p.id, p.label, p.kind, ${SqlLiteral(PlaceRepo.startCol)}, upper(p.period) - 1, p.source,
                   coalesce(nm.names, ''), nm.matched, coalesce(fc.n, 0),
                   coalesce((p.properties->>'contested')::boolean, false), ${SqlLiteral(PlaceRepo.datingCol)}, p.import_source
            FROM places p
            LEFT JOIN LATERAL (
              SELECT string_agg(DISTINCT n.name, ', ' ORDER BY n.name) AS names,
                     (SELECT m.name FROM place_names m
                      WHERE m.place_id = p.id AND m.name ILIKE $cont ESCAPE '\\'
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
            WHERE (${text}::text IS NULL OR p.id IN (SELECT id FROM cand))
              AND (${text}::text IS NULL OR nm.matched IS NOT NULL OR p.label ILIKE $cont ESCAPE '\\'
                   OR ${short}::text IS NOT NULL)
              AND ${SqlLiteral(PlaceRepo.inTimeSql("p", asOf))}
            ORDER BY (lower(p.label) = lower($exact)) DESC,
                     (p.label ILIKE $pre ESCAPE '\\') DESC,
                     ${SqlLiteral(Geocoder.tierSql)},
                     fc.n DESC, length(p.label), p.id
            LIMIT ${limit.max(1)}""").query[PlaceHit].run()

  test("place search ranks and decorates exactly as the single statement did"):
    val queries = List("Zzia", "zzia", "Zzia ", "Zzia T", "Zziarealm", "Hidden", "zq", "Zz", "Z", "ZZIA LAND", "Zzi_",
      "Wikizz", "Zzia W")
    for
      q     <- queries
      asOf  <- List(None, Some(-1400L), Some(-1250L), Some(0L), Some(1600L))
      limit <- List(1, 2, 3, 5, 20)
    do assertEquals(PlaceRepo.search(ds, Some(q), asOf, limit), referenceSearch(Some(q), asOf, limit), s"q=$q asOf=$asOf limit=$limit")

  test("place search: the label-prefix fast path and the full path both reach the fixtures"):
    // "Zzia" has five label prefixes (Zzia, Zzia Land/Realm/Town/East): 3 come from the prefix pass alone
    assertEquals(PlaceRepo.search(ds, Some("Zzia"), None, 3).head.id, "zzi_exact")
    // the period-scoped name is the only way to "Hidden Place": present in its window, absent outside
    val inWindow = PlaceRepo.search(ds, Some("Zzia Hidden"), Some(-1300), 20)
    assertEquals(inWindow.map(_.id), Vector("zzi_hidden"))
    assertEquals(inWindow.head.matchedName, Some("Zzia Hidden"))
    assert(!PlaceRepo.search(ds, Some("Zzia Hidden"), Some(0), 20).exists(_.id == "zzi_hidden"))
    // a present-day import: by label and by name only from modern-from (the pre-modern passes skip it)
    List("Zzia Wiki", "Wikizz").foreach { q =>
      assert(!PlaceRepo.search(ds, Some(q), Some(-1400L), 20).exists(_.id == "zzi_wd_now"), q)
      assert(PlaceRepo.search(ds, Some(q), Some(1600L), 20).exists(_.id == "zzi_wd_now"), q)
    }

  // ── the map's base layers: the single statement, verbatim, as the reference ──────────────────
  // With the time rule (2026-10-09): the statement as the time-views branch wrote it — the window
  // CTE, `PlaceRepo.inTimeSql` in place of `p.period @> asOf`, the `basis` column and the ranking of
  // dated before findings, timeless and modern — so the split base layer and the tier-aware pairs are
  // held to the rule, not to the plain period containment it replaced. Since V090 the layer reads the
  // stored shape columns and draws an area's stored web outline; the fixtures are boxes and points,
  // which that simplification (and its 4 decimals) leaves as they are, so the reference stays verbatim.

  private def referenceGeoQuery(kind: Option[String], asOf: Option[Long], limit: Int): Sql =
    import Param.*
    import PlaceRepo.*
    val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val where = List(Some(Sql.raw("p.geom IS NOT NULL")),
      Some(Sql.of("p.label !~* ?", S(UninformativeLabel))),
      Some(Sql.raw(if asOf.isEmpty then s"NOT $ModernSql" else inTimeSql("p", asOf, k))),
      Some(Sql.raw(BaseLayerSql)),
      k.map(v => Sql.of("p.kind = ?", S(v))),
      asOf.map(_ => Sql.raw(s"NOT $UndatedImportSql")),
      // region-cleanup's rule: an undated polity known only by a point is on no map at a year
      asOf.map(_ => Sql.raw(UndatedPolityPointSql))).flatten
    val live = Sql.raw("WITH ") ++ timeWindowCte(asOf, DefaultModernFrom) ++ Sql.raw(
      ", live AS (SELECT p.id, p.kind, " +
        s"$sourceTierSql AS tier, ${normLabelSql("p.label")} AS norm, ST_Area(p.geom) AS area, " +
        s"p.geom::box2d AS bb, ${timeBasisSql("p")} AS basis FROM places p WHERE ") ++
      Sql(where.map(_.text).mkString(" AND "), where.flatMap(_.params)) ++ Sql.raw(")")
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
      s" SELECT l.id, p.label, l.kind, $startCol AS ps, upper(p.period) - 1 AS pe, $datingCol AS dating," +
        " l.basis," +
        " coalesce(ST_Dimension(p.geom) = 2, false) AS has_boundary," +
        " ST_AsGeoJSON(coalesce(p.geom_clipped, p.geom), 5) AS geojson FROM live l JOIN places p ON p.id = l.id" +
        " WHERE l.id NOT IN (SELECT id FROM shadowed)" +
        " ORDER BY l.area DESC, CASE l.basis WHEN 'dated' THEN 0 WHEN 'findings' THEN 1" +
        " WHEN 'timeless' THEN 2 ELSE 3 END, l.id") ++ Sql.of(" LIMIT ?", I(limit.max(1)))

  private def rows(q: Sql): Vector[String] =
    connect(ds)(q.frag.query[(String, String, String, Option[Long], Option[Long], String, String, Boolean, String)].run()
      .map(_.toString))

  test("the base layers draw exactly what the single statement drew"):
    // two overlapping polities in different tiers (one shadows the other), an imported site with a
    // finding (drawn) and one without (not drawn)
    val _ = PlaceRepo.upsertPlaces(ds, List(
      PlaceInput(Place("zzi_pol_ohm", "Zzi Kingdom", PlaceKind.Polity, Some(box(-140, -40, -135, -35)), Some(Period(-1500, -1000))),
        List(PlaceName("Zzi Kingdom"))),
      PlaceInput(Place("zzi_pol_cl", "Zzi Kingdom", PlaceKind.Polity, Some(box(-139.5, -39.5, -135, -35)), Some(Period(-1500, -1000))),
        List(PlaceName("Zzi Kingdom"))),
      PlaceInput(Place("zzi_imp_hit", "Zzi Imported Hit", PlaceKind.Site, Some("""{"type":"Point","coordinates":[-128.0,-37.0]}""")),
        List(PlaceName("Zzi Imported Hit"))),
      PlaceInput(Place("zzi_imp_miss", "Zzi Imported Miss", PlaceKind.Site, Some("""{"type":"Point","coordinates":[-139.0,-36.0]}""")),
        List(PlaceName("Zzi Imported Miss"))),
      // the time rule's bases among equal (point) areas: an undated curated site shown only by a
      // finding dated then, a physical feature at any time, a present-day place from modern-from
      PlaceInput(Place("zzi_und_site", "Zzi Undated Site", PlaceKind.Site, Some("""{"type":"Point","coordinates":[-121.0,-39.0]}""")),
        List(PlaceName("Zzi Undated Site"))),
      PlaceInput(Place("zzi_feat", "Zzi Feature", PlaceKind.Feature, Some("""{"type":"Point","coordinates":[-122.0,-39.0]}""")),
        List(PlaceName("Zzi Feature"))),
      PlaceInput(Place("zzi_mod", "Zzi Modern Town", PlaceKind.Site, Some("""{"type":"Point","coordinates":[-123.0,-39.0]}"""),
        properties = Some("""{"modern":true}""")), List(PlaceName("Zzi Modern Town"))),
      // region-cleanup: an undated polity known only by a point (never on a map at a year, even with a
      // finding dated then), a dated one (drawn as usual), and an undated people (drawn by its findings)
      PlaceInput(Place("zzi_und_pol", "Zzi Undated Province", PlaceKind.Polity, Some("""{"type":"Point","coordinates":[-124.0,-39.0]}""")),
        List(PlaceName("Zzi Undated Province"))),
      PlaceInput(Place("zzi_dat_pol", "Zzi Dated Province", PlaceKind.Polity, Some("""{"type":"Point","coordinates":[-125.0,-39.0]}"""),
        Some(Period(-1500, -1000))), List(PlaceName("Zzi Dated Province"))),
      PlaceInput(Place("zzi_people", "Zzi Tribe", PlaceKind.People, Some("""{"type":"Point","coordinates":[-126.0,-39.0]}""")),
        List(PlaceName("Zzi Tribe")))))
    connect(ds):
      sql"""UPDATE places SET properties = coalesce(properties, '{}'::jsonb) || '{"method":"ohm-import"}'::jsonb
            WHERE id = 'zzi_pol_ohm'""".update.run()
      sql"""UPDATE places SET import_source = 'wikidata', period = int8range(-2000, 2000, '[]')
            WHERE id IN ('zzi_imp_hit', 'zzi_imp_miss')""".update.run()
      sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zzi_a#0', 'zzi_imp_hit')
            ON CONFLICT DO NOTHING""".update.run()
      sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zzi_a#1', 'zzi_und_site')
            ON CONFLICT DO NOTHING""".update.run()
      sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zzi_a#1', 'zzi_und_pol'), ('zzi_a#1', 'zzi_people')
            ON CONFLICT DO NOTHING""".update.run()
    for
      kind <- List(Some("polity"), Some("site"), Some("region"), Some("feature"), Some("people"), None)
      asOf <- List(None, Some(-1200L), Some(-850L), Some(0L), Some(1600L))
      limit <- List(3, 400)
    do assertEquals(rows(PlaceRepo.geoJsonQuery(kind, asOf, limit)), rows(referenceGeoQuery(kind, asOf, limit)),
      s"kind=$kind asOf=$asOf limit=$limit")
    val sites = rows(PlaceRepo.geoJsonQuery(Some("site"), Some(-1200L), 100000)).mkString
    assert(sites.contains("zzi_imp_hit") && !sites.contains("zzi_imp_miss"), "an imported site is drawn only with a finding")
    val polities = rows(PlaceRepo.geoJsonQuery(Some("polity"), Some(-1200L), 100000)).mkString
    assert(polities.contains("zzi_pol_ohm") && !polities.contains("zzi_pol_cl"), "the lower tier shadows the same state")
    def siteIds(y: Long) = "\\(([a-z_]+),".r.findAllMatchIn(rows(PlaceRepo.geoJsonQuery(None, Some(y), 100000)).mkString("\n"))
      .map(_.group(1)).filter(_.startsWith("zzi_")).toSet
    assert(!siteIds(-1200L).contains("zzi_und_site") && siteIds(-850L).contains("zzi_und_site"), "an undated site by its findings")
    assert(siteIds(-1200L).contains("zzi_feat") && !siteIds(0L).contains("zzi_mod") && siteIds(1600L).contains("zzi_mod"),
      "a feature at any time, a present-day place from modern-from")
    def layer(kind: String, y: Option[Long]) = "\\(([a-z_]+),".r.findAllMatchIn(rows(PlaceRepo.geoJsonQuery(Some(kind), y, 100000))
      .mkString("\n")).map(_.group(1)).filter(_.startsWith("zzi_")).toSet
    assert(!layer("polity", Some(-850L)).contains("zzi_und_pol") && layer("polity", None).contains("zzi_und_pol"),
      "an undated polity point: across all time, never at a year — findings or not")
    assert(layer("polity", Some(-1200L)).contains("zzi_dat_pol"), "a dated polity point is drawn at its time")
    assert(layer("people", Some(-850L)).contains("zzi_people") && !layer("people", Some(-1200L)).contains("zzi_people"),
      "an undated people by its findings, as any undated place")
    assert(rows(PlaceRepo.geoJsonQuery(Some("people"), Some(-850L), 100000)).exists(r => r.contains("zzi_people") && r.contains(",findings,")),
      "drawn as dated by its findings")
