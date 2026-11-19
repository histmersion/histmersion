package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.findings.FindingFixtures
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures
import com.histmersion.domain.catalogue.places.given

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{GeoPoint, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.Place.Kind.Wire as KindWire
import com.augustnagro.magnum.*
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.{joined, literal, Migrate}
import com.histmersion.api.catalogue.{EventPut, Geocode, Period, Place}
import com.histmersion.api.library.{Filter, Finding, Source}
import com.histmersion.api.research.Article
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.domain.research.Slug

/** The interactive-latency rewrites (docs/research-interactive-performance.md) return EXACTLY what the shapes they
  * replaced returned, against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. Each test runs the new
  * query and a REFERENCE — the old SQL, kept here verbatim, or the rule computed the plain way — over the same database
  * and compares the whole answers, so the other suites' fixtures in a shared test database only make the comparison
  * stronger.
  */
class InteractiveQueriesSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  // the reference search reads its hits as the repository does (Magnum 2 derives a row codec only where asked)
  private given DbCodec[Place.Hit] = DbCodec.derived

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def box(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
    Place.Geometry(
      s"""{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""
    )

  // a region > polity > site chain far from every other fixture, plus look-alike labels for search
  private val places = List(
    Place.Input(
      Place(
        Place.Id("zzi_land"),
        Place.Label("Zzia Land"),
        Place.KindValue(Place.Kind.Region),
        Some(box(-130, -40, -120, -30))
      ),
      List(Place.Name(Place.Name.Value("Zzia Land")), Place.Name(Place.Name.Value("Zzialand"), Place.Name.Lang("xx")))
    ),
    Place.Input(
      Place(
        Place.Id("zzi_realm"),
        Place.Label("Zzia Realm"),
        Place.KindValue(Place.Kind.Polity),
        Some(box(-129, -39, -125, -35)),
        Some(Period(Year(-1600), Year(-1000)))
      ),
      List(
        Place.Name(Place.Name.Value("Zzia Realm")),
        Place.Name(Place.Name.Value("Old Zziarealm"), Place.Name.Lang("xx"), Some(Period(Year(-1600), Year(-1300))))
      ),
      List(
        Place.Relation(
          Place.Relation.ChildId("zzi_realm"),
          Place.Relation.ParentId("zzi_land"),
          Place.Relation.Kind(Place.Relation.PartOf),
          Some(Period(Year(-1600), Year(-1000)))
        )
      )
    ),
    Place.Input(
      Place(
        Place.Id("zzi_town"),
        Place.Label("Zzia Town"),
        Place.KindValue(Place.Kind.Site),
        Some(Place.Geometry("""{"type":"Point","coordinates":[-128.0,-37.0]}"""))
      ),
      List(Place.Name(Place.Name.Value("Zzia Town"))),
      List(Place.Relation(Place.Relation.ChildId("zzi_town"), Place.Relation.ParentId("zzi_realm")))
    ),
    Place.Input(
      Place(
        Place.Id("zzi_east"),
        Place.Label("Zzia East"),
        Place.KindValue(Place.Kind.Region),
        Some(box(-122, -40, -120, -38))
      ),
      List(Place.Name(Place.Name.Value("Zzia East")))
    ),
    // contains the query but does not START with it, and is only findable by a period-scoped name
    Place.Input(
      Place(Place.Id("zzi_hidden"), Place.Label("Hidden Place"), Place.KindValue(Place.Kind.Site)),
      List(
        Place.Name(Place.Name.Value("Hidden Place")),
        Place.Name(Place.Name.Value("Zzia Hidden"), Place.Name.Lang("xx"), Some(Period(Year(-1500), Year(-1200))))
      )
    ),
    Place.Input(
      Place(Place.Id("zzi_inner"), Place.Label("The Zzia"), Place.KindValue(Place.Kind.Site)),
      List(Place.Name(Place.Name.Value("The Zzia")))
    ),
    Place.Input(
      Place(Place.Id("zzi_exact"), Place.Label("Zzia"), Place.KindValue(Place.Kind.Site)),
      List(Place.Name(Place.Name.Value("Zzia")))
    ),
    // a present-day GeoNames/Wikidata place (made one in beforeAll): never in a search before modern-from
    Place.Input(
      Place(
        Place.Id("zzi_wd_now"),
        Place.Label("Zzia Wikitown"),
        Place.KindValue(Place.Kind.Site),
        Some(Place.Geometry("""{"type":"Point","coordinates":[-127.0,-36.0]}"""))
      ),
      List(
        Place.Name(Place.Name.Value("Zzia Wikitown")),
        Place.Name(Place.Name.Value("Wikizzia"), Place.Name.Lang("xx"))
      )
    )
  )

  private def source(
      ck: Citekey,
      tags: List[Source.Tags],
      fs: List[(Finding.Claim, Option[GeoPoint], Option[Finding.Region], Period)]
  ) =
    Source(
      citekey = ck,
      title = com.histmersion.api.library.Title(s"Zzi source $ck"),
      authors = (List("Zzi")).map(com.histmersion.api.library.AuthorName.apply),
      doi = None,
      year = (Some(2011)).map(Year.apply),
      venue = None,
      status = Source.Status.Current,
      topics = Nil,
      regions = List(Source.Regions("Zzia region")),
      tags = tags,
      period = None,
      summary = None,
      citedByCount = None,
      influentialCitations = None,
      provenance = None,
      calibreId = None,
      findings = fs.zipWithIndex.map { case ((claim, at, region, p), i) =>
        Finding(Finding.Id(s"$ck#$i"), ck, claim, region, at, Some(p), None, Nil, Some(Confidence.High))
      }
    )

  override def beforeAll(): Unit =
    if testDb.isDefined then
      val _ = PlaceFixtures.upsertPlaces(ds, places)
      val _ = connect(ds)(sql"""UPDATE places SET import_source = 'wikidata',
                          properties = coalesce(properties, '{}'::jsonb) || '{"modern":true}'::jsonb
                        WHERE id = 'zzi_wd_now'""".update.run())
      SourceFixtures.upsert(
        ds,
        source(
          Citekey("zzi_a"),
          List(Source.Tags("zzi-tag")),
          List(
            (
              Finding.Claim("at the town"),
              Some(GeoPoint(Longitude(-128.0), Latitude(-37.0))),
              Some(Finding.Region("Zzia Town")),
              Period(Year(-1400), Year(-1350))
            ),
            (
              Finding.Claim("in the east"),
              Some(GeoPoint(Longitude(-121.0), Latitude(-39.0))),
              None,
              Period(Year(-900), Year(-800))
            )
          )
        ),
        cfg.limits.chunkChars
      )
      SourceFixtures.upsert(
        ds,
        source(
          Citekey("zzi_b"),
          Nil,
          List(
            (Finding.Claim("by name only"), None, Some(Finding.Region("Zzialand")), Period(Year(-1300), Year(-1250)))
          )
        ),
        cfg.limits.chunkChars
      )
      SourceFixtures.upsert(
        ds,
        source(
          Citekey("zzi_c"),
          List(Source.Tags("zzi-tag")),
          List(
            (
              Finding.Claim("far away"),
              Some(GeoPoint(Longitude(-150.0), Latitude(60.0))),
              Some(Finding.Region("Elsewhere")),
              Period(Year(-1400), Year(-1300))
            )
          )
        ),
        cfg.limits.chunkChars
      )
      val _ = ArticleFixtures.put(
        ds,
        Article(
          slug = Article.Slug("zzi_art_a"),
          title = Article.Title("Zzi article A"),
          kind = Article.KindValue("concept"),
          body = Article.Body("# A\n\nSee [[zzi_a]] and [[zzi_c]]."),
          summary = None,
          tags = Nil,
          regions = Nil,
          period = None
        )
      )
      val _ = ArticleFixtures.put(
        ds,
        Article(
          slug = Article.Slug("zzi_art_b"),
          title = Article.Title("Zzi article B"),
          kind = Article.KindValue("concept"),
          body = Article.Body("# B\n\nSee [[zzi_b]]."),
          summary = None,
          tags = Nil,
          regions = Nil,
          period = None
        )
      )
      List(("zzi_ev_a", -1380L, Some("zzi_a")), ("zzi_ev_b", -1280L, Some("zzi_b")), ("zzi_ev_none", -1300L, None))
        .foreach((id, y, ck) =>
          EventFixtures.put(
            ds,
            BEventRow.Id(id),
            EventPut(
              label = EventSummary.Label(s"Zzi event $id"),
              timeSpan = TimeSpan(
                Some(Year(y)),
                None,
                None,
                Some(Year(y))
              ),
              citekey = ck.map(Citekey.apply)
            )
          )
        )

  override def afterSuite(): Unit =
    if testDb.isDefined then
      List("zzi_art_a", "zzi_art_b").foreach(s => ArticleFixtures.delete(ds, Slug.applyUnsafe(s)))
      List("zzi_ev_a", "zzi_ev_b", "zzi_ev_none").foreach(e => EventFixtures.delete(ds, BEventRow.Id(e)))

  // ── the filters a reader builds: place ids (one, two), id + name, name, + tag, ± a period ──────

  private val periods = List(None, Some(Period(Year(-1500), Year(-1200))), Some(Period(Year(-1000), Year(-700))))
  private val filters: List[Filter] =
    for
      places <- List(
        List(Filter.Places("zzi_land")),
        List(Filter.Places("zzi_realm")),
        List(Filter.Places("zzi_town"), Filter.Places("zzi_east")),
        List(Filter.Places("zzi_land"), Filter.Places("Zzialand")),
        List(Filter.Places("Zzia")),
        List(Filter.Places("zzi_land"))
      )
      tags <- List(Nil, List(Filter.Tags("zzi-tag")))
      period <- periods
    yield Filter(places = places, tags = tags, period = period, limit = Filter.Limit(500))

  private def scope(f: Filter) = PlaceFixtures.scopeFor(ds, f.places, f.period)

  /** The source predicate as it was: the place ids through a CORRELATED `EXISTS`. */
  private def correlated(sql: Frag): Frag =
    // the old text with the new one's parameters, bound by its own writer: the same values in the same places
    Frag(
      sql.sqlString
        .replace(
          "s.citekey IN (SELECT f.citekey FROM findings f JOIN finding_places fp ON fp.finding_id = f.id" +
            " WHERE fp.place_id = ANY(?::text[]))",
          "EXISTS (SELECT 1 FROM findings f JOIN finding_places fp ON fp.finding_id = f.id" +
            " WHERE f.citekey = s.citekey AND fp.place_id = ANY(?::text[]))"
        )
        .replace(
          "f.id IN (SELECT fp.finding_id FROM finding_places fp WHERE fp.place_id = ANY(?::text[]))",
          "EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id AND fp.place_id = ANY(?::text[]))"
        ),
      sql.params,
      sql.writer
    )

  private def citekeys(sql: Frag): Set[String] = connect(ds)(sql.query[String].run().toSet)

  test("a place id selects the same sources and findings as the correlated EXISTS did"):
    filters.foreach { f =>
      val sc = scope(f)
      val now = citekeys(FilterSql.sourceKeys(f, sc))
      assertEquals(now, citekeys(correlated(FilterSql.sourceKeys(f, sc))), f.toString)
      val feed =
        sql"SELECT f.id FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ${FilterSql.sourceWhere(f)} AND ${FilterSql.findingWhere(f, scope = sc)}"
      assertEquals(citekeys(feed), citekeys(correlated(feed)), f.toString)
      assertEquals(FindingFixtures.findingsFor(ds, f, sc).size, citekeys(feed).size, f.toString)
    }
    // the fixtures are actually exercised: the region reaches the town's finding through the hierarchy
    assert(
      citekeys(
        FilterSql.sourceKeys(
          Filter(places = List(Filter.Places("zzi_land"))),
          scope(Filter(places = List(Filter.Places("zzi_land"))))
        )
      )
        .contains("zzi_a")
    )

  test("articles and events: exactly the items related to the source set, computed the plain way"):
    filters.foreach { f =>
      val sc = scope(f)
      val keys = citekeys(FilterSql.sourceKeys(f.sourceSet, sc)).toList
      val wantArticles = connect(ds)(
        sql"""SELECT DISTINCT src_slug FROM article_links WHERE target_kind = 'source' AND target = ANY($keys::text[])"""
          .query[String]
          .run()
          .toSet
      )
      val got = ArticleFixtures.list(ds, f, sc, None, None, ArticleFixtures.Limit(500), Offset(0))
      assertEquals(got.articles.map(_.slug).toSet, wantArticles, f.toString)
      assertEquals((got.total.value: Int), wantArticles.size, f.toString)
      val eventKeys = citekeys(FilterSql.sourceKeys(f.sourceSet.copy(period = None), sc)).toList
      val wantEvents = connect(ds)(
        sql"""SELECT e.id FROM events e WHERE e.citekey = ANY($eventKeys::text[])
              AND (${f.period.map(_.start)}::bigint IS NULL
                   OR e.outer_span && int8range(${f.period.map(_.start)}::bigint, ${f.period.map(
            _.end
          )}::bigint, '[]'))"""
          .query[String]
          .run()
          .toSet
      )
      val evs = EventFixtures.list(ds, f, sc, None, None, None, None, PageSize(500), Offset(0))
      assertEquals(evs.events.map(_.id).toSet, wantEvents, f.toString)
      assertEquals((evs.total.value: Int), wantEvents.size, f.toString)
    }

  test("the hover card's four counts in one statement equal the four list totals"):
    List("zzi_land", "zzi_realm", "zzi_town", "zzi_east", "zzi_hidden").foreach { id =>
      val f = Filter(places = List(Filter.Places(id)))
      val sc = scope(f)
      assertEquals(
        SearchFixtures.selectionCounts(ds, f, sc),
        Place.Counts(
          sources = Place.Counts.Sources(SearchFixtures.count(ds, f, sc).value),
          findings = Place.Counts.Findings(FindingFixtures.findingsCount(ds, f, sc).value),
          articles = Place.Counts.Articles(
            ArticleFixtures.list(ds, f, sc, None, None, ArticleFixtures.Limit(1), Offset(0)).total.value
          ),
          events = Place.Counts.Events(
            EventFixtures
              .list(ds, f, sc, None, None, None, None, PageSize(1), Offset(0))
              .total
              .value
          )
        ),
        id
      )
    }
    assertEquals(
      SearchFixtures
        .selectionCounts(
          ds,
          Filter(places = List(Filter.Places("zzi_land"))),
          scope(Filter(places = List(Filter.Places("zzi_land"))))
        )
        .sources,
      2
    )

  // ── place search: the single statement, verbatim, as the reference ────────────────────────────
  // With the time rule (2026-10-09): the statement as the time-views branch wrote it — the window
  // CTE `tw` in front and `PlaceFixtures.inTimeSql` where `p.period @> asOf` was — so the rank-first
  // rewrite is held to the rule, not to the plain period containment it replaced.

  private def referenceSearch(
      q: Option[PlaceFixtures.SearchText],
      asOf: Option[DeepTime.SignedYear],
      limit: PlaceFixtures.Limit
  ): Vector[Place.Hit] =
    val text = q.map(_.value.trim).filter(_.nonEmpty)
    val cont = text.map(FilterSql.contains).getOrElse("%")
    val pre = text.map(FilterSql.startsWith).getOrElse("%")
    val exact = text.getOrElse("")
    val long = text.filter(_.length >= 3)
    val short = text.filter(_.length < 3).map(Geocode.key)
    connect(ds):
      (sql"""WITH ${PlaceFixtures.timeWindowCte(
          asOf,
          PlaceFixtures.DefaultModernFrom
        )}, cand AS MATERIALIZED (
              SELECT id FROM places WHERE $long::text IS NOT NULL AND label ILIKE $cont ESCAPE '\\'
              UNION
              SELECT place_id FROM place_names WHERE $long::text IS NOT NULL AND name ILIKE $cont ESCAPE '\\'
              UNION
              SELECT id FROM places WHERE $short::text IS NOT NULL AND histmersion_place_key(label) = $short::text
              UNION
              SELECT place_id FROM place_names WHERE $short::text IS NOT NULL AND histmersion_place_key(name) = $short::text)
            SELECT p.id, p.label, p.kind, ${PlaceFixtures.startCol}, upper(p.period) - 1, p.source,
                   coalesce(nm.names, ''), nm.matched, coalesce(fc.n, 0),
                   coalesce((p.properties->>'contested')::boolean, false), ${PlaceFixtures.datingCol}, p.import_source
            FROM places p
            LEFT JOIN LATERAL (
              SELECT string_agg(DISTINCT n.name, ', ' ORDER BY n.name) AS names,
                     (SELECT m.name FROM place_names m
                      WHERE m.place_id = p.id AND m.name ILIKE $cont ESCAPE '\\'
                        AND ($asOf::bigint IS NULL OR m.period @> $asOf::bigint)
                      ORDER BY (lower(m.name) = lower($exact)) DESC, length(m.name), m.name
                      LIMIT 1) AS matched
              FROM place_names n
              WHERE n.place_id = p.id
                AND ($asOf::bigint IS NULL OR n.period @> $asOf::bigint)
            ) nm ON true
            LEFT JOIN LATERAL (
              SELECT count(*)::int AS n FROM finding_places fp WHERE fp.place_id = p.id
            ) fc ON true
            WHERE ($text::text IS NULL OR p.id IN (SELECT id FROM cand))
              AND ($text::text IS NULL OR nm.matched IS NOT NULL OR p.label ILIKE $cont ESCAPE '\\'
                   OR $short::text IS NOT NULL)
              AND ${PlaceFixtures.inTimeSql(sql"p", asOf)}
            ORDER BY (lower(p.label) = lower($exact)) DESC,
                     (p.label ILIKE $pre ESCAPE '\\') DESC,
                     ${PlaceFixtures.tierSql},
                     fc.n DESC, length(p.label), p.id
            LIMIT ${limit.value.max(1)}""").query[Place.Hit].run()

  test("place search ranks and decorates exactly as the single statement did"):
    val queries = List(
      "Zzia",
      "zzia",
      "Zzia ",
      "Zzia T",
      "Zziarealm",
      "Hidden",
      "zq",
      "Zz",
      "Z",
      "ZZIA LAND",
      "Zzi_",
      "Wikizz",
      "Zzia W"
    )
    for
      q <- queries
      asOf <- List(
        None,
        Some(DeepTime.SignedYear(-1400L)),
        Some(DeepTime.SignedYear(-1250L)),
        Some(DeepTime.SignedYear(0L)),
        Some(DeepTime.SignedYear(1600L))
      )
      limit <- List(1, 2, 3, 5, 20).map(PlaceFixtures.Limit.apply)
    do
      assertEquals(
        PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText(q)), asOf, limit),
        referenceSearch(Some(PlaceFixtures.SearchText(q)), asOf, limit),
        s"q=$q asOf=$asOf limit=$limit"
      )

  test("place search: the label-prefix fast path and the full path both reach the fixtures"):
    // "Zzia" has five label prefixes (Zzia, Zzia Land/Realm/Town/East): 3 come from the prefix pass alone
    assertEquals(
      PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("Zzia")), None, PlaceFixtures.Limit(3)).head.id,
      "zzi_exact"
    )
    // the period-scoped name is the only way to "Hidden Place": present in its window, absent outside
    val inWindow = PlaceFixtures.search(
      ds,
      Some(PlaceFixtures.SearchText("Zzia Hidden")),
      Some(DeepTime.SignedYear(-1300L)),
      PlaceFixtures.Limit(20)
    )
    assertEquals(inWindow.map(_.id), Vector("zzi_hidden"))
    assertEquals(inWindow.head.matchedName, Some("Zzia Hidden"))
    assert(
      !PlaceFixtures
        .search(
          ds,
          Some(PlaceFixtures.SearchText("Zzia Hidden")),
          Some(DeepTime.SignedYear(0L)),
          PlaceFixtures.Limit(20)
        )
        .exists(_.id.value == "zzi_hidden")
    )
    // a present-day import: by label and by name only from modern-from (the pre-modern passes skip it)
    List("Zzia Wiki", "Wikizz").foreach { q =>
      assert(
        !PlaceFixtures
          .search(ds, Some(PlaceFixtures.SearchText(q)), Some(DeepTime.SignedYear(-1400L)), PlaceFixtures.Limit(20))
          .exists(_.id.value == "zzi_wd_now"),
        q
      )
      assert(
        PlaceFixtures
          .search(ds, Some(PlaceFixtures.SearchText(q)), Some(DeepTime.SignedYear(1600L)), PlaceFixtures.Limit(20))
          .exists(_.id.value == "zzi_wd_now"),
        q
      )
    }

  // ── the map's base layers: the single statement, verbatim, as the reference ──────────────────
  // With the time rule (2026-10-09): the statement as the time-views branch wrote it — the window
  // CTE, `PlaceFixtures.inTimeSql` in place of `p.period @> asOf`, the `basis` column and the ranking of
  // dated before findings, timeless and modern — so the split base layer and the tier-aware pairs are
  // held to the rule, not to the plain period containment it replaced. Since V090 the layer reads the
  // stored shape columns and draws an area's stored web outline; the fixtures are boxes and points,
  // which that simplification (and its 4 decimals) leaves as they are, so the reference stays verbatim.

  private def referenceGeoQuery(
      kind: Option[KindWire],
      asOf: Option[DeepTime.SignedYear],
      limit: PlaceFixtures.Limit
  ): Frag =
    import PlaceFixtures.*
    val k = kind.map(v => KindWire(v.value.trim.toLowerCase)).filter(_.value.nonEmpty)
    val where = List(
      Some(sql"p.geom IS NOT NULL"),
      Some(sql"p.label !~* $UninformativeLabel"),
      Some(if asOf.isEmpty then sql"NOT $ModernSql" else sql"${inTimeSql(sql"p", asOf, k)}"),
      Some(sql"$BaseLayerSql"),
      k.map(v => sql"p.kind = $v"),
      asOf.map(_ => sql"NOT $UndatedImportSql"),
      // region-cleanup's rule: an undated polity known only by a point is on no map at a year
      asOf.map(_ => sql"$UndatedPolityPointSql")
    ).flatten
    val live =
      sql"WITH ${timeWindowCte(asOf, DefaultModernFrom)}, live AS (SELECT p.id, p.kind, $sourceTierSql AS tier, ${normLabel(sql"p.label")} AS norm, ST_Area(p.geom) AS area, p.geom::box2d AS bb, ${timeBasisSql(sql"p")} AS basis FROM places p WHERE ${where.joined(sql" AND ")})"
    val shadowed =
      if asOf.isEmpty then sql", shadowed AS (SELECT NULL::text AS id WHERE false)"
      else
        sql", shadowed AS (SELECT DISTINCT lo.id FROM live lo JOIN live hi ON hi.tier < lo.tier AND hi.kind = lo.kind AND hi.bb && lo.bb JOIN places gh ON gh.id = hi.id JOIN places gl ON gl.id = lo.id WHERE hi.norm = lo.norm OR (ST_Intersects(gh.geom, gl.geom) AND ST_Area(ST_Intersection(ST_MakeValid(gh.geom), ST_MakeValid(gl.geom))) > ${SameStateOverlap.literal} * least(hi.area, lo.area)))"
    sql"$live$shadowed SELECT l.id, p.label, l.kind, $startCol AS ps, upper(p.period) - 1 AS pe, $datingCol AS dating, l.basis, coalesce(ST_Dimension(p.geom) = 2, false) AS has_boundary, ST_AsGeoJSON(coalesce(p.geom_clipped, p.geom), 5) AS geojson FROM live l JOIN places p ON p.id = l.id WHERE l.id NOT IN (SELECT id FROM shadowed) ORDER BY l.area DESC, CASE l.basis WHEN 'dated' THEN 0 WHEN 'findings' THEN 1 WHEN 'timeless' THEN 2 ELSE 3 END, l.id LIMIT ${limit.value.max(1)}"

  private def rows(q: Frag): Vector[String] =
    connect(ds)(
      q
        .query[(String, String, String, Option[Long], Option[Long], String, String, Boolean, String)]
        .run()
        .map(_.toString)
    )

  test("the base layers draw exactly what the single statement drew"):
    // two overlapping polities in different tiers (one shadows the other), an imported site with a
    // finding (drawn) and one without (not drawn)
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(
        Place.Input(
          Place(
            Place.Id("zzi_pol_ohm"),
            Place.Label("Zzi Kingdom"),
            Place.KindValue(Place.Kind.Polity),
            Some(box(-140, -40, -135, -35)),
            Some(Period(Year(-1500), Year(-1000)))
          ),
          List(Place.Name(Place.Name.Value("Zzi Kingdom")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_pol_cl"),
            Place.Label("Zzi Kingdom"),
            Place.KindValue(Place.Kind.Polity),
            Some(box(-139.5, -39.5, -135, -35)),
            Some(Period(Year(-1500), Year(-1000)))
          ),
          List(Place.Name(Place.Name.Value("Zzi Kingdom")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_imp_hit"),
            Place.Label("Zzi Imported Hit"),
            Place.KindValue(Place.Kind.Site),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-128.0,-37.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Imported Hit")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_imp_miss"),
            Place.Label("Zzi Imported Miss"),
            Place.KindValue(Place.Kind.Site),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-139.0,-36.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Imported Miss")))
        ),
        // the time rule's bases among equal (point) areas: an undated curated site shown only by a
        // finding dated then, a physical feature at any time, a present-day place from modern-from
        Place.Input(
          Place(
            Place.Id("zzi_und_site"),
            Place.Label("Zzi Undated Site"),
            Place.KindValue(Place.Kind.Site),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-121.0,-39.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Undated Site")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_feat"),
            Place.Label("Zzi Feature"),
            Place.KindValue(Place.Kind.Feature),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-122.0,-39.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Feature")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_mod"),
            Place.Label("Zzi Modern Town"),
            Place.KindValue(Place.Kind.Site),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-123.0,-39.0]}""")),
            properties = Some(Place.Properties("""{"modern":true}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Modern Town")))
        ),
        // region-cleanup: an undated polity known only by a point (never on a map at a year, even with a
        // finding dated then), a dated one (drawn as usual), and an undated people (drawn by its findings)
        Place.Input(
          Place(
            Place.Id("zzi_und_pol"),
            Place.Label("Zzi Undated Province"),
            Place.KindValue(Place.Kind.Polity),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-124.0,-39.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Undated Province")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_dat_pol"),
            Place.Label("Zzi Dated Province"),
            Place.KindValue(Place.Kind.Polity),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-125.0,-39.0]}""")),
            Some(Period(Year(-1500), Year(-1000)))
          ),
          List(Place.Name(Place.Name.Value("Zzi Dated Province")))
        ),
        Place.Input(
          Place(
            Place.Id("zzi_people"),
            Place.Label("Zzi Tribe"),
            Place.KindValue(Place.Kind.People),
            Some(Place.Geometry("""{"type":"Point","coordinates":[-126.0,-39.0]}"""))
          ),
          List(Place.Name(Place.Name.Value("Zzi Tribe")))
        )
      )
    )
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
      kind <- List(
        Some(KindWire("polity")),
        Some(KindWire("site")),
        Some(KindWire("region")),
        Some(KindWire("feature")),
        Some(KindWire("people")),
        None
      )
      asOf <- List(
        None,
        Some(DeepTime.SignedYear(-1200L)),
        Some(DeepTime.SignedYear(-850L)),
        Some(DeepTime.SignedYear(0L)),
        Some(DeepTime.SignedYear(1600L))
      )
      limit <- List(3, 400).map(PlaceFixtures.Limit.apply)
    do
      assertEquals(
        rows(PlaceFixtures.geoJsonQuery(kind, asOf, limit)),
        rows(referenceGeoQuery(kind, asOf, limit)),
        s"kind=$kind asOf=$asOf limit=$limit"
      )
    val sites = rows(
      PlaceFixtures.geoJsonQuery(Some(KindWire("site")), Some(DeepTime.SignedYear(-1200L)), PlaceFixtures.Limit(100000))
    ).mkString
    assert(
      sites.contains("zzi_imp_hit") && !sites.contains("zzi_imp_miss"),
      "an imported site is drawn only with a finding"
    )
    val polities = rows(
      PlaceFixtures.geoJsonQuery(
        Some(KindWire("polity")),
        Some(DeepTime.SignedYear(-1200L)),
        PlaceFixtures.Limit(100000)
      )
    ).mkString
    assert(
      polities.contains("zzi_pol_ohm") && !polities.contains("zzi_pol_cl"),
      "the lower tier shadows the same state"
    )
    def siteIds(y: Long) = "\\(([a-z_]+),".r
      .findAllMatchIn(
        rows(PlaceFixtures.geoJsonQuery(None, Some(DeepTime.SignedYear(y)), PlaceFixtures.Limit(100000))).mkString("\n")
      )
      .map(_.group(1))
      .filter(_.startsWith("zzi_"))
      .toSet
    assert(
      !siteIds(-1200L).contains("zzi_und_site") && siteIds(-850L).contains("zzi_und_site"),
      "an undated site by its findings"
    )
    assert(
      siteIds(-1200L).contains("zzi_feat") && !siteIds(0L).contains("zzi_mod") && siteIds(1600L).contains("zzi_mod"),
      "a feature at any time, a present-day place from modern-from"
    )
    def layer(kind: String, y: Option[Long]) = "\\(([a-z_]+),".r
      .findAllMatchIn(
        rows(
          PlaceFixtures
            .geoJsonQuery(Some(KindWire(kind)), y.map(DeepTime.SignedYear.apply), PlaceFixtures.Limit(100000))
        )
          .mkString("\n")
      )
      .map(_.group(1))
      .filter(_.startsWith("zzi_"))
      .toSet
    assert(
      !layer("polity", Some(-850L)).contains("zzi_und_pol") && layer("polity", None).contains("zzi_und_pol"),
      "an undated polity point: across all time, never at a year — findings or not"
    )
    assert(layer("polity", Some(-1200L)).contains("zzi_dat_pol"), "a dated polity point is drawn at its time")
    assert(
      layer("people", Some(-850L)).contains("zzi_people") && !layer("people", Some(-1200L)).contains("zzi_people"),
      "an undated people by its findings, as any undated place"
    )
    assert(
      rows(
        PlaceFixtures.geoJsonQuery(
          Some(KindWire("people")),
          Some(DeepTime.SignedYear(-850L)),
          PlaceFixtures.Limit(100000)
        )
      ).exists(r => r.contains("zzi_people") && r.contains(",findings,")),
      "drawn as dated by its findings"
    )
