package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.api.library.Source
import com.histmersion.api.library.Facet
import com.histmersion.api.library.Finding
import com.histmersion.util.database.given
import com.histmersion.util.database.Param
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.domain.library.Citekey

/** `Filter → SQL` is a pure function (plan §1.0), so it is tested WITHOUT a database: the SQL text and the bound
  * parameters are asserted directly.
  */
class FilterSqlSuite extends munit.FunSuite:

  private def where(f: Filter, scope: PlaceScope = PlaceScope.empty): Sql = FilterSql.where(f, scope)

  test("empty filter → TRUE, no parameters"):
    assertEquals(where(Filter()), Sql(Sql.Text("TRUE"), Nil))
    assertEquals(FilterSql.countQuery(Filter()).text, "SELECT count(*)::int FROM sources s WHERE TRUE")

  test("q is the FTS predicate: the title/summary vector OR any body chunk (plan §1.4)"):
    val s = where(Filter(q = Some(Filter.Q("collapse"))))
    assertEquals(s, Search.ftsPredicate(Search.FtsPredicateQ("collapse")))
    assert(s.text.value.contains("s.tsv @@ websearch_to_tsquery('english', ?)"), s.text)
    assert(s.text.value.contains("FROM source_chunks fts_p WHERE fts_p.citekey = s.citekey"), s.text)
    assertEquals(s.params, List(S(Param.S.V("collapse")), S(Param.S.V("collapse")))) // one per tsquery occurrence

  test("a blank q is not a filter"):
    assertEquals(where(Filter(q = Some(Filter.Q("   ")))), Sql(Sql.Text("TRUE"), Nil))

  test("tags: AND semantics, each over sources.tags ∪ findings.tags"):
    val s = where(Filter(tags = List(Filter.Tags("sea-peoples"), Filter.Tags("steppe"))))
    val one = "(EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower(?))" +
      " OR EXISTS (SELECT 1 FROM findings f, unnest(f.tags) x" +
      " WHERE f.citekey = s.citekey AND lower(x) = lower(?)))"
    assertEquals(s.text, s"$one AND $one") // one required predicate per tag → AND semantics
    assert(s.text.value.contains("FROM findings f, unnest(f.tags) x"), s.text)
    assertEquals(s.params, List(S(Param.S.V("sea-peoples")), S(Param.S.V("sea-peoples")), S(Param.S.V("steppe")), S(Param.S.V("steppe"))))

  test("authors: any-of, ILIKE substring"):
    val s = where(Filter(authors = List(Filter.Authors("cline"), Filter.Authors("anthony"))))
    assertEquals(
      s.text,
      "(EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ? ESCAPE '\\')" +
        " OR EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ? ESCAPE '\\'))"
    )
    assertEquals(s.params, List(S(Param.S.V("%cline%")), S(Param.S.V("%anthony%"))))

  test("a single author is not parenthesised (any-of of one)"):
    assert(!where(Filter(authors = List(Filter.Authors("cline")))).text.value.startsWith("("))

  test("places: any-of over sources.regions ∪ findings.region_name ∪ place_names"):
    val s = where(Filter(places = List(Filter.Places("Anatolia"))))
    assert(s.text.value.contains("unnest(s.regions)"), s.text)
    assert(s.text.value.contains("f.region_name ILIKE ?"), s.text)
    assert(s.text.value.contains("pn.name ILIKE ?"), s.text)
    assertEquals(s.params, List(S(Param.S.V("%Anatolia%")), S(Param.S.V("%Anatolia%")), S(Param.S.V("%Anatolia%"))))

  test(
    "places: a resolved gazetteer id matches through finding_places alone, never its id as a name (hover counts took 3-20 s)"
  ):
    val scope = PlaceScope(Map(PlaceScope.Value("pleiades_570577") -> List(Place.Id("pleiades_570577"), Place.Id("pleiades_1"))))
    val s = where(Filter(places = List(Filter.Places("pleiades_570577"))), scope)
    assert(s.text.value.contains("fp.place_id = ANY(?::text[])"), s.text)
    assert(!s.text.value.contains("ILIKE"), s.text)
    assertEquals(s.params, List(A(List(Param.A.V("pleiades_570577"), Param.A.V("pleiades_1")))))

  test("a resolved place id is an UNCORRELATED semi-join with its scope bound as ONE array"):
    val scope = PlaceScope(Map(PlaceScope.Value("zz_region") -> List(Place.Id("zz_region"), Place.Id("zz_child"), Place.Id("zz_grandchild"))))
    val s = FilterSql.where(Filter(places = List(Filter.Places("zz_region"))), scope)
    // computed once (a hashed sub-plan), never re-run per source or per article link
    assert(
      s.text.value.contains(
        "s.citekey IN (SELECT f.citekey FROM findings f JOIN finding_places fp" +
          " ON fp.finding_id = f.id WHERE fp.place_id = ANY(?::text[]))"
      ),
      s.text
    )
    assert(!s.text.value.contains("f.citekey = s.citekey AND fp.place_id"), s.text)
    assertEquals(s.params.head, A(List(Param.A.V("zz_region"), Param.A.V("zz_child"), Param.A.V("zz_grandchild"))))
    // a 451-place scope is still ONE parameter (a generic plan of 451 `?`s went bad; JDBC caps at 32 767)
    val big = PlaceScope(Map(PlaceScope.Value("zz_big") -> (1 to 451).map(i => Place.Id.assume(s"zz_$i")).toList))
    assertEquals(FilterSql.where(Filter(places = List(Filter.Places("zz_big"))), big).params.count(_.isInstanceOf[A]), 1)
    val f = FilterSql.findingPlaceWhere(Filter(places = List(Filter.Places("zz_region"))), scope = scope)
    assert(
      f.text.value.contains("f.id IN (SELECT fp.finding_id FROM finding_places fp WHERE fp.place_id = ANY(?::text[]))"),
      f.text
    )

  test("selectionCountsQuery: the four lists' own predicates, as one statement"):
    val f = Filter(tags = List(Filter.Tags("x")), period = Some(Period(Year(-1300), Year(-1100))))
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
    val s = where(Filter(topics = List(Filter.Topics("indo-european"))))
    assertEquals(s.text, "EXISTS (SELECT 1 FROM unnest(s.topics) x WHERE lower(x) = lower(?))")
    assertEquals(s.params, List(S(Param.S.V("indo-european"))))

  test("status: any-of, lower-cased IN list"):
    val s = where(Filter(status = List(Filter.Status("Current"), Filter.Status("FOUNDATIONAL"))))
    assertEquals(s.text, "lower(s.status) IN (?, ?)")
    assertEquals(s.params, List(S(Param.S.V("current")), S(Param.S.V("foundational"))))

  test("publication years: inclusive BETWEEN"):
    val s = where(Filter(years = Some(Period(Year(2000), Year(2020)))))
    assertEquals(s.text, "(s.year IS NOT NULL AND s.year BETWEEN ? AND ?)")
    assertEquals(s.params, List(L(Param.L.V(2000)), L(Param.L.V(2020))))

  test("deep time: inclusive int8range overlap on the source OR any of its findings"):
    val s = where(Filter(period = Some(Period(Year(-1300), Year(-1100)))))
    assertEquals(
      s.text,
      "(s.period && int8range(?::bigint, ?::bigint, '[]')" +
        " OR EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey" +
        " AND f.period && int8range(?::bigint, ?::bigint, '[]')))"
    )
    assertEquals(s.params, List(L(Param.L.V(-1300)), L(Param.L.V(-1100)), L(Param.L.V(-1300)), L(Param.L.V(-1100))))

  test("bbox: one ST_MakeEnvelope, matched through the source's findings"):
    val s = where(Filter(bbox = Some(BBox(Longitude(25.0), Latitude(30.0), Longitude(45.0), Latitude(42.0)))))
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL" +
        " AND f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326))"
    )
    assertEquals(s.params, List(D(D.V(25.0)), D(D.V(30.0)), D(D.V(45.0)), D(D.V(42.0))))

  test("bbox across the antimeridian (minLon > maxLon) splits into two OR'ed envelopes"):
    val s = where(Filter(bbox = Some(BBox(Longitude(170.0), Latitude(-20.0), Longitude(-170.0), Latitude(-5.0)))))
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL" +
        " AND (f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)" +
        " OR f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)))"
    )
    assertEquals(s.params, List(D(D.V(170.0)), D(D.V(-20.0)), D(D.V(180.0)), D(D.V(-5.0)), D(D.V(-180.0)), D(D.V(-20.0)), D(D.V(-170.0)), D(D.V(-5.0))))

  test("predicates combine with AND, in Filter field order"):
    val s = where(Filter(q = Some(Filter.Q("x")), tags = List(Filter.Tags("t")), status = List(Filter.Status("current"))))
    val order = List(s.text.value.indexOf("s.tsv @@"), s.text.value.indexOf("unnest(s.tags)"), s.text.value.indexOf("lower(s.status)"))
    assertEquals(order, order.sorted.filter(_ >= 0))
    assertEquals(s.params, List(S(Param.S.V("x")), S(Param.S.V("x")), S(Param.S.V("t")), S(Param.S.V("t")), S(Param.S.V("current"))))

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
    val s = FilterSql.sourcesQuery(Filter(q = Some(Filter.Q("collapse")), limit = Filter.Limit(5), offset = Filter.Offset(10)), Search.SnippetWords(30))
    // CTE scans source_chunks once for best chunk rank per citekey
    assert(s.text.value.startsWith("WITH fts_m AS MATERIALIZED"), s.text)
    assert(s.text.value.contains("best_chunks AS (SELECT DISTINCT ON (citekey)"), s.text)
    // each matching chunk is ranked once, not once per sort-key arm
    val (ranking, rest) = s.text.value.splitAt(s.text.value.indexOf("best_chunks AS"))
    assertEquals("ts_rank_cd\\(".r.findAllIn(ranking).length, 1, ranking)
    val bestChunks = rest.take(rest.indexOf(") SELECT"))
    assert(!bestChunks.contains("ts_rank_cd"), bestChunks)
    assert(s.text.value.contains("DISTINCT ON (citekey)"), s.text)
    // body_hit comes from the CTE join, snippets are NULL (deferred to snippetsQuery)
    assert(s.text.value.contains("(bc.citekey IS NOT NULL) AS body_hit"), s.text)
    assert(s.text.value.contains("NULL::text AS snippet"), s.text)
    // ranking uses the CTE's precomputed rank, not a correlated subquery
    assert(s.text.value.contains("LEFT JOIN best_chunks bc ON bc.citekey = s.citekey"), s.text)
    assert(s.text.value.contains("ORDER BY (ts_rank_cd(s.tsv"), s.text)
    assert(s.text.value.contains("coalesce(bc.rank, 0)"), s.text)
    assert(s.text.value.endsWith("LIMIT ? OFFSET ?"), s.text)
    // no LATERAL, no ts_headline, no MaxFragments option string
    assert(!s.text.value.contains("LATERAL"), s.text)
    assert(!s.text.value.contains("ts_headline"), s.text)
    // CTE params (2 tsquery occurrences: match + the single rank) + WHERE (2) + ORDER BY rank (1)
    assertEquals(s.params.count(_ == S(S.V("collapse"))), 5)
    assertEquals(s.params.takeRight(2), List(I(Param.I.V(5)), I(Param.I.V(10))))

  test("sourcesQuery without q has no CTE, a constant FALSE body-hit column and no extra param"):
    val s = FilterSql.sourcesQuery(Filter(limit = Filter.Limit(20)), Search.SnippetWords(30))
    assert(s.text.value.contains("SELECT s.citekey"), s.text)
    assert(s.text.value.contains("FALSE AS body_hit, NULL::text, NULL::text FROM sources s WHERE"), s.text)
    assert(!s.text.value.contains("LATERAL"), s.text)
    assert(!s.text.value.contains("WITH best_chunks"), s.text)
    assert(s.text.value.contains("ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey"), s.text)
    assertEquals(s.params, List(I(Param.I.V(20)), I(Param.I.V(0))))

  test("negative limit/offset never reach SQL as negatives"):
    assertEquals(FilterSql.sourcesQuery(Filter(limit = Filter.Limit(-3), offset = Filter.Offset(-9)), Search.SnippetWords(30)).params, List(I(Param.I.V(0)), I(Param.I.V(0))))

  test("snippetsQuery computes ts_headline for specific citekeys via two-level query"):
    val s = FilterSql.snippetsQuery(Search.Query("collapse"), Search.SnippetWords(30), List(Citekey.assume("a"), Citekey.assume("b")))
    // inner subquery: DISTINCT ON to find best chunk per citekey (no ts_headline)
    assert(s.text.value.contains("DISTINCT ON (citekey) citekey, ord"), s.text)
    assert(s.text.value.contains("citekey IN (?, ?)"), s.text)
    // outer query: ts_headline only for those N rows
    assert(s.text.value.contains("ts_headline('english', sc.body,"), s.text)
    assert(s.text.value.contains("INNER JOIN ("), s.text)
    assert(s.text.value.contains("best.citekey = sc.citekey AND best.ord = sc.ord"), s.text)
    assert(
      s.params.contains(S(S.V("MaxFragments=1, MaxWords=30, MinWords=8, StartSel=**, StopSel=**, HighlightAll=false"))),
      s.params.toString
    )
    // inner: tsv match + two rank occurrences (3 tsquery); outer: one tsquery for ts_headline
    assertEquals(s.params.count(_ == S(Param.S.V("collapse"))), 4)

  test("snippetsQuery with empty citekeys returns a no-op"):
    val s = FilterSql.snippetsQuery(Search.Query("x"), Search.SnippetWords(30), Nil)
    assert(s.text.value.contains("WHERE FALSE"), s.text)
    assertEquals(s.params, Nil)

  test("orderBySql: relevance ranks by FTS when there is a query, by citations when there is not"):
    // legacy path (useCte = false): correlated subquery
    val ranked = FilterSql.orderBySql(Filter(q = Some(Filter.Q("collapse"))))
    assert(ranked.text.value.startsWith("ORDER BY (ts_rank_cd(s.tsv, websearch_to_tsquery"), ranked.text)
    assert(ranked.text.value.endsWith(" DESC, s.cited_by_count DESC NULLS LAST, s.citekey"), ranked.text)
    assertEquals(ranked.params, List.fill(3)(S(Param.S.V("collapse")))) // one per tsquery in rankExpr
    // CTE path: uses precomputed rank
    val ctePath = FilterSql.orderBySql(Filter(q = Some(Filter.Q("collapse"))), useCte = true)
    assert(ctePath.text.value.contains("coalesce(bc.rank, 0)"), ctePath.text)
    assert(!ctePath.text.value.contains("source_chunks"), ctePath.text)
    assertEquals(ctePath.params, List(S(Param.S.V("collapse")))) // one tsquery for ts_rank_cd on source
    assertEquals(FilterSql.orderBySql(Filter()), Sql(Sql.Text(FilterSql.orderBy(Sort.Relevance)), Nil))
    // an explicit sort always wins over relevance ranking
    assertEquals(
      FilterSql.orderBySql(Filter(q = Some(Filter.Q("x")), sort = Sort.Title)),
      Sql(Sql.Text(FilterSql.orderBy(Sort.Title)), Nil)
    )

  test("FilterSql.text trims the query and treats blank as absent"):
    assertEquals(FilterSql.text(Filter(q = Some(Filter.Q("  x ")))), Some("x"))
    assertEquals(FilterSql.text(Filter(q = Some(Filter.Q("   ")))), None)
    assertEquals(FilterSql.text(Filter()), None)

  test("findingsQuery binds one parameter per citekey"):
    val s = FilterSql.findingsQuery(List(Citekey.assume("a"), Citekey.assume("b"), Citekey.assume("c")))
    assert(s.text.value.contains("WHERE f.citekey IN (?, ?, ?)"), s.text)
    assertEquals(s.params, List(S(Param.S.V("a")), S(Param.S.V("b")), S(Param.S.V("c"))))

  test("facetsQuery matches the prefix as displayed: an underscore and a space are the same"):
    val spaced = FilterSql.facetsQuery(Facet.Field.Topics, Some(FilterSql.FacetPrefix("bronze age")), Filter(), PageSize(10))
    val under = FilterSql.facetsQuery(Facet.Field.Topics, Some(FilterSql.FacetPrefix("bronze_age")), Filter(), PageSize(10))
    assert(spaced.text.value.contains("replace(v, '_', ' ') ILIKE ?"), spaced.text)
    assertEquals(spaced.params, List(S(Param.S.V("bronze age%")), I(Param.I.V(10))))
    assertEquals(under.params, spaced.params)

  test("facetsQuery: distinct-source counts, filter applied, prefix is a prefix"):
    val s = FilterSql.facetsQuery(Facet.Field.Tags, Some(FilterSql.FacetPrefix("in")), Filter(status = List(Filter.Status("current"))), PageSize(25))
    assert(s.text.value.startsWith("SELECT mode() WITHIN GROUP (ORDER BY v) AS value"), s.text)
    assert(s.text.value.contains("count(DISTINCT s.citekey)::int AS count"), s.text)
    assert(s.text.value.contains("CROSS JOIN LATERAL"), s.text)
    assert(s.text.value.contains("WHERE lower(s.status) IN (?)"), s.text)
    assertEquals(s.params, List(S(Param.S.V("current")), S(Param.S.V("in%")), I(Param.I.V(25))))

  test("facetsQuery groups values case-insensitively and labels them with the commonest spelling"):
    val s = FilterSql.facetsQuery(Facet.Field.Places, None, Filter(), PageSize(10))
    // one bucket for Anatolia/anatolia …
    assert(s.text.value.contains("GROUP BY lower(v)"), s.text)
    // … labelled by the most frequent original spelling, ordered by distinct sources then the label
    assert(s.text.value.contains("mode() WITHIN GROUP (ORDER BY v)"), s.text)
    assert(s.text.value.contains("ORDER BY count(DISTINCT s.citekey) DESC, lower(v) ASC"), s.text)

  // ── the finding-level predicate (map feed + histogram) ──────────────────────────────────────

  test("sourceOnly drops every finding-level criterion, keeping the source-level ones"):
    val f = Filter(
      q = Some(Filter.Q("x")),
      authors = List(Filter.Authors("a")),
      topics = List(Filter.Topics("t")),
      status = List(Filter.Status("current")),
      years = Some(Period(Year(2000), Year(2020))),
      tags = List(Filter.Tags("tag")),
      places = List(Filter.Places("p")),
      period = Some(Period(Year(-100), Year(-50))),
      bbox = Some(BBox(Longitude(1), Latitude(2), Longitude(3), Latitude(4)))
    )
    val s = FilterSql.sourceOnly(f)
    assertEquals((s.tags, s.places, s.period, s.bbox), (Nil, Nil, None, None))
    assertEquals((s.q, s.authors, s.topics, s.status, s.years), (f.q, f.authors, f.topics, f.status, f.years))
    // …so the source-level predicate never reaches into findings
    assert(!FilterSql.sourceWhere(f).text.value.contains("FROM findings"), FilterSql.sourceWhere(f).text)

  test("findingPlaceWhere: tags over findings.tags ∪ sources.tags, AND semantics"):
    val s = FilterSql.findingPlaceWhere(Filter(tags = List(Filter.Tags("steppe"))))
    assertEquals(
      s.text,
      "(EXISTS (SELECT 1 FROM unnest(f.tags) x WHERE lower(x) = lower(?))" +
        " OR EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower(?)))"
    )
    assertEquals(s.params, List(S(Param.S.V("steppe")), S(Param.S.V("steppe"))))
    assertEquals(FilterSql.findingPlaceWhere(Filter(tags = List(Filter.Tags("a"), Filter.Tags("b")))).text.value.split(" AND ").length, 2)

  test("findingPlaceWhere: places are any-of on region_name, escaped"):
    assertEquals(
      FilterSql.findingPlaceWhere(Filter(places = List(Filter.Places("Anatolia")))),
      Sql(Sql.Text("f.region_name ILIKE ? ESCAPE '\\'"), List(S(Param.S.V("%Anatolia%"))))
    )
    assert(FilterSql.findingPlaceWhere(Filter(places = List(Filter.Places("a"), Filter.Places("b")))).text.value.contains(" OR "))

  test("findingPlaceWhere: the viewport splits at the antimeridian"):
    val s = FilterSql.findingPlaceWhere(Filter(bbox = Some(BBox(Longitude(170.0), Latitude(-60.0), Longitude(-170.0), Latitude(60.0)))))
    assertEquals(
      s.text,
      "(f.geom IS NOT NULL AND (f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)" +
        " OR f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)))"
    )
    assertEquals(s.params, List(D(D.V(170.0)), D(D.V(-60.0)), D(D.V(180.0)), D(D.V(60.0)), D(D.V(-180.0)), D(D.V(-60.0)), D(D.V(-170.0)), D(D.V(60.0))))

  test("findingPeriodWhere is the finding's own overlap; TRUE without a period"):
    assertEquals(
      FilterSql.findingPeriodWhere(Filter(period = Some(Period(Year(-1300), Year(-1100))))),
      Sql(Sql.Text("f.period && int8range(?::bigint, ?::bigint, '[]')"), List(L(Param.L.V(-1300)), L(Param.L.V(-1100))))
    )
    assertEquals(FilterSql.findingPeriodWhere(Filter()), Sql(Sql.Text("TRUE"), Nil))

  test("findingWhere ANDs period with the rest and is TRUE for an empty filter"):
    assertEquals(FilterSql.findingWhere(Filter()), Sql(Sql.Text("TRUE"), Nil))
    val s = FilterSql.findingWhere(Filter(period = Some(Period(Year(-1300), Year(-1100))), places = List(Filter.Places("Ugarit"))))
    assertEquals(s.text, "f.period && int8range(?::bigint, ?::bigint, '[]') AND f.region_name ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List(L(Param.L.V(-1300)), L(Param.L.V(-1100)), S(Param.S.V("%Ugarit%"))))

  test("hasFindingPlaceCriteria is true only for tag/place/bbox"):
    assert(!FilterSql.hasFindingPlaceCriteria(Filter(q = Some(Filter.Q("x")), period = Some(Period(Year(1), Year(2))))))
    assert(FilterSql.hasFindingPlaceCriteria(Filter(tags = List(Filter.Tags("t")))))
    assert(FilterSql.hasFindingPlaceCriteria(Filter(places = List(Filter.Places("p")))))
    assert(FilterSql.hasFindingPlaceCriteria(Filter(bbox = Some(BBox(Longitude(1), Latitude(2), Longitude(3), Latitude(4))))))

  test("findingsFeedQuery: source-level WHERE, then finding-level, ordered and limited"):
    val s = FilterSql.findingsFeedQuery(Filter(q = Some(Filter.Q("collapse")), tags = List(Filter.Tags("steppe")), limit = Filter.Limit(7)))
    assert(s.text.value.startsWith("SELECT f.citekey, s.title, f.claim, f.region_name"), s.text)
    assert(s.text.value.contains("FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"), s.text)
    assert(s.text.value.contains("s.tsv @@ websearch_to_tsquery"), s.text) // source-level q (FTS)
    assert(s.text.value.contains("unnest(f.tags)"), s.text) // finding-level tag
    assert(s.text.value.endsWith("ORDER BY lower(f.period) NULLS LAST, f.citekey, f.id LIMIT ? OFFSET ?"), s.text)
    assertEquals(s.params, List(S(Param.S.V("collapse")), S(Param.S.V("collapse")), S(Param.S.V("steppe")), S(Param.S.V("steppe")), I(Param.I.V(7)), I(Param.I.V(0))))

  test("findingsFeedQuery never sends a negative limit or offset; it pages by offset (geo_when, #1798)"):
    assertEquals(FilterSql.findingsFeedQuery(Filter(limit = Filter.Limit(-1), offset = Filter.Offset(-3))).params, List(I(Param.I.V(0)), I(Param.I.V(0))))
    assertEquals(FilterSql.findingsFeedQuery(Filter(limit = Filter.Limit(50), offset = Filter.Offset(100))).params, List(I(Param.I.V(50)), I(Param.I.V(100))))

  test("#716: findingsCountQuery counts exactly what the map feed selects, uncapped"):
    val f = Filter(q = Some(Filter.Q("collapse")), tags = List(Filter.Tags("steppe")), limit = Filter.Limit(7))
    val feed = FilterSql.findingsFeedQuery(f)
    val n = FilterSql.findingsCountQuery(f)
    assert(
      n.text.value.startsWith("SELECT count(*)::int FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE"),
      n.text
    )
    // the same WHERE, word for word, and no ORDER BY / LIMIT
    val where = (s: String) => s.substring(s.indexOf(" WHERE "))
    assert(feed.text.value.contains(where(n.text.value)), s"${n.text}\n${feed.text}")
    assert(!n.text.value.contains("LIMIT") && !n.text.value.contains("ORDER BY"), n.text)
    assertEquals(n.params, feed.params.dropRight(2))

  test("facet dimensions: places and tags union the source and its findings"):
    assert(FilterSql.facetValues(Facet.Field.Tags).contains("unnest(f.tags)"))
    assert(FilterSql.facetValues(Facet.Field.Places).contains("f.region_name"))
    assertEquals(FilterSql.facetValues(Facet.Field.Status), "SELECT s.status")

  // ── pure match explanation ──────────────────────────────────────────────────────────────────
  // the columns arrive as `text[]` lists, so a value containing a comma still matches exactly
  private val row = FilterSql.SourceRow(
    citekey = Citekey("cline2014_collapse"),
    title = com.histmersion.api.library.Title("1177 BC: The Year Civilization Collapsed"),
    year = Some(Year(2014)),
    status = Source.Status.Current,
    topics = List(Source.Topics("collapse"), Source.Topics("trade")),
    regions = List(Source.Regions("Levant"), Source.Regions("Aegean")),
    periodStart = Some(Year(-1300)),
    periodEnd = Some(Year(-1100)),
    summary = Some(Source.Summary("Systems collapse.")),
    tags = List(Filter.Tags("sea-peoples"), Filter.Tags("bronze-age")),
    authors = List(com.histmersion.api.library.AuthorName("Eric H. Cline")),
    bodyHit = false
  )

  test("overlaps is inclusive on both bounds and false for an undated row"):
    assert(FilterSql.overlaps(Some(Year(-1300)), Some(Year(-1100)), Period(Year(-1100), Year(-900))))
    assert(FilterSql.overlaps(Some(Year(-1300)), Some(Year(-1100)), Period(Year(-1500), Year(-1300))))
    assert(!FilterSql.overlaps(Some(Year(-1300)), Some(Year(-1100)), Period(Year(-1099), Year(-900))))
    assert(!FilterSql.overlaps(None, None, Period(Year(-1300), Year(-1100))))

  test("sourceWhy explains every source-level criterion"):
    val f = Filter(
      q = Some(Filter.Q("Collapsed")),
      tags = List(Filter.Tags("sea-peoples")),
      authors = List(Filter.Authors("cline")),
      places = List(Filter.Places("levant")),
      topics = List(Filter.Topics("collapse")),
      status = List(Filter.Status("current")),
      years = Some(Period(Year(2000), Year(2020))),
      period = Some(Period(Year(-1200), Year(-1000)))
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
    val f = Filter(q = Some(Filter.Q("Ugarit")))
    assertEquals(FilterSql.sourceWhy(f, row.modify(_.bodyHit).setTo(true)), List("full text matches 'Ugarit'"))

  test("queryTerms reads the websearch syntax the tsquery uses"):
    assertEquals(FilterSql.queryTerms(Search.Query("\"sea peoples\"")), List("sea", "peoples"))
    assertEquals(FilterSql.queryTerms(Search.Query("\"sea peoples\" -egypt")), List("sea", "peoples"))
    assertEquals(FilterSql.queryTerms(Search.Query("collapse or drought")), List("collapse", "drought"))
    assertEquals(FilterSql.queryTerms(Search.Query("   ")), Nil)

  test("sourceWhy explains a phrase query, whose words are never in the text verbatim"):
    val f = Filter(q = Some(Filter.Q("\"year civilization\"")))
    assertEquals(FilterSql.sourceWhy(f, row), List("title matches '\"year civilization\"'"))
    // every word must be there: a half-match is not claimed as a title match
    assertEquals(FilterSql.sourceWhy(Filter(q = Some(Filter.Q("civilization pottery"))), row), Nil)
    // an excluded term is not something the source "matches"
    assertEquals(
      FilterSql.sourceWhy(Filter(q = Some(Filter.Q("collapsed -pottery"))), row),
      List("title matches 'collapsed -pottery'")
    )

  test("an empty filter explains nothing"):
    assertEquals(FilterSql.sourceWhy(Filter(), row), Nil)

  test("a value containing a COMMA still explains itself, and still renders comma-joined"):
    // "Stocker, Sharon R." is ONE author and "trade, long-distance" is ONE tag; splitting the
    // column on ',' turned each into two values that matched nothing.
    val r = row.modify(_.tags).setTo(List(Filter.Tags("trade, long-distance"))).modify(_.authors).setTo(List(com.histmersion.api.library.AuthorName("Stocker, Sharon R."), com.histmersion.api.library.AuthorName("Davis, J.")))
    assertEquals(FilterSql.sourceWhy(Filter(tags = List(Filter.Tags("trade, long-distance"))), r), List("tag: trade, long-distance"))
    assertEquals(FilterSql.sourceWhy(Filter(authors = List(Filter.Authors("stocker"))), r), List("author: Stocker, Sharon R."))
    // …and a tag that is merely a PIECE of one does not match
    assertEquals(FilterSql.sourceWhy(Filter(tags = List(Filter.Tags("trade"))), r), Nil)
    // the wire shape is now a proper list
    assertEquals(r.toHit.topics, List("collapse", "trade"))

  test("findingWhy compares exact tag values"):
    val m = Finding.Match(
      Finding.Id("x#0"),
      Finding.Claim("claim"),
      None,
      None,
      None,
      Some(Year(-1190)),
      Some(Year(-1185)),
      None,
      List(Finding.Tags("trade, long-distance"), Finding.Tags("destruction")),
      Nil,
      List(Finding.Match.Places("Ugarit, Syria"))
    )
    assertEquals(
      FilterSql.findingWhy(Filter(tags = List(Filter.Tags("trade, long-distance"))), m),
      List("tag: trade, long-distance")
    )
    assertEquals(FilterSql.findingWhy(Filter(tags = List(Filter.Tags("trade"))), m), Nil)

  private def fm(
      region: Option[com.histmersion.api.catalogue.Place.Region],
      lon: Option[Longitude],
      lat: Option[Latitude],
      start: Option[Year],
      end: Option[Year],
      tags: List[Finding.Tags]
  ) =
    Finding.Match(
      Finding.Id("x#0"),
      Finding.Claim("claim"),
      region,
      lon,
      lat,
      start,
      end,
      None,
      tags,
      Nil
    )

  test("findingWhy names the criteria a finding satisfies"):
    val m = fm(Some(com.histmersion.api.catalogue.Place.Region("Ugarit, Syria")), Some(Longitude(35.8)), Some(Latitude(35.6)), Some(Year(-1190)), Some(Year(-1185)), List(Finding.Tags("destruction")))
    val f = Filter(
      tags = List(Filter.Tags("destruction")),
      places = List(Filter.Places("ugarit")),
      period = Some(Period(Year(-1200), Year(-1100))),
      bbox = Some(BBox(Longitude(30.0), Latitude(30.0), Longitude(40.0), Latitude(40.0)))
    )
    assertEquals(FilterSql.findingWhy(f, m), List("period", "bbox", "place: ugarit", "tag: destruction"))

  test("findingWhy is empty when nothing matches → the finding is not evidence"):
    val m = fm(Some(com.histmersion.api.catalogue.Place.Region("Cyprus")), Some(Longitude(33.0)), Some(Latitude(35.0)), Some(Year(-800)), Some(Year(-700)), List(Finding.Tags("trade")))
    assertEquals(FilterSql.findingWhy(Filter(period = Some(Period(Year(-1300), Year(-1100)))), m), Nil)

  test("findingWhy: bbox needs a point; the antimeridian rule matches both sides"):
    val f = Filter(bbox = Some(BBox(Longitude(170.0), Latitude(-20.0), Longitude(-170.0), Latitude(-5.0))))
    assertEquals(FilterSql.findingWhy(f, fm(None, Some(Longitude(178.0)), Some(Latitude(-17.7)), None, None, Nil)), List("bbox"))
    assertEquals(FilterSql.findingWhy(f, fm(None, Some(Longitude(-172.0)), Some(Latitude(-13.8)), None, None, Nil)), List("bbox"))
    assertEquals(FilterSql.findingWhy(f, fm(None, Some(Longitude(0.0)), Some(Latitude(-13.8)), None, None, Nil)), Nil)
    assertEquals(FilterSql.findingWhy(f, fm(None, None, None, None, None, Nil)), Nil)

  // ── the title quick-filter, and the lists related to the source set (#406) ─────────────────

  test("title: a case-insensitive substring of the source title, % and _ escaped"):
    val s = where(Filter(title = Some(Filter.Title("  sea_peoples "))))
    assertEquals(s.text, "s.title ILIKE ? ESCAPE '\\'")
    assertEquals(s.params, List(S(Param.S.V("%sea\\_peoples%"))))
    assertEquals(where(Filter(title = Some(Filter.Title("  ")))), Sql(Sql.Text("TRUE"), Nil))

  test("sourceSet drops the title, sort and paging — they belong to the list, not to the set"):
    val f = Filter(tags = List(Filter.Tags("x")), title = Some(Filter.Title("t")), sort = Sort.Year, limit = Filter.Limit(3), offset = Filter.Offset(6))
    assertEquals(f.sourceSet, Filter(tags = List(Filter.Tags("x"))))
    assert(Filter(title = Some(Filter.Title("t"))).sourceSet.isEmpty)
    assert(!Filter(title = Some(Filter.Title("t"))).isEmpty)

  test("articleWhere: an empty filter restricts nothing; a title alone narrows the article title"):
    assertEquals(FilterSql.articleWhere(Filter()), Sql(Sql.Text("TRUE"), Nil))
    assertEquals(FilterSql.articleWhere(Filter(title = Some(Filter.Title("troy")))).text, "a.title ILIKE ? ESCAPE '\\'")

  test("articleWhere: the period is part of the source set (the articles of the Sources list)"):
    val f = Filter(period = Some(Period(Year(-1300), Year(-1100))))
    val s = FilterSql.articleWhere(f)
    // the source set is an ARRAY init-plan, computed once per statement (never per article link)
    assert(
      s.text.value.contains("al.target = ANY(ARRAY(SELECT s.citekey FROM sources s WHERE " + where(f).text + "))"),
      s.text
    )
    assertEquals(s.params, where(f).params)

  test("eventWhere: the period is the EVENT's own span, the rest selects its source — its own or an attesting one"):
    val f = Filter(status = List(Filter.Status("current")), period = Some(Period(Year(-1300), Year(-1100))), title = Some(Filter.Title("kadesh")))
    val s = FilterSql.eventWhere(f)
    val src = where(Filter(status = List(Filter.Status("current"))))
    val set = "ARRAY(SELECT s.citekey FROM sources s WHERE " + src.text + ")"
    assertEquals(
      s.text,
      s"(e.citekey = ANY($set) OR EXISTS (SELECT 1 FROM attestations ea WHERE ea.target_type = 'event' " +
        s"AND ea.target_id = e.id AND ea.citekey = ANY($set)))" +
        " AND e.outer_span && int8range(?::bigint, ?::bigint, '[]')" +
        " AND e.label ILIKE ? ESCAPE '\\'"
    )
    assertEquals(s.params, src.params ++ src.params ++ List(L(Param.L.V(-1300)), L(Param.L.V(-1100)), S(Param.S.V("%kadesh%"))))

  test("eventWhere: a period alone puts no condition on the source (events without one stay listed)"):
    val s = FilterSql.eventWhere(Filter(period = Some(Period(Year(-1300), Year(-1100)))))
    assertEquals(s.text, "e.outer_span && int8range(?::bigint, ?::bigint, '[]')")
    assertEquals(FilterSql.eventWhere(Filter()), Sql(Sql.Text("TRUE"), Nil))

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
