package histdb.db

import histdb.core.*
import histdb.db.FilterSql.Param.*
import histdb.db.FilterSql.{Param, Sql}

/** `Filter → SQL` is a pure function (plan §1.0), so it is tested WITHOUT a database: the SQL text
  * and the bound parameters are asserted directly. */
class FilterSqlSuite extends munit.FunSuite:

  private def where(f: Filter): Sql = FilterSql.where(f)

  test("empty filter → TRUE, no parameters"):
    assertEquals(where(Filter()), Sql("TRUE", Nil))
    assertEquals(FilterSql.countQuery(Filter()).text, "SELECT count(*)::int FROM sources s WHERE TRUE")

  test("q searches title, summary and body — one parameter each, same value"):
    val s = where(Filter(q = Some("collapse")))
    assertEquals(s.params, List(S("%collapse%"), S("%collapse%"), S("%collapse%")))
    assertEquals(
      s.text,
      "(s.title ILIKE ? ESCAPE '\\' OR s.summary ILIKE ? ESCAPE '\\' OR s.ocr_markdown ILIKE ? ESCAPE '\\')")

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
    val order = List(s.text.indexOf("s.title ILIKE"), s.text.indexOf("unnest(s.tags)"), s.text.indexOf("lower(s.status)"))
    assertEquals(order, order.sorted.filter(_ >= 0))
    assertEquals(s.params, List(S("%x%"), S("%x%"), S("%x%"), S("t"), S("t"), S("current")))

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

  test("sourcesQuery binds SELECT-list params before WHERE params, then limit/offset"):
    val s = FilterSql.sourcesQuery(Filter(q = Some("collapse"), limit = 5, offset = 10))
    assert(s.text.contains("AS body_hit FROM sources s WHERE"), s.text)
    assert(s.text.endsWith("LIMIT ? OFFSET ?"), s.text)
    assertEquals(
      s.params,
      List(S("%collapse%"), S("%collapse%"), S("%collapse%"), S("%collapse%"), I(5), I(10)))

  test("sourcesQuery without q has a constant FALSE body-hit column and no extra param"):
    val s = FilterSql.sourcesQuery(Filter(limit = 20))
    assert(s.text.contains("SELECT s.citekey"), s.text)
    assert(s.text.contains("FALSE AS body_hit"), s.text)
    assertEquals(s.params, List(I(20), I(0)))

  test("negative limit/offset never reach SQL as negatives"):
    assertEquals(FilterSql.sourcesQuery(Filter(limit = -3, offset = -9)).params, List(I(0), I(0)))

  test("findingsQuery binds one parameter per citekey"):
    val s = FilterSql.findingsQuery(List("a", "b", "c"))
    assert(s.text.contains("WHERE f.citekey IN (?, ?, ?)"), s.text)
    assertEquals(s.params, List(S("a"), S("b"), S("c")))

  test("facetsQuery: distinct-source counts, filter applied, prefix is a prefix"):
    val s = FilterSql.facetsQuery(FacetField.Tags, Some("in"), Filter(status = List("current")), 25)
    assert(s.text.startsWith("SELECT v AS value, count(DISTINCT s.citekey)::int AS count"), s.text)
    assert(s.text.contains("CROSS JOIN LATERAL"), s.text)
    assert(s.text.contains("WHERE lower(s.status) IN (?)"), s.text)
    assert(s.text.contains("GROUP BY v ORDER BY count(DISTINCT s.citekey) DESC, v ASC"), s.text)
    assertEquals(s.params, List(S("current"), S("in%"), I(25)))

  test("facet dimensions: places and tags union the source and its findings"):
    assert(FilterSql.facetValues(FacetField.Tags).contains("unnest(f.tags)"))
    assert(FilterSql.facetValues(FacetField.Places).contains("f.region_name"))
    assertEquals(FilterSql.facetValues(FacetField.Status), "SELECT s.status")

  // ── pure match explanation ──────────────────────────────────────────────────────────────────
  private val row = FilterSql.SourceRow(
    citekey = "cline2014_collapse", title = "1177 BC: The Year Civilization Collapsed",
    year = Some(2014), status = "current", topics = "collapse, trade", regions = "Levant, Aegean",
    periodStart = Some(-1300), periodEnd = Some(-1100), summary = Some("Systems collapse."),
    tags = "sea-peoples, bronze-age", authors = "Eric H. Cline", bodyHit = false)

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

  test("an empty filter explains nothing"):
    assertEquals(FilterSql.sourceWhy(Filter(), row), Nil)

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
