package histdb.db

import histdb.core.*
import histdb.db.Param.*

/** `Filter → SQL` is a pure function (plan §1.0), so it is tested WITHOUT a database: the SQL text
  * and the bound parameters are asserted directly. */
class FilterSqlSuite extends munit.FunSuite:

  private def where(f: Filter): Sql = FilterSql.where(f)

  test("empty filter → TRUE, no parameters"):
    assertEquals(where(Filter()), Sql("TRUE", Nil))
    assertEquals(FilterSql.countQuery(Filter()).text, "SELECT count(*)::int FROM sources s WHERE TRUE")

  test("q is the FTS predicate: the title/summary vector OR any body chunk (plan §1.4)"):
    val s = where(Filter(q = Some("collapse")))
    assertEquals(s, Search.ftsPredicate("collapse"))
    assert(s.text.contains("s.tsv @@ websearch_to_tsquery('english', ?)"), s.text)
    assert(s.text.contains("FROM source_chunks fts_p WHERE fts_p.citekey = s.citekey"), s.text)
    assertEquals(s.params, List(S("collapse"), S("collapse")))   // one per tsquery occurrence

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
        " OR EXISTS (SELECT 1 FROM unnest(s.authors) x WHERE x ILIKE ? ESCAPE '\\'))")
    assertEquals(s.params, List(S("%cline%"), S("%anthony%")))

  test("a single author is not parenthesised (any-of of one)"):
    assert(!where(Filter(authors = List("cline"))).text.startsWith("("))

  test("places: any-of over sources.regions ∪ findings.region_name"):
    val s = where(Filter(places = List("Anatolia")))
    assert(s.text.contains("unnest(s.regions)"), s.text)
    assert(s.text.contains("f.region_name ILIKE ?"), s.text)
    assertEquals(s.params, List(S("%Anatolia%"), S("%Anatolia%")))

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
        " AND f.period && int8range(?::bigint, ?::bigint, '[]')))")
    assertEquals(s.params, List(L(-1300), L(-1100), L(-1300), L(-1100)))

  test("bbox: one ST_MakeEnvelope, matched through the source's findings"):
    val s = where(Filter(bbox = Some(BBox(25.0, 30.0, 45.0, 42.0))))
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL" +
        " AND f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326))")
    assertEquals(s.params, List(D(25.0), D(30.0), D(45.0), D(42.0)))

  test("bbox across the antimeridian (minLon > maxLon) splits into two OR'ed envelopes"):
    val s = where(Filter(bbox = Some(BBox(170.0, -20.0, -170.0, -5.0))))
    assertEquals(
      s.text,
      "EXISTS (SELECT 1 FROM findings f WHERE f.citekey = s.citekey AND f.geom IS NOT NULL" +
        " AND (f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)" +
        " OR f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)))")
    assertEquals(
      s.params,
      List(D(170.0), D(-20.0), D(180.0), D(-5.0), D(-180.0), D(-20.0), D(-170.0), D(-5.0)))

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
    assertEquals(FilterSql.orderBy(Sort.Title), "ORDER BY lower(s.title) ASC, s.citekey")

  test("sourcesQuery with a text query joins the best chunk in and ranks by relevance"):
    val s = FilterSql.sourcesQuery(Filter(q = Some("collapse"), limit = 5, offset = 10), 30)
    assert(s.text.contains("(fts.snippet IS NOT NULL) AS body_hit, fts.snippet, fts.heading"), s.text)
    assert(s.text.contains("FROM sources s LEFT JOIN LATERAL"), s.text)
    assert(s.text.contains("ORDER BY (ts_rank_cd(s.tsv"), s.text)
    assert(s.text.endsWith("LIMIT ? OFFSET ?"), s.text)
    // every parameter is the query text, in textual order, then the paging
    assertEquals(s.params.count(_ == S("collapse")), 8)
    assertEquals(s.params.takeRight(2), List(I(5), I(10)))
    assert(s.params.contains(S("MaxFragments=2, MaxWords=30, MinWords=8, StartSel=**, StopSel=**")), s.params.toString)

  test("sourcesQuery without q has no join, a constant FALSE body-hit column and no extra param"):
    val s = FilterSql.sourcesQuery(Filter(limit = 20), 30)
    assert(s.text.contains("SELECT s.citekey"), s.text)
    assert(s.text.contains("FALSE AS body_hit, NULL::text, NULL::text FROM sources s WHERE"), s.text)
    assert(!s.text.contains("LATERAL"), s.text)
    assert(s.text.contains("ORDER BY s.cited_by_count DESC NULLS LAST, s.citekey"), s.text)
    assertEquals(s.params, List(I(20), I(0)))

  test("negative limit/offset never reach SQL as negatives"):
    assertEquals(FilterSql.sourcesQuery(Filter(limit = -3, offset = -9), 30).params, List(I(0), I(0)))

  test("orderBySql: relevance ranks by FTS when there is a query, by citations when there is not"):
    val ranked = FilterSql.orderBySql(Filter(q = Some("collapse")))
    assert(ranked.text.startsWith("ORDER BY (ts_rank_cd(s.tsv, websearch_to_tsquery"), ranked.text)
    assert(ranked.text.endsWith(" DESC, s.cited_by_count DESC NULLS LAST, s.citekey"), ranked.text)
    assertEquals(ranked.params, List.fill(3)(S("collapse")))  // one per tsquery in rankExpr
    assertEquals(FilterSql.orderBySql(Filter()), Sql(FilterSql.orderBy(Sort.Relevance), Nil))
    // an explicit sort always wins over relevance ranking
    assertEquals(FilterSql.orderBySql(Filter(q = Some("x"), sort = Sort.Title)),
      Sql(FilterSql.orderBy(Sort.Title), Nil))

  test("FilterSql.text trims the query and treats blank as absent"):
    assertEquals(FilterSql.text(Filter(q = Some("  x "))), Some("x"))
    assertEquals(FilterSql.text(Filter(q = Some("   "))), None)
    assertEquals(FilterSql.text(Filter()), None)

  test("findingsQuery binds one parameter per citekey"):
    val s = FilterSql.findingsQuery(List("a", "b", "c"))
    assert(s.text.contains("WHERE f.citekey IN (?, ?, ?)"), s.text)
    assertEquals(s.params, List(S("a"), S("b"), S("c")))

  test("facetsQuery: distinct-source counts, filter applied, prefix is a prefix"):
    val s = FilterSql.facetsQuery(FacetField.Tags, Some("in"), Filter(status = List("current")), 25)
    assert(s.text.startsWith("SELECT mode() WITHIN GROUP (ORDER BY v) AS value"), s.text)
    assert(s.text.contains("count(DISTINCT s.citekey)::int AS count"), s.text)
    assert(s.text.contains("CROSS JOIN LATERAL"), s.text)
    assert(s.text.contains("WHERE lower(s.status) IN (?)"), s.text)
    assertEquals(s.params, List(S("current"), S("in%"), I(25)))

  test("facetsQuery groups values case-insensitively and labels them with the commonest spelling"):
    val s = FilterSql.facetsQuery(FacetField.Places, None, Filter(), 10)
    // one bucket for Anatolia/anatolia …
    assert(s.text.contains("GROUP BY lower(v)"), s.text)
    // … labelled by the most frequent original spelling, ordered by distinct sources then the label
    assert(s.text.contains("mode() WITHIN GROUP (ORDER BY v)"), s.text)
    assert(s.text.contains("ORDER BY count(DISTINCT s.citekey) DESC, lower(v) ASC"), s.text)

  // ── the finding-level predicate (map feed + histogram) ──────────────────────────────────────

  test("sourceOnly drops every finding-level criterion, keeping the source-level ones"):
    val f = Filter(q = Some("x"), authors = List("a"), topics = List("t"), status = List("current"),
      years = Some(Period(2000, 2020)), tags = List("tag"), places = List("p"),
      period = Some(Period(-100, -50)), bbox = Some(BBox(1, 2, 3, 4)))
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
        " OR EXISTS (SELECT 1 FROM unnest(s.tags) x WHERE lower(x) = lower(?)))")
    assertEquals(s.params, List(S("steppe"), S("steppe")))
    assertEquals(FilterSql.findingPlaceWhere(Filter(tags = List("a", "b"))).text.split(" AND ").length, 2)

  test("findingPlaceWhere: places are any-of on region_name, escaped"):
    assertEquals(
      FilterSql.findingPlaceWhere(Filter(places = List("Anatolia"))),
      Sql("f.region_name ILIKE ? ESCAPE '\\'", List(S("%Anatolia%"))))
    assert(FilterSql.findingPlaceWhere(Filter(places = List("a", "b"))).text.contains(" OR "))

  test("findingPlaceWhere: the viewport splits at the antimeridian"):
    val s = FilterSql.findingPlaceWhere(Filter(bbox = Some(BBox(170.0, -60.0, -170.0, 60.0))))
    assertEquals(
      s.text,
      "(f.geom IS NOT NULL AND (f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)" +
        " OR f.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)))")
    assertEquals(s.params,
      List(D(170.0), D(-60.0), D(180.0), D(60.0), D(-180.0), D(-60.0), D(-170.0), D(60.0)))

  test("findingPeriodWhere is the finding's own overlap; TRUE without a period"):
    assertEquals(FilterSql.findingPeriodWhere(Filter(period = Some(Period(-1300, -1100)))),
      Sql("f.period && int8range(?::bigint, ?::bigint, '[]')", List(L(-1300), L(-1100))))
    assertEquals(FilterSql.findingPeriodWhere(Filter()), Sql("TRUE", Nil))

  test("findingWhere ANDs period with the rest and is TRUE for an empty filter"):
    assertEquals(FilterSql.findingWhere(Filter()), Sql("TRUE", Nil))
    val s = FilterSql.findingWhere(Filter(period = Some(Period(-1300, -1100)), places = List("Ugarit")))
    assertEquals(s.text,
      "f.period && int8range(?::bigint, ?::bigint, '[]') AND f.region_name ILIKE ? ESCAPE '\\'")
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
    assert(s.text.contains("s.tsv @@ websearch_to_tsquery"), s.text)   // source-level q (FTS)
    assert(s.text.contains("unnest(f.tags)"), s.text)          // finding-level tag
    assert(s.text.endsWith("ORDER BY lower(f.period) NULLS LAST, f.citekey, f.id LIMIT ?"), s.text)
    assertEquals(s.params, List(S("collapse"), S("collapse"), S("steppe"), S("steppe"), I(7)))

  test("findingsFeedQuery never sends a negative limit"):
    assertEquals(FilterSql.findingsFeedQuery(Filter(limit = -1)).params, List(I(0)))

  test("facet dimensions: places and tags union the source and its findings"):
    assert(FilterSql.facetValues(FacetField.Tags).contains("unnest(f.tags)"))
    assert(FilterSql.facetValues(FacetField.Places).contains("f.region_name"))
    assertEquals(FilterSql.facetValues(FacetField.Status), "SELECT s.status")

  // ── pure match explanation ──────────────────────────────────────────────────────────────────
  // the columns arrive `|~|`-joined (FilterSql.Sep) — the wire's comma-joined form is produced by
  // `toHit`, AFTER the matching, so a value containing a comma still matches exactly
  private def j(vs: String*) = vs.mkString(FilterSql.Sep)
  private val row = FilterSql.SourceRow(
    citekey = "cline2014_collapse", title = "1177 BC: The Year Civilization Collapsed",
    year = Some(2014), status = "current", topics = j("collapse", "trade"),
    regions = j("Levant", "Aegean"),
    periodStart = Some(-1300), periodEnd = Some(-1100), summary = Some("Systems collapse."),
    tags = j("sea-peoples", "bronze-age"), authors = j("Eric H. Cline"), bodyHit = false)

  test("overlaps is inclusive on both bounds and false for an undated row"):
    assert(FilterSql.overlaps(Some(-1300), Some(-1100), Period(-1100, -900)))
    assert(FilterSql.overlaps(Some(-1300), Some(-1100), Period(-1500, -1300)))
    assert(!FilterSql.overlaps(Some(-1300), Some(-1100), Period(-1099, -900)))
    assert(!FilterSql.overlaps(None, None, Period(-1300, -1100)))

  test("sourceWhy explains every source-level criterion"):
    val f = Filter(q = Some("Collapsed"), tags = List("sea-peoples"), authors = List("cline"),
      places = List("levant"), topics = List("collapse"), status = List("current"),
      years = Some(Period(2000, 2020)), period = Some(Period(-1200, -1000)))
    assertEquals(
      FilterSql.sourceWhy(f, row),
      List("title matches 'Collapsed'", "tag: sea-peoples", "author: Eric H. Cline", "region: Levant",
        "topic: collapse", "status: current", "year: 2014", "period: 1300 BCE–1100 BCE"))

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
    assertEquals(FilterSql.sourceWhy(Filter(q = Some("collapsed -pottery")), row),
      List("title matches 'collapsed -pottery'"))

  test("an empty filter explains nothing"):
    assertEquals(FilterSql.sourceWhy(Filter(), row), Nil)

  test("a value containing a COMMA still explains itself, and still renders comma-joined"):
    // "Stocker, Sharon R." is ONE author and "trade, long-distance" is ONE tag; splitting the
    // column on ',' turned each into two values that matched nothing.
    val r = row.copy(tags = j("trade, long-distance"), authors = j("Stocker, Sharon R.", "Davis, J."))
    assertEquals(FilterSql.sourceWhy(Filter(tags = List("trade, long-distance")), r),
      List("tag: trade, long-distance"))
    assertEquals(FilterSql.sourceWhy(Filter(authors = List("stocker")), r),
      List("author: Stocker, Sharon R."))
    // …and a tag that is merely a PIECE of one does not match
    assertEquals(FilterSql.sourceWhy(Filter(tags = List("trade")), r), Nil)
    // the wire shape is unchanged: comma-joined
    assertEquals(r.toHit.topics, "collapse, trade")

  test("findingWhy compares exact tag values, then wireMatch renders them"):
    val m = FindingMatch("x#0", "claim", None, None, None, Some(-1190), Some(-1185), None,
      j("trade, long-distance", "destruction"), Nil, j("Ugarit, Syria"))
    assertEquals(FilterSql.findingWhy(Filter(tags = List("trade, long-distance")), m),
      List("tag: trade, long-distance"))
    assertEquals(FilterSql.wireMatch(m).tags, "trade, long-distance, destruction")
    assertEquals(FilterSql.wireMatch(m).places, "Ugarit, Syria")

  private def fm(region: Option[String], lon: Option[Double], lat: Option[Double],
                 start: Option[Long], end: Option[Long], tags: String) =
    FindingMatch("x#0", "claim", region, lon, lat, start, end, None, tags, Nil)

  test("findingWhy names the criteria a finding satisfies"):
    val m = fm(Some("Ugarit, Syria"), Some(35.8), Some(35.6), Some(-1190), Some(-1185), "destruction")
    val f = Filter(tags = List("destruction"), places = List("ugarit"),
      period = Some(Period(-1200, -1100)), bbox = Some(BBox(30.0, 30.0, 40.0, 40.0)))
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
