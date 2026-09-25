package com.histmersion.core

/** The `Filter` contract (plan §1.0): the flat wire shape, its interpretation, and the Markdown that
  * shows a human/LLM *why* each source matched. All pure. */
class FilterSuite extends munit.FunSuite:

  test("Filter.of trims, splits comma lists and drops blanks"):
    val f = Filter.of(q = Some("  collapse "), tags = List("sea-peoples, steppe", " ", "bronze-age"))
    assertEquals(f.q, Some("collapse"))
    assertEquals(f.tags, List("sea-peoples", "steppe", "bronze-age"))
    assertEquals(Filter.of(q = Some("   ")).q, None)

  test("Filter.of builds closed periods from half-open bounds"):
    assertEquals(Filter.of(from = Some(-1300), to = Some(-1100)).period, Some(Period(-1300, -1100)))
    assertEquals(Filter.of(from = Some(-1300)).period, Some(Period(-1300, Filter.Latest)))
    assertEquals(Filter.of(to = Some(-1100)).period, Some(Period(Filter.Earliest, -1100)))
    assertEquals(Filter.of().period, None)
    assertEquals(Filter.of(from = Some(-1100), to = Some(-1300)).period, Some(Period(-1300, -1100)))

  test("a bbox needs all four corners"):
    assertEquals(Filter.of(minLon = Some(1), minLat = Some(2), maxLon = Some(3)).bbox, None)
    assertEquals(
      Filter.of(minLon = Some(1), minLat = Some(2), maxLon = Some(3), maxLat = Some(4)).bbox,
      Some(BBox(1, 2, 3, 4)))

  test("limit <= 0 means 'server default'; offset is never negative"):
    assertEquals(Filter.of().limit, 0)
    assertEquals(Filter.of(offset = Some(-5)).offset, 0)

  test("Sort parses the wire labels and round-trips"):
    assertEquals(Sort.parse("period"), Some(Sort.PeriodStart))
    assertEquals(Sort.parse("PERIOD_START"), Some(Sort.PeriodStart))
    assertEquals(Sort.parse("nonsense"), None)
    Sort.values.foreach(s => assertEquals(Sort.parse(Sort.label(s)), Some(s)))
    assertEquals(Filter.of(sort = Some("nonsense")).sort, Sort.Relevance)

  test("FacetField parses its wire labels"):
    assertEquals(FacetField.parse("places"), Some(FacetField.Places))
    assertEquals(FacetField.parse("Tags"), Some(FacetField.Tags))
    assertEquals(FacetField.parse("colour"), None)
    assertEquals(FacetField.all, List("tags", "authors", "places", "topics", "status", "provenance"))

  test("BBox.parse reads the CLI spelling"):
    assertEquals(BBox.parse("25,30, 45 ,42"), Some(BBox(25, 30, 45, 42)))
    assertEquals(BBox.parse("25,30,45"), None)
    assertEquals(BBox.parse("a,b,c,d"), None)

  test("BBox.contains honours the antimeridian split"):
    val pacific = BBox(170, -20, -170, -5)
    assert(pacific.crossesAntimeridian)
    assert(pacific.contains(178, -17))
    assert(pacific.contains(-172, -13))
    assert(!pacific.contains(0, -13))
    assert(!pacific.contains(178, 10))
    assert(BBox(25, 30, 45, 42).contains(35, 39))

  test("FilterQuery round-trips a Filter (so the CLI's derived client cannot drift)"):
    val f = Filter.of(q = Some("collapse"), tags = List("sea-peoples"), authors = List("cline"),
      places = List("Levant"), topics = List("collapse"), status = List("current"),
      yearFrom = Some(2000), yearTo = Some(2020), from = Some(-1300), to = Some(-1100),
      minLon = Some(25), minLat = Some(30), maxLon = Some(45), maxLat = Some(42),
      sort = Some("cited"), limit = Some(5), offset = Some(10))
    assertEquals(FilterQuery.from(f).toFilter, f)

  test("half-open ranges survive the round-trip as half-open"):
    val f = Filter.of(from = Some(-1300))
    assertEquals(FilterQuery.from(f).to, None)
    assertEquals(FilterQuery.from(f).toFilter, f)

  test("an empty Filter round-trips to an empty query"):
    val q = FilterQuery.from(Filter())
    assertEquals(q.tag, Nil)
    assertEquals(q.limit, None)
    assertEquals(q.toFilter, Filter())
    assert(Filter().isEmpty)
    assert(!Filter().hasFindingCriteria)
    assert(Filter(bbox = Some(BBox(0, 0, 1, 1))).hasFindingCriteria)

  // ── Markdown: every hit must show the evidence ─────────────────────────────────────────────
  private val hit = Hit(
    SearchHit("cline2014_collapse", "1177 BC", Some(2014), SourceStatus.Current, List("collapse"), List("Levant"),
      Some(-1300), Some(-1100), Some("Systems collapse.")),
    List(FindingMatch("cline2014_collapse#0", "Ugarit destroyed", Some("Ugarit"), Some(35.8),
      Some(35.6), Some(-1190), Some(-1185), None, List("destruction"), List("period", "bbox"))),
    List("tag: sea-peoples"))

  test("searchResults renders a matches: sub-list with claim, place, period and why"):
    val md = Markdown.searchResults(SearchResult(1, List(hit)))
    assert(md.startsWith("### 1 source(s)"), md)
    assert(md.contains("- **cline2014_collapse** — 1177 BC"), md)
    assert(md.contains("why: tag: sea-peoples"), md)
    assert(md.contains("matches:"), md)
    assert(md.contains("- Ugarit destroyed"), md)
    assert(md.contains("Ugarit (35.6, 35.8) · 1190 BCE–1185 BCE · period, bbox"), md)

  test("searchResults shows the page size against the total when paging"):
    assert(Markdown.searchResults(SearchResult(42, List(hit))).startsWith("### 1 of 42 source(s)"))
    assertEquals(Markdown.searchResults(SearchResult(0, Nil)), "_No matching sources._")

  test("a hit with no matched findings renders without a matches: list"):
    val md = Markdown.searchResults(SearchResult(1, List(hit.copy(matched = Nil, why = Nil))))
    assert(!md.contains("matches:"), md)
    assert(!md.contains("why:"), md)

  test("facets render value (count)"):
    assertEquals(
      Markdown.facets("tags", Vector(Facet("sea-peoples", 12), Facet("steppe", 3))),
      "### tags (2)\n\n- sea-peoples (12)\n- steppe (3)")
    assertEquals(Markdown.facets("tags", Vector.empty), "_No tags in the current filter._")

  test("periodLabel is shared by the renderers and the SQL layer's explanations"):
    assertEquals(Markdown.periodLabel(Some(-1300), Some(-1100)), "1300 BCE–1100 BCE")
    assertEquals(Markdown.periodLabel(Some(-1300), Some(-1300)), "1300 BCE")
    assertEquals(Markdown.periodLabel(None, Some(-1100)), "")

  // ── /api/findings + /api/aggregate inputs (they carry the SAME filter) ───────────────────────

  test("FindingsQuery folds the legacy `region` parameter in as one more place"):
    val f = Filter(places = List("Levant"), tags = List("sea-peoples"))
    assertEquals(Api.FindingsQuery(f, Some(" Anatolia ")).toFilter.places, List("Levant", "Anatolia"))
    assertEquals(Api.FindingsQuery(f, None).toFilter, f)
    assertEquals(Api.FindingsQuery(f, Some("  ")).toFilter, f)
    assertEquals(Api.FindingsQuery.of(f).toFilter, f)
    // a bare `?region=…` is exactly the old behaviour: one place name
    assertEquals(Api.FindingsQuery(Filter(), Some("Anatolia")).toFilter.places, List("Anatolia"))

  test("AggregateQuery.time defaults `by` to time and keeps the filter as given"):
    val f = Filter(tags = List("steppe"), period = Some(Period(-3300, -2600)))
    assertEquals(Api.AggregateQuery.time(f), Api.AggregateQuery(Some("time"), None, f))
    assertEquals(Api.AggregateQuery.time(f, Some("findings")).unit, Some("findings"))

  test("AggregateQuery.time asks for the FLAT strip; .tree asks for the nested answer"):
    val f = Filter(period = Some(Period(-1600, -1200)))
    // no depth at all = the §1.7 strip: `by=time` must not build a tree the timeline ignores
    assertEquals(Api.AggregateQuery.time(f).depth, None)
    // `.tree` always asks for one; depth 0 means "the server's configured ui.tree-depth"
    assertEquals(Api.AggregateQuery.tree("time", f).depth, Some(0))
    assertEquals(Api.AggregateQuery.tree("time,region", f, depth = Some(3)).depth, Some(3))
    assertEquals(Api.AggregateQuery.tree("region", f, asOf = Some(-1400L)).asOf, Some(-1400L))

  // ── half-open ranges must never build an INVERTED period ─────────────────────────────────────

  test("a `from` past the open-ended upper sentinel does not invert the range"):
    // `?from=2000000000` used to become Period(2000000000, Latest) with start > end, which Postgres
    // refuses outright (`range lower bound must be less than or equal to range upper bound`) — a 500
    // on a perfectly ordinary query string.
    val p = Filter.periodOf(Some(2000000000L), None).get
    assert(p.start <= p.end, p.toString)
    assertEquals(p, Period(Filter.Latest, Filter.Latest))

  test("a `to` below the open-ended lower sentinel does not invert it either"):
    val p = Filter.periodOf(None, Some(-2000000000L)).get
    assert(p.start <= p.end, p.toString)
    assertEquals(p, Period(Filter.Earliest, Filter.Earliest))

  test("both bounds are clamped, and a reversed pair is still ordered"):
    assertEquals(Filter.periodOf(Some(Long.MaxValue), Some(Long.MinValue)),
      Some(Period(Filter.Earliest, Filter.Latest)))
    assertEquals(Filter.periodOf(Some(-1100), Some(-1300)), Some(Period(-1300, -1100)))

  test("ordinary deep time is untouched by the clamp"):
    assertEquals(Filter.periodOf(Some(-3300), Some(-1200)), Some(Period(-3300, -1200)))
    assertEquals(Filter.periodOf(None, None), None)

  test("lowerGiven/upperGiven report which bound the caller actually gave"):
    assertEquals(Filter.lowerGiven(Filter.periodOf(Some(-1200), None)), Some(-1200L))
    assertEquals(Filter.upperGiven(Filter.periodOf(Some(-1200), None)), None)
    assertEquals(Filter.lowerGiven(Filter.periodOf(None, Some(-800))), None)
    assertEquals(Filter.upperGiven(Filter.periodOf(None, Some(-800))), Some(-800L))
    assertEquals(Filter.lowerGiven(None), None)
