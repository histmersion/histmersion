package com.histmersion.db

import com.histmersion.domain.library.search.FilterSql
import com.histmersion.domain.library.search.Search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Param.*
import com.histmersion.util.database.Sql
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.domain.library.Source
import com.histmersion.api.library.Facet
import com.histmersion.api.library.Finding

/** `Filter → SQL` is a pure function (plan §1.0), so it is tested WITHOUT a database: the SQL text and the bound
  * parameters are asserted directly.
  */
class FilterSqlSuite extends munit.FunSuite:

  private def where(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql = FilterSql.where(f, scope)

  test("empty filter → TRUE, no parameters"):
    assertEquals(where(Filter()), Sql("TRUE", Nil))
    assertEquals(FilterSql.countQuery(Filter()).text, "SELECT count(*)::int FROM sources s WHERE TRUE")

  test("q is the FTS predicate: the title/summary vector OR any body chunk (plan §1.4)"):
    val s = where(Filter(q = Some("collapse")))
    assertEquals(s, Search.ftsPredicate("collapse"))
    assert(s.text.contains("s.tsv @@ websearch_to_tsquery('english', ?)"), s.text)
    assert(s.text.contains("FROM source_chunks fts_p WHERE fts_p.citekey = s.citekey"), s.text)
    assertEquals(s.params, List(S("collapse"), S("collapse"))) // one per tsquery occurrence

  test("a blank q is not a filter"):
    assertEquals(where(Filter(q = Some("   "))), Sql("TRUE", Nil))

  test("tags: AND semantics, each over sources.tags ∪ findings.tags"):
    val s = where(Filter(tags = List("sea-peoples", "steppe")))
    val one = "(EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower(?))" +
      " OR EXISTS (SELECT 1 FROM findings f, unnest(f.tags) x" +
      " WHERE f.citekey = s.citekey AND lower(x) = lower(?)))"
    assertEquals(s.text, s"$one AND $one") // one required predicate per tag → AND semantics
    assert(s.text.contains("FROM findings f, unnest(f.tags) x"), s.text)
    assertEquals(s.params, List(S("sea-peoples"), S("sea-peoples"), S("steppe"), S("steppe")))

  test("authors: any-of, ILIKE substring"):
    val s = where(Filter(authors = List("cline", "anthony")))
    assertEquals(
      s.text,
      "(EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ? ESCAPE '\\')" +
        " OR EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ? ESCAPE '\\'))"
    )
    assertEquals(s.params, List(S("%cline%"), S("%anthony%")))

  test("a single author is not parenthesised (any-of of one)"):
    assert(!where(Filter(authors = List("cline"))).text.startsWith("("))

  test("places: any-of over sources.regions ∪ findings.region_name ∪ place_names"):
    val s = where(Filter(places = List("Anatolia")))
    assert(s.text.contains("unnest(s.regions)"), s.text)
    assert(s.text.contains("f.region_name ILIKE ?"), s.text)
    assert(s.text.contains("pn.name ILIKE ?"), s.text)
    assertEquals(s.params, List(S("%Anatolia%"), S("%Anatolia%"), S("%Anatolia%")))

  test(
    "places: a resolved gazetteer id matches through finding_places alone, never its id as a name (hover counts took 3-20 s)"
  ):
    val scope = PlaceScope(Map("pleiades_570577" -> List("pleiades_570577", "pleiades_1")))
    val s = where(Filter(places = List("pleiades_570577")), scope)
    assert(s.text.contains("fp.place_id = ANY(?::text[])"), s.text)
    assert(!s.text.contains("ILIKE"), s.text)
    assertEquals(s.params, List(A(List("pleiades_570577", "pleiades_1"))))

  test("a resolved place id is an UNCORRELATED semi-join with its scope bound as ONE array"):
    val scope = PlaceScope(Map("zz_region" -> List("zz_region", "zz_child", "zz_grandchild")))
    val s = FilterSql.where(Filter(places = List("zz_region")), scope)
    // computed once (a hashed sub-plan), never re-run per source or per article link
    assert(
      s.text.contains(
        "s.citekey IN (SELECT f.citekey FROM findings f JOIN finding_places fp" +
          " ON fp.finding_id = f.id WHERE fp.place_id = ANY(?::text[]))"
      ),
      s.text
    )
    assert(!s.text.contains("f.citekey = s.citekey AND fp.place_id"), s.text)
    assertEquals(s.params.head, A(List("zz_region", "zz_child", "zz_grandchild")))
    // a 451-place scope is still ONE parameter (a generic plan of 451 `?`s went bad; JDBC caps at 32 767)
    val big = PlaceScope(Map("zz_big" -> (1 to 451).map(i => s"zz_$i").toList))
    assertEquals(FilterSql.where(Filter(places = List("zz_big")), big).params.count(_.isInstanceOf[A]), 1)
    val f = FilterSql.findingPlaceWhere(Filter(places = List("zz_region")), scope = scope)
    assert(
      f.text.contains("f.id IN (SELECT fp.finding_id FROM finding_places fp WHERE fp.place_id = ANY(?::text[]))"),
      f.text
    )

  test("selectionCountsQuery: the four lists' own predicates, as one statement"):
    val f = Filter(tags = List("x"), period = Some(Period(-1300, -1100)))
    val s = FilterSql.selectionCountsQuery(f)
    assertEquals(
      s.text,
      "SELECT (" + FilterSql.countQuery(f).text + "), (" + FilterSql.findingsCountQuery(f).text +
        "), (SELECT count(*)::int FROM articles a WHERE " + FilterSql.articleWhere(f).text +
        "), (SELECT count(*)::int FROM events e WHERE " + FilterSql.eventWhere(f).text + ")"
    )
    assertEquals(
      s.params,
      FilterSql.countQuery(f).params ++ FilterSql.findingsCountQuery(f).params ++
        FilterSql.articleWhere(f).params ++ FilterSql.eventWhere(f).params
    )

  test("topics: AND semantics, sources only"):
    val s = where(Filter(topics = List("indo-european")))
    assertEquals(s.text, "EXISTS (SELECT 1 FROM unnest(s.topics) x WHERE lower(x) = lower(?))")
    assertEquals(s.params, List(S("indo-european")))

  test("status: any-of, lower-cased IN list"):
    val s = where(Filter(status = List("Current", "FOUNDATIONAL")))
    assertEquals(s.text, "lower(s.status) IN (?, ?)")
    assertEquals(s.params, List(S("current"), S("foundational")))

  test("publication years: inclusive BETWEEN"):
    val s = where(Filter(years = Some(Period(2000, 2020))))
    assertEquals(s.text, "(s.year IS NOT NULL AND s.year BETWEEN ? AND ?)")
    assertEquals(s.params, List(L(2000), L(2020)))

  test("deep time: inclusive int8range overlap on the source OR any of its findings"):
    val s = where(Filter(period = Some(Period(-1300, -1100))))
    assertEquals(
      s.text,
      "(s.period && int8range(?::bigint, ?::bigint, '[]')" +
        " OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
        " AND f.period && int8range(?::bigint, ?::bigint, '[]')))"
    )
    assertEquals(s.params, List(L(-1300), L(-1100), L(-1300), L(-1100)))

  test("bbox: one ST_MakeEnvelope, matched through the source's findings"):
    val s = where(Filter(bbox = Some(BBox(25.0, 30.0, 45.0, 42.0))))
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL" +
        " AND f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326))"
    )
    assertEquals(s.params, List(D(25.0), D(30.0), D(45.0), D(42.0)))

  test("bbox across the antimeridian (minLon > maxLon) splits into two OR'ed envelopes"):
    val s = where(Filter(bbox = Some(BBox(170.0, -20.0, -170.0, -5.0))))
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL" +
        " AND (f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)" +
        " OR f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)))"
    )
    assertEquals(s.params, List(D(170.0), D(-20.0), D(180.0), D(-5.0), D(-180.0), D(-20.0), D(-170.0), D(-5.0)))

  test("predicates combine with AND, in Filter field order"):
    val s = where(Filter(q = Some("x"), tags = List("t"), status = List("current")))
    val order = List(s.text.indexOf("s.tsv @@"), s.text.indexOf("unnest(s.tags)"), s.text.indexOf("lower(s.status)"))
    assertEquals(order, order.sorted.filter(_ >= 0))
    assertEquals(s.params, List(S("x"), S("x"), S("t"), S("t"), S("current")))

  test("LIKE wildcards in user input are escaped, not interpreted"):
    assertEquals(FilterSql.escapeLike("100%_a\\b"), "100\\%\\_a\\\\b")
    assertEquals(FilterSql.contains("a%b"), "%a\\%b%")
    assertEquals(FilterSql.startsWith("a_"), "a\\_%")

  test("sort orders are deterministic (citekey tiebreak) and cover every case"):
    Sort.values.foreach(s => assert(FilterSql.orderBy(s).endsWith(", s.citekey"), s.toString))
    assertEquals(FilterSql.orderBy(Sort.Cited), "ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey")
    assertEquals(FilterSql.orderBy(Sort.PeriodStart), "ORDER BY lower(s.period) ASC NULLS LAST, s.citekey")
    assertEquals(FilterSql.orderBy(Sort.Year), "ORDER BY s.year DESC NULLS LAST, s.citekey")
    assertEquals(FilterSql.orderBy(Sort.Oldest), "ORDER BY s.year ASC NULLS LAST, s.citekey")
    assertEquals(FilterSql.orderBy(Sort.Title), "ORDER BY lower(s.title) ASC, s.citekey")

  test("sourcesQuery with a text query uses a CTE for ranking, defers snippets"):
    val s = FilterSql.sourcesQuery(Filter(q = Some("collapse"), limit = 5, offset = 10), 30)
    // CTE scans source_chunks once for best chunk rank per citekey
    assert(s.text.startsWith("WITH fts_m AS MATERIALIZED"), s.text)
    assert(s.text.contains("best_chunks AS (SELECT DISTINCT ON (citekey)"), s.text)
    // each matching chunk is ranked once, not once per sort-key arm
    val (ranking, rest) = s.text.splitAt(s.text.indexOf("best_chunks AS"))
    assertEquals("ts_rank_cd\\(".r.findAllIn(ranking).length, 1, ranking)
    val bestChunks = rest.take(rest.indexOf(") SELECT"))
    assert(!bestChunks.contains("ts_rank_cd"), bestChunks)
    assert(s.text.contains("DISTINCT ON (citekey)"), s.text)
    // body_hit comes from the CTE join, snippets are NULL (deferred to snippetsQuery)
    assert(s.text.contains("(bc.citekey IS NOT NULL) AS body_hit"), s.text)
    assert(s.text.contains("NULL::text AS snippet"), s.text)
    // ranking uses the CTE's precomputed rank, not a correlated subquery
    assert(s.text.contains("LEFT JOIN best_chunks bc ON bc.citekey = s.citekey"), s.text)
    assert(s.text.contains("ORDER BY (ts_rank_cd(s.tsv"), s.text)
    assert(s.text.contains("coalesce(bc.rank, 0)"), s.text)
    assert(s.text.endsWith("LIMIT ? OFFSET ?"), s.text)
    // no LATERAL, no ts_headline, no MaxFragments option string
    assert(!s.text.contains("LATERAL"), s.text)
    assert(!s.text.contains("ts_headline"), s.text)
    // CTE params (2 tsquery occurrences: match + the single rank) + WHERE (2) + ORDER BY rank (1)
    assertEquals(s.params.count(_ == S("collapse")), 5)
    assertEquals(s.params.takeRight(2), List(I(5), I(10)))

  test("sourcesQuery without q has no CTE, a constant FALSE body-hit column and no extra param"):
    val s = FilterSql.sourcesQuery(Filter(limit = 20), 30)
    assert(s.text.contains("SELECT s.citekey"), s.text)
    assert(s.text.contains("FALSE AS body_hit, NULL::text, NULL::text FROM sources s WHERE"), s.text)
    assert(!s.text.contains("LATERAL"), s.text)
    assert(!s.text.contains("WITH best_chunks"), s.text)
    assert(s.text.contains("ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey"), s.text)
    assertEquals(s.params, List(I(20), I(0)))

  test("negative limit/offset never reach SQL as negatives"):
    assertEquals(FilterSql.sourcesQuery(Filter(limit = -3, offset = -9), 30).params, List(I(0), I(0)))

  test("snippetsQuery computes ts_headline for specific citekeys via two-level query"):
    val s = FilterSql.snippetsQuery("collapse", 30, List("a", "b"))
    // inner subquery: DISTINCT ON to find best chunk per citekey (no ts_headline)
    assert(s.text.contains("DISTINCT ON (citekey) citekey, ord"), s.text)
    assert(s.text.contains("citekey IN (?, ?)"), s.text)
    // outer query: ts_headline only for those N rows
    assert(s.text.contains("ts_headline('english', sc.body,"), s.text)
    assert(s.text.contains("INNER JOIN ("), s.text)
    assert(s.text.contains("best.citekey = sc.citekey AND best.ord = sc.ord"), s.text)
    assert(
      s.params.contains(S("MaxFragments=1, MaxWords=30, MinWords=8, StartSel=**, StopSel=**, HighlightAll=false")),
      s.params.toString
    )
    // inner: tsv match + two rank occurrences (3 tsquery); outer: one tsquery for ts_headline
    assertEquals(s.params.count(_ == S("collapse")), 4)

  test("snippetsQuery with empty citekeys returns a no-op"):
    val s = FilterSql.snippetsQuery("x", 30, Nil)
    assert(s.text.contains("WHERE FALSE"), s.text)
    assertEquals(s.params, Nil)

  test("orderBySql: relevance ranks by FTS when there is a query, by citations when there is not"):
    // legacy path (useCte = false): correlated subquery
    val ranked = FilterSql.orderBySql(Filter(q = Some("collapse")))
    assert(ranked.text.startsWith("ORDER BY (ts_rank_cd(s.tsv, websearch_to_tsquery"), ranked.text)
    assert(ranked.text.endsWith(" DESC, s.cited_by_count DESC NULLS LAST, s.citekey"), ranked.text)
    assertEquals(ranked.params, List.fill(3)(S("collapse"))) // one per tsquery in rankExpr
    // CTE path: uses precomputed rank
    val ctePath = FilterSql.orderBySql(Filter(q = Some("collapse")), useCte = true)
    assert(ctePath.text.contains("coalesce(bc.rank, 0)"), ctePath.text)
    assert(!ctePath.text.contains("source_chunks"), ctePath.text)
    assertEquals(ctePath.params, List(S("collapse"))) // one tsquery for ts_rank_cd on source
    assertEquals(FilterSql.orderBySql(Filter()), Sql(FilterSql.orderBy(Sort.Relevance), Nil))
    // an explicit sort always wins over relevance ranking
    assertEquals(
      FilterSql.orderBySql(Filter(q = Some("x"), sort = Sort.Title)),
      Sql(FilterSql.orderBy(Sort.Title), Nil)
    )

  test("FilterSql.text trims the query and treats blank as absent"):
    assertEquals(FilterSql.text(Filter(q = Some("  x "))), Some("x"))
    assertEquals(FilterSql.text(Filter(q = Some("   "))), None)
    assertEquals(FilterSql.text(Filter()), None)

  test("findingsQuery binds one parameter per citekey"):
    val s = FilterSql.findingsQuery(List("a", "b", "c"))
    assert(s.text.contains("WHERE f.citekey IN (?, ?, ?)"), s.text)
    assertEquals(s.params, List(S("a"), S("b"), S("c")))

  test("facetsQuery matches the prefix as displayed: an underscore and a space are the same"):
    val spaced = FilterSql.facetsQuery(Facet.Field.Topics, Some("bronze age"), Filter(), 10)
    val under = FilterSql.facetsQuery(Facet.Field.Topics, Some("bronze_age"), Filter(), 10)
    assert(spaced.text.contains("replace(v, '_', ' ') ILIKE ?"), spaced.text)
    assertEquals(spaced.params, List(S("bronze age%"), I(10)))
    assertEquals(under.params, spaced.params)

  test("facetsQuery: distinct-source counts, filter applied, prefix is a prefix"):
    val s = FilterSql.facetsQuery(Facet.Field.Tags, Some("in"), Filter(status = List("current")), 25)
    assert(s.text.startsWith("SELECT mode() WITHIN GROUP (ORDER BY v) AS value"), s.text)
    assert(s.text.contains("count(DISTINCT s.citekey)::int AS count"), s.text)
    assert(s.text.contains("CROSS JOIN LATERAL"), s.text)
    assert(s.text.contains("WHERE lower(s.status) IN (?)"), s.text)
    assertEquals(s.params, List(S("current"), S("in%"), I(25)))

  test("facetsQuery groups values case-insensitively and labels them with the commonest spelling"):
    val s = FilterSql.facetsQuery(Facet.Field.Places, None, Filter(), 10)
    // one bucket for Anatolia/anatolia …
    assert(s.text.contains("GROUP BY lower(v)"), s.text)
    // … labelled by the most frequent original spelling, ordered by distinct sources then the label
    assert(s.text.contains("mode() WITHIN GROUP (ORDER BY v)"), s.text)
    assert(s.text.contains("ORDER BY count(DISTINCT s.citekey) DESC, lower(v) ASC"), s.text)

  // ── the finding-level predicate (map feed + histogram) ──────────────────────────────────────

  test("sourceOnly drops every finding-level criterion, keeping the source-level ones"):
    val f = Filter(
      q = Some("x"),
      authors = List("a"),
      topics = List("t"),
      status = List("current"),
      years = Some(Period(2000, 2020)),
      tags = List("tag"),
      places = List("p"),
      period = Some(Period(-100, -50)),
      bbox = Some(BBox(1, 2, 3, 4))
    )
    val s = FilterSql.sourceOnly(f)
    assertEquals((s.tags, s.places, s.period, s.bbox), (Nil, Nil, None, None))
    assertEquals((s.q, s.authors, s.topics, s.status, s.years), (f.q, f.authors, f.topics, f.status, f.years))
    // …so the source-level predicate never reaches into findings
    assert(!FilterSql.sourceWhere(f).text.contains("FROM findings"), FilterSql.sourceWhere(f).text)

  test("findingPlaceWhere: tags over findings.tags ∪ sources.tags, AND semantics"):
    val s = FilterSql.findingPlaceWhere(Filter(tags = List("steppe")))
    assertEquals(
      s.text,
      "(EXISTS (SELECT 1 FROM unnest(f.tags) x WHERE lower(x) = lower(?))" +
        " OR EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower(?)))"
    )
    assertEquals(s.params, List(S("steppe"), S("steppe")))
    assertEquals(FilterSql.findingPlaceWhere(Filter(tags = List("a", "b"))).text.split(" AND ").length, 2)

  test("findingPlaceWhere: places are any-of on region_name, escaped"):
    assertEquals(
      FilterSql.findingPlaceWhere(Filter(places = List("Anatolia"))),
      Sql("f.region_name ILIKE ? ESCAPE '\\'", List(S("%Anatolia%")))
    )
    assert(FilterSql.findingPlaceWhere(Filter(places = List("a", "b"))).text.contains(" OR "))

  test("findingPlaceWhere: the viewport splits at the antimeridian"):
    val s = FilterSql.findingPlaceWhere(Filter(bbox = Some(BBox(170.0, -60.0, -170.0, 60.0))))
    assertEquals(
      s.text,
      "(f.geom IS NOT NULL AND (f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)" +
        " OR f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)))"
    )
    assertEquals(s.params, List(D(170.0), D(-60.0), D(180.0), D(60.0), D(-180.0), D(-60.0), D(-170.0), D(60.0)))

  test("findingPeriodWhere is the finding's own overlap; TRUE without a period"):
    assertEquals(
      FilterSql.findingPeriodWhere(Filter(period = Some(Period(-1300, -1100)))),
      Sql("f.period && int8range(?::bigint, ?::bigint, '[]')", List(L(-1300), L(-1100)))
    )
    assertEquals(FilterSql.findingPeriodWhere(Filter()), Sql("TRUE", Nil))

  test("findingWhere ANDs period with the rest and is TRUE for an empty filter"):
    assertEquals(FilterSql.findingWhere(Filter()), Sql("TRUE", Nil))
    val s = FilterSql.findingWhere(Filter(period = Some(Period(-1300, -1100)), places = List("Ugarit")))
    assertEquals(s.text, "f.period && int8range(?::bigint, ?::bigint, '[]') AND f.region_name ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List(L(-1300), L(-1100), S("%Ugarit%")))

  test("hasFindingPlaceCriteria is true only for tag/place/bbox"):
    assert(!FilterSql.hasFindingPlaceCriteria(Filter(q = Some("x"), period = Some(Period(1, 2)))))
    assert(FilterSql.hasFindingPlaceCriteria(Filter(tags = List("t"))))
    assert(FilterSql.hasFindingPlaceCriteria(Filter(places = List("p"))))
    assert(FilterSql.hasFindingPlaceCriteria(Filter(bbox = Some(BBox(1, 2, 3, 4)))))

  test("findingsFeedQuery: source-level WHERE, then finding-level, ordered and limited"):
    val s = FilterSql.findingsFeedQuery(Filter(q = Some("collapse"), tags = List("steppe"), limit = 7))
    assert(s.text.startsWith("SELECT f.citekey, s.title, f.claim, f.region_name"), s.text)
    assert(s.text.contains("FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"), s.text)
    assert(s.text.contains("s.tsv @@ websearch_to_tsquery"), s.text) // source-level q (FTS)
    assert(s.text.contains("unnest(f.tags)"), s.text) // finding-level tag
    assert(s.text.endsWith("ORDER BY lower(f.period) NULLS LAST, f.citekey, f.id LIMIT ? OFFSET ?"), s.text)
    assertEquals(s.params, List(S("collapse"), S("collapse"), S("steppe"), S("steppe"), I(7), I(0)))

  test("findingsFeedQuery never sends a negative limit or offset; it pages by offset (geo_when, #1798)"):
    assertEquals(FilterSql.findingsFeedQuery(Filter(limit = -1, offset = -3)).params, List(I(0), I(0)))
    assertEquals(FilterSql.findingsFeedQuery(Filter(limit = 50, offset = 100)).params, List(I(50), I(100)))

  test("#716: findingsCountQuery counts exactly what the map feed selects, uncapped"):
    val f = Filter(q = Some("collapse"), tags = List("steppe"), limit = 7)
    val feed = FilterSql.findingsFeedQuery(f)
    val n = FilterSql.findingsCountQuery(f)
    assert(
      n.text.startsWith("SELECT count(*)::int FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"),
      n.text
    )
    // the same WHERE, word for word, and no ORDER BY / LIMIT
    val where = (s: String) => s.substring(s.indexOf(" WHERE "))
    assert(feed.text.contains(where(n.text)), s"${n.text}\n${feed.text}")
    assert(!n.text.contains("LIMIT") && !n.text.contains("ORDER BY"), n.text)
    assertEquals(n.params, feed.params.dropRight(2))

  test("facet dimensions: places and tags union the source and its findings"):
    assert(FilterSql.facetValues(Facet.Field.Tags).contains("unnest(f.tags)"))
    assert(FilterSql.facetValues(Facet.Field.Places).contains("f.region_name"))
    assertEquals(FilterSql.facetValues(Facet.Field.Status), "SELECT s.status")

  // ── pure match explanation ──────────────────────────────────────────────────────────────────
  // the columns arrive as `text[]` lists, so a value containing a comma still matches exactly
  private def j(vs: String*) = vs.toList
  private val row = FilterSql.SourceRow(
    citekey = "cline2014_collapse",
    title = "1177 BC: The Year Civilization Collapsed",
    year = Some(2014),
    status = Source.Status.Current,
    topics = j("collapse", "trade"),
    regions = j("Levant", "Aegean"),
    periodStart = Some(-1300),
    periodEnd = Some(-1100),
    summary = Some("Systems collapse."),
    tags = j("sea-peoples", "bronze-age"),
    authors = j("Eric H. Cline"),
    bodyHit = false
  )

  test("overlaps is inclusive on both bounds and false for an undated row"):
    assert(FilterSql.overlaps(Some(-1300), Some(-1100), Period(-1100, -900)))
    assert(FilterSql.overlaps(Some(-1300), Some(-1100), Period(-1500, -1300)))
    assert(!FilterSql.overlaps(Some(-1300), Some(-1100), Period(-1099, -900)))
    assert(!FilterSql.overlaps(None, None, Period(-1300, -1100)))

  test("sourceWhy explains every source-level criterion"):
    val f = Filter(
      q = Some("Collapsed"),
      tags = List("sea-peoples"),
      authors = List("cline"),
      places = List("levant"),
      topics = List("collapse"),
      status = List("current"),
      years = Some(Period(2000, 2020)),
      period = Some(Period(-1200, -1000))
    )
    assertEquals(
      FilterSql.sourceWhy(f, row),
      List(
        "title matches 'Collapsed'",
        "tag: sea-peoples",
        "author: Eric H. Cline",
        "region: Levant",
        "topic: collapse",
        "status: current",
        "year: 2014",
        "period: 1300 BCE–1100 BCE"
      )
    )

  test("sourceWhy reports a body-only text match"):
    val f = Filter(q = Some("Ugarit"))
    assertEquals(FilterSql.sourceWhy(f, row.copy(bodyHit = true)), List("full text matches 'Ugarit'"))

  test("queryTerms reads the websearch syntax the tsquery uses"):
    assertEquals(FilterSql.queryTerms("\"sea peoples\""), List("sea", "peoples"))
    assertEquals(FilterSql.queryTerms("\"sea peoples\" -egypt"), List("sea", "peoples"))
    assertEquals(FilterSql.queryTerms("collapse or drought"), List("collapse", "drought"))
    assertEquals(FilterSql.queryTerms("   "), Nil)

  test("sourceWhy explains a phrase query, whose words are never in the text verbatim"):
    val f = Filter(q = Some("\"year civilization\""))
    assertEquals(FilterSql.sourceWhy(f, row), List("title matches '\"year civilization\"'"))
    // every word must be there: a half-match is not claimed as a title match
    assertEquals(FilterSql.sourceWhy(Filter(q = Some("civilization pottery")), row), Nil)
    // an excluded term is not something the source "matches"
    assertEquals(
      FilterSql.sourceWhy(Filter(q = Some("collapsed -pottery")), row),
      List("title matches 'collapsed -pottery'")
    )

  test("an empty filter explains nothing"):
    assertEquals(FilterSql.sourceWhy(Filter(), row), Nil)

  test("a value containing a COMMA still explains itself, and still renders comma-joined"):
    // "Stocker, Sharon R." is ONE author and "trade, long-distance" is ONE tag; splitting the
    // column on ',' turned each into two values that matched nothing.
    val r = row.copy(tags = j("trade, long-distance"), authors = j("Stocker, Sharon R.", "Davis, J."))
    assertEquals(FilterSql.sourceWhy(Filter(tags = List("trade, long-distance")), r), List("tag: trade, long-distance"))
    assertEquals(FilterSql.sourceWhy(Filter(authors = List("stocker")), r), List("author: Stocker, Sharon R."))
    // …and a tag that is merely a PIECE of one does not match
    assertEquals(FilterSql.sourceWhy(Filter(tags = List("trade")), r), Nil)
    // the wire shape is now a proper list
    assertEquals(r.toHit.topics, List("collapse", "trade"))

  test("findingWhy compares exact tag values"):
    val m = Finding.Match(
      "x#0",
      "claim",
      None,
      None,
      None,
      Some(-1190),
      Some(-1185),
      None,
      List("trade, long-distance", "destruction"),
      Nil,
      List("Ugarit, Syria")
    )
    assertEquals(
      FilterSql.findingWhy(Filter(tags = List("trade, long-distance")), m),
      List("tag: trade, long-distance")
    )
    assertEquals(FilterSql.findingWhy(Filter(tags = List("trade")), m), Nil)

  private def fm(
      region: Option[String],
      lon: Option[Double],
      lat: Option[Double],
      start: Option[Long],
      end: Option[Long],
      tags: String
  ) =
    Finding.Match(
      "x#0",
      "claim",
      region,
      lon,
      lat,
      start,
      end,
      None,
      if tags.isEmpty then Nil else tags.split(",").map(_.trim).toList,
      Nil
    )

  test("findingWhy names the criteria a finding satisfies"):
    val m = fm(Some("Ugarit, Syria"), Some(35.8), Some(35.6), Some(-1190), Some(-1185), "destruction")
    val f = Filter(
      tags = List("destruction"),
      places = List("ugarit"),
      period = Some(Period(-1200, -1100)),
      bbox = Some(BBox(30.0, 30.0, 40.0, 40.0))
    )
    assertEquals(FilterSql.findingWhy(f, m), List("period", "bbox", "place: ugarit", "tag: destruction"))

  test("findingWhy is empty when nothing matches → the finding is not evidence"):
    val m = fm(Some("Cyprus"), Some(33.0), Some(35.0), Some(-800), Some(-700), "trade")
    assertEquals(FilterSql.findingWhy(Filter(period = Some(Period(-1300, -1100))), m), Nil)

  test("findingWhy: bbox needs a point; the antimeridian rule matches both sides"):
    val f = Filter(bbox = Some(BBox(170.0, -20.0, -170.0, -5.0)))
    assertEquals(FilterSql.findingWhy(f, fm(None, Some(178.0), Some(-17.7), None, None, "")), List("bbox"))
    assertEquals(FilterSql.findingWhy(f, fm(None, Some(-172.0), Some(-13.8), None, None, "")), List("bbox"))
    assertEquals(FilterSql.findingWhy(f, fm(None, Some(0.0), Some(-13.8), None, None, "")), Nil)
    assertEquals(FilterSql.findingWhy(f, fm(None, None, None, None, None, "")), Nil)

  // ── the title quick-filter, and the lists related to the source set (#406) ─────────────────

  test("title: a case-insensitive substring of the source title, % and _ escaped"):
    val s = where(Filter(title = Some("  sea_peoples ")))
    assertEquals(s.text, "s.title ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List(S("%sea\\_peoples%")))
    assertEquals(where(Filter(title = Some("  "))), Sql("TRUE", Nil))

  test("sourceSet drops the title, sort and paging — they belong to the list, not to the set"):
    val f = Filter(tags = List("x"), title = Some("t"), sort = Sort.Year, limit = 3, offset = 6)
    assertEquals(f.sourceSet, Filter(tags = List("x")))
    assert(Filter(title = Some("t")).sourceSet.isEmpty)
    assert(!Filter(title = Some("t")).isEmpty)

  test("articleWhere: an empty filter restricts nothing; a title alone narrows the article title"):
    assertEquals(FilterSql.articleWhere(Filter()), Sql("TRUE", Nil))
    assertEquals(FilterSql.articleWhere(Filter(title = Some("troy"))).text, "a.title ILIKE ? ESCAPE '\\'")

  test("articleWhere: the period is part of the source set (the articles of the Sources list)"):
    val f = Filter(period = Some(Period(-1300, -1100)))
    val s = FilterSql.articleWhere(f)
    // the source set is an ARRAY init-plan, computed once per statement (never per article link)
    assert(
      s.text.contains("al.target = ANY(ARRAY(SELECT s.citekey FROM sources s WHERE " + where(f).text + "))"),
      s.text
    )
    assertEquals(s.params, where(f).params)

  test("eventWhere: the period is the EVENT's own span, the rest selects its source — its own or an attesting one"):
    val f = Filter(status = List("current"), period = Some(Period(-1300, -1100)), title = Some("kadesh"))
    val s = FilterSql.eventWhere(f)
    val src = where(Filter(status = List("current")))
    val set = "ARRAY(SELECT s.citekey FROM sources s WHERE " + src.text + ")"
    assertEquals(
      s.text,
      s"(e.citekey = ANY($set) OR EXISTS (SELECT 1 FROM attestations ea WHERE ea.target_type = 'event' " +
        s"AND ea.target_id = e.id AND ea.citekey = ANY($set)))" +
        " AND e.outer_span && int8range(?::bigint, ?::bigint, '[]')" +
        " AND e.label ILIKE ? ESCAPE '\\'"
    )
    assertEquals(s.params, src.params ++ src.params ++ List(L(-1300), L(-1100), S("%kadesh%")))

  test("eventWhere: a period alone puts no condition on the source (events without one stay listed)"):
    val s = FilterSql.eventWhere(Filter(period = Some(Period(-1300, -1100))))
    assertEquals(s.text, "e.outer_span && int8range(?::bigint, ?::bigint, '[]')")
    assertEquals(FilterSql.eventWhere(Filter()), Sql("TRUE", Nil))

  // #769: inside a Magnum sql"…" the interpolator processes escapes, so `ESCAPE '\'` written there
  // reached Postgres as `ESCAPE ''` and a `_` in the search never matched. No source may spell it so.
  test("no repo spells the LIKE escape clause in a form that loses its backslash"):
    import java.nio.file.{Files, Path}
    import scala.jdk.CollectionConverters.*
    val dir = Path.of(sys.props.getOrElse("histmersion.repoRoot", ".")).resolve("modules/db/src/main/scala")
    val broken = "ESCAPE '\\'" // as written in source: ESCAPE, a quote, ONE backslash, a quote
    assert(Files.isDirectory(dir), s"no $dir")
    val bad = Files
      .walk(dir)
      .iterator
      .asScala
      .filter(_.toString.endsWith(".scala"))
      .flatMap { f =>
        Files.readAllLines(f).asScala.zipWithIndex.collect {
          case (l, i) if l.contains(broken) => s"${f.getFileName}:${i + 1}"
        }
      }
      .toList
    assertEquals(bad, Nil)
