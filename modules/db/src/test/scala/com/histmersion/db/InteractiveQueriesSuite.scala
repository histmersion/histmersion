package com.histmersion.db

import com.histmersion.core.*
import com.augustnagro.magnum.*

/** The interactive-latency rewrites (docs/research-interactive-performance.md) return EXACTLY what
  * the shapes they replaced returned, against a real Postgres (HISTMERSION_TEST_DB_URL); skipped
  * otherwise. Each test runs the new query and a REFERENCE — the old SQL, kept here verbatim, or the
  * rule computed the plain way — over the same database and compares the whole answers, so the other
  * suites' fixtures in a shared test database only make the comparison stronger. */
class InteractiveQueriesSuite extends munit.FunSuite with Bounded:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = Db.dataSource(cfg.db.copy(url = testDb.get))
    Migrate.run(d); d

  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""

  // a region > polity > site chain far from every other fixture, plus look-alike labels for search
  private val places = List(
    PlaceInput(Place("zzq_land", "Zzqa Land", PlaceKind.Region, Some(box(150, -40, 160, -30))),
      List(PlaceName("Zzqa Land"), PlaceName("Zzqaland", "xx"))),
    PlaceInput(Place("zzq_realm", "Zzqa Realm", PlaceKind.Polity, Some(box(151, -39, 155, -35)), Some(Period(-1600, -1000))),
      List(PlaceName("Zzqa Realm"), PlaceName("Old Zzqarealm", "xx", Some(Period(-1600, -1300)))),
      List(PlaceRelation("zzq_realm", "zzq_land", PlaceRelation.PartOf, Some(Period(-1600, -1000))))),
    PlaceInput(Place("zzq_town", "Zzqa Town", PlaceKind.Site, Some("""{"type":"Point","coordinates":[152.0,-37.0]}""")),
      List(PlaceName("Zzqa Town")),
      List(PlaceRelation("zzq_town", "zzq_realm"))),
    PlaceInput(Place("zzq_east", "Zzqa East", PlaceKind.Region, Some(box(158, -40, 160, -38))),
      List(PlaceName("Zzqa East"))),
    // contains the query but does not START with it, and is only findable by a period-scoped name
    PlaceInput(Place("zzq_hidden", "Hidden Place", PlaceKind.Site),
      List(PlaceName("Hidden Place"), PlaceName("Zzqa Hidden", "xx", Some(Period(-1500, -1200))))),
    PlaceInput(Place("zzq_inner", "The Zzqa", PlaceKind.Site), List(PlaceName("The Zzqa"))),
    PlaceInput(Place("zzq_exact", "Zzqa", PlaceKind.Site), List(PlaceName("Zzqa"))))

  private def source(ck: String, tags: List[String], fs: List[(String, Option[GeoPoint], Option[String], Period)]) =
    Source(
      citekey = ck, title = s"Zzq source $ck", authors = List("Zzq"), doi = None, year = Some(2011),
      venue = None, status = SourceStatus.Current, topics = Nil, regions = List("Zzqa region"), tags = tags,
      period = None, summary = None, citedByCount = None, influentialCitations = None,
      provenance = None, calibreId = None,
      findings = fs.zipWithIndex.map { case ((claim, at, region, p), i) =>
        Finding(s"$ck#$i", ck, claim, region, at, Some(p), None, Nil, Some(Confidence.High))
      })

  override def beforeAll(): Unit =
    if testDb.isDefined then
      val _ = PlaceRepo.upsertPlaces(ds, places)
      SourceRepo.upsert(ds, source("zzq_a", List("zzq-tag"), List(
        ("at the town", Some(GeoPoint(152.0, -37.0)), Some("Zzqa Town"), Period(-1400, -1350)),
        ("in the east", Some(GeoPoint(159.0, -39.0)), None, Period(-900, -800)))), cfg.limits.chunkChars)
      SourceRepo.upsert(ds, source("zzq_b", Nil, List(
        ("by name only", None, Some("Zzqaland"), Period(-1300, -1250)))), cfg.limits.chunkChars)
      SourceRepo.upsert(ds, source("zzq_c", List("zzq-tag"), List(
        ("far away", Some(GeoPoint(-150.0, 60.0)), Some("Elsewhere"), Period(-1400, -1300)))), cfg.limits.chunkChars)
      val _ = ArticleRepo.put(ds, Article(slug = "zzq_art_a", title = "Zzq article A", kind = "concept",
        body = "# A\n\nSee [[zzq_a]] and [[zzq_c]].", summary = None, tags = Nil, regions = Nil, period = None))
      val _ = ArticleRepo.put(ds, Article(slug = "zzq_art_b", title = "Zzq article B", kind = "concept",
        body = "# B\n\nSee [[zzq_b]].", summary = None, tags = Nil, regions = Nil, period = None))
      List(("zzq_ev_a", -1380L, Some("zzq_a")), ("zzq_ev_b", -1280L, Some("zzq_b")), ("zzq_ev_none", -1300L, None))
        .foreach((id, y, ck) => EventRepo.put(ds, id, EventPut(label = s"Zzq event $id",
          timeSpan = TimeSpan(Some(y), None, None, Some(y)), citekey = ck)))

  override def afterAll(): Unit =
    if testDb.isDefined then
      List("zzq_art_a", "zzq_art_b").foreach(s => ArticleRepo.delete(ds, s))
      List("zzq_ev_a", "zzq_ev_b", "zzq_ev_none").foreach(e => EventRepo.delete(ds, e))

  // ── the filters a reader builds: place ids (one, two), id + name, name, + tag, ± a period ──────

  private val periods = List(None, Some(Period(-1500, -1200)), Some(Period(-1000, -700)))
  private val filters: List[Filter] =
    for
      places <- List(List("zzq_land"), List("zzq_realm"), List("zzq_town", "zzq_east"), List("zzq_land", "Zzqaland"),
        List("Zzqa"), List("zzq_land"))
      tags   <- List(Nil, List("zzq-tag"))
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
    assert(citekeys(FilterSql.sourceKeys(Filter(places = List("zzq_land")), scope(Filter(places = List("zzq_land")))))
      .contains("zzq_a"))

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
    List("zzq_land", "zzq_realm", "zzq_town", "zzq_east", "zzq_hidden").foreach { id =>
      val f  = Filter(places = List(id))
      val sc = scope(f)
      assertEquals(SourceRepo.selectionCounts(ds, f, sc), PlaceCounts(
        sources  = SourceRepo.count(ds, f, sc),
        findings = SourceRepo.findingsCount(ds, f, sc),
        articles = ArticleRepo.list(ds, f, sc, None, None, 1, 0).total,
        events   = EventRepo.list(ds, f, sc, None, None, None, None, 1, 0).total), id)
    }
    assertEquals(SourceRepo.selectionCounts(ds, Filter(places = List("zzq_land")), scope(Filter(places = List("zzq_land")))).sources, 2)

  // ── place search: the old statement, verbatim, as the reference ───────────────────────────────

  private def referenceSearch(q: Option[String], asOf: Option[Long], limit: Int): Vector[PlaceHit] =
    val text = q.map(_.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val pre  = text.map(FilterSql.startsWith).getOrElse("%")
    val exact = text.getOrElse("")
    val long  = text.filter(_.length >= 3)
    val short = text.filter(_.length < 3).map(Geocode.key)
    connect(ds):
      sql"""WITH cand AS MATERIALIZED (
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
              AND (${asOf}::bigint IS NULL OR p.period @> ${asOf}::bigint)
            ORDER BY (lower(p.label) = lower($exact)) DESC,
                     (p.label ILIKE $pre ESCAPE '\\') DESC,
                     ${SqlLiteral(Geocoder.tierSql)},
                     fc.n DESC, length(p.label), p.id
            LIMIT ${limit.max(1)}""".query[PlaceHit].run()

  test("place search ranks and decorates exactly as the single statement did"):
    val queries = List("Zzqa", "zzqa", "Zzqa ", "Zzqa T", "Zzqarealm", "Hidden", "zq", "Zz", "Z", "ZZQA LAND", "Zzq_")
    for
      q     <- queries
      asOf  <- List(None, Some(-1400L), Some(-1250L), Some(0L))
      limit <- List(1, 2, 3, 5, 20)
    do assertEquals(PlaceRepo.search(ds, Some(q), asOf, limit), referenceSearch(Some(q), asOf, limit), s"q=$q asOf=$asOf limit=$limit")

  test("place search: the label-prefix fast path and the full path both reach the fixtures"):
    // "Zzqa" has five label prefixes (Zzqa, Zzqa Land/Realm/Town/East): 3 come from the prefix pass alone
    assertEquals(PlaceRepo.search(ds, Some("Zzqa"), None, 3).head.id, "zzq_exact")
    // the period-scoped name is the only way to "Hidden Place": present in its window, absent outside
    val inWindow = PlaceRepo.search(ds, Some("Zzqa Hidden"), Some(-1300), 20)
    assertEquals(inWindow.map(_.id), Vector("zzq_hidden"))
    assertEquals(inWindow.head.matchedName, Some("Zzqa Hidden"))
    assert(!PlaceRepo.search(ds, Some("Zzqa Hidden"), Some(0), 20).exists(_.id == "zzq_hidden"))
