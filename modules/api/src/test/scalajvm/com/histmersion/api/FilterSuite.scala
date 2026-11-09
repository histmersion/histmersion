package com.histmersion
package api

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.SearchMarkdown

import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Facet, Filter, Hit, SearchHit, SearchResult, Sort}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Finding
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Place

/** The `Filter` contract (plan §1.0): the flat wire shape, its interpretation, and the Markdown that shows a human/LLM
  * *why* each source matched. All pure.
  */
class FilterSuite extends munit.FunSuite:

  test("Filter.of trims, splits comma lists and drops blanks"):
    val f = Filter.of(
      q = Some(Filter.Q("  collapse ")),
      tags = List(Filter.Tags("sea-peoples, steppe"), Filter.Tags(" "), Filter.Tags("bronze-age"))
    )
    assertEquals(f.q, Some("collapse"))
    assertEquals(f.tags, List("sea-peoples", "steppe", "bronze-age"))
    assertEquals(Filter.of(q = Some(Filter.Q("   "))).q, None)

  test("Filter.of builds closed periods from half-open bounds"):
    assertEquals(
      Filter.of(from = Some(Year(-1300)), to = Some(Year(-1100))).period,
      Some(Period(Year(-1300), Year(-1100)))
    )
    assertEquals(Filter.of(from = Some(Year(-1300))).period, Some(Period(Year(-1300), Filter.Latest)))
    assertEquals(Filter.of(to = Some(Year(-1100))).period, Some(Period(Filter.Earliest, Year(-1100))))
    assertEquals(Filter.of().period, None)
    assertEquals(
      Filter.of(from = Some(Year(-1100)), to = Some(Year(-1300))).period,
      Some(Period(Year(-1300), Year(-1100)))
    )

  test("a bbox needs all four corners"):
    assertEquals(
      Filter.of(minLon = Some(Longitude(1)), minLat = Some(Latitude(2)), maxLon = Some(Longitude(3))).bbox,
      None
    )
    assertEquals(
      Filter
        .of(
          minLon = Some(Longitude(1)),
          minLat = Some(Latitude(2)),
          maxLon = Some(Longitude(3)),
          maxLat = Some(Latitude(4))
        )
        .bbox,
      Some(BBox(Longitude(1), Latitude(2), Longitude(3), Latitude(4)))
    )

  test("limit <= 0 means 'server default'; offset is never negative"):
    assertEquals(Filter.of().limit, 0)
    assertEquals(Filter.of(offset = Some(Filter.Offset(-5))).offset, 0)

  test("Sort parses the wire labels and round-trips"):
    assertEquals(Sort.parse(Sort.Wire("period")), Some(Sort.PeriodStart))
    assertEquals(Sort.parse(Sort.Wire("PERIOD_START")), Some(Sort.PeriodStart))
    assertEquals(Sort.parse(Sort.Wire("nonsense")), None)
    assertEquals(Sort.parse(Sort.Wire("newest")), Some(Sort.Year))
    assertEquals(Sort.parse(Sort.Wire("oldest")), Some(Sort.Oldest))
    Sort.values.foreach(s => assertEquals(Sort.parse(Sort.label(s)), Some(s)))
    assertEquals(Filter.of(sort = Some(Sort.Wire("nonsense"))).sort, Sort.Relevance)

  test("FacetField parses its wire labels"):
    assertEquals(Facet.Field.parse(Facet.Field.Wire("places")), Some(Facet.Field.Places))
    assertEquals(Facet.Field.parse(Facet.Field.Wire("Tags")), Some(Facet.Field.Tags))
    assertEquals(Facet.Field.parse(Facet.Field.Wire("colour")), None)
    assertEquals(Facet.Field.all, List("tags", "authors", "places", "topics", "status", "provenance"))

  test("BBox.parse reads the CLI spelling"):
    assertEquals(
      BBox.parse(BBox.Text("25,30, 45 ,42")),
      Some(BBox(Longitude(25), Latitude(30), Longitude(45), Latitude(42)))
    )
    assertEquals(BBox.parse(BBox.Text("25,30,45")), None)
    assertEquals(BBox.parse(BBox.Text("a,b,c,d")), None)

  test("BBox.contains honours the antimeridian split"):
    val pacific = BBox(Longitude(170), Latitude(-20), Longitude(-170), Latitude(-5))
    assert(pacific.crossesAntimeridian)
    assert(pacific.contains(Longitude(178), Latitude(-17)))
    assert(pacific.contains(Longitude(-172), Latitude(-13)))
    assert(!pacific.contains(Longitude(0), Latitude(-13)))
    assert(!pacific.contains(Longitude(178), Latitude(10)))
    assert(BBox(Longitude(25), Latitude(30), Longitude(45), Latitude(42)).contains(Longitude(35), Latitude(39)))

  test("FilterQuery round-trips a Filter (so the CLI's derived client cannot drift)"):
    val f = Filter.of(
      q = Some(Filter.Q("collapse")),
      tags = List(Filter.Tags("sea-peoples")),
      authors = List(Filter.Authors("cline")),
      places = List(Filter.Places("Levant")),
      topics = List(Filter.Topics("collapse")),
      status = List(Filter.Status("current")),
      yearFrom = Some(Year(2000)),
      yearTo = Some(Year(2020)),
      from = Some(Year(-1300)),
      to = Some(Year(-1100)),
      minLon = Some(Longitude(25)),
      minLat = Some(Latitude(30)),
      maxLon = Some(Longitude(45)),
      maxLat = Some(Latitude(42)),
      sort = Some(Sort.Wire("cited")),
      limit = Some(Filter.Limit(5)),
      offset = Some(Filter.Offset(10)),
      title = Some(Filter.Title("troy"))
    )
    assertEquals(Filter.Query.from(f).toFilter, f)
    assertEquals(f.title, Some("troy"))

  test("half-open ranges survive the round-trip as half-open"):
    val f = Filter.of(from = Some(Year(-1300)))
    assertEquals(Filter.Query.from(f).to, None)
    assertEquals(Filter.Query.from(f).toFilter, f)

  test("an empty Filter round-trips to an empty query"):
    val q = Filter.Query.from(Filter())
    assertEquals(q.tag, Nil)
    assertEquals(q.limit, None)
    assertEquals(q.toFilter, Filter())
    assert(Filter().isEmpty)
    assert(!Filter().hasFindingCriteria)
    assert(Filter(bbox = Some(BBox(Longitude(0), Latitude(0), Longitude(1), Latitude(1)))).hasFindingCriteria)

  test("title is a criterion (trimmed; blank is none) but not part of the source set"):
    assertEquals(Filter.of(title = Some(Filter.Title("  Troy "))).title, Some("Troy"))
    assertEquals(Filter.of(title = Some(Filter.Title("   "))).title, None)
    assert(!Filter(title = Some(Filter.Title("troy"))).isEmpty)
    assert(Filter(title = Some(Filter.Title("troy")), sort = Sort.Year, limit = Filter.Limit(2)).sourceSet.isEmpty)
    assertEquals(
      Filter(status = List(Filter.Status("current")), title = Some(Filter.Title("t"))).sourceSet,
      Filter(status = List(Filter.Status("current")))
    )

  // ── Markdown: every hit must show the evidence ─────────────────────────────────────────────
  private val hit = Hit(
    SearchHit(
      Citekey("cline2014_collapse"),
      Title("1177 BC"),
      (Some(2014)).map(Year.apply),
      Source.Status.Current,
      List(Source.Topics("collapse")),
      List(Source.Regions("Levant")),
      Some(Year(-1300)),
      Some(Year(-1100)),
      Some(Source.Summary("Systems collapse."))
    ),
    List(
      Finding.Match(
        Finding.Id("cline2014_collapse#0"),
        Finding.Claim("Ugarit destroyed"),
        Some(Place.Region("Ugarit")),
        Some(Longitude(35.8)),
        Some(Latitude(35.6)),
        Some(Year(-1190)),
        Some(Year(-1185)),
        None,
        List(Finding.Tags("destruction")),
        List(Problem.Unclassified(Legacy.Text("period")), Problem.Unclassified(Legacy.Text("bbox")))
      )
    ),
    List(Problem.Unclassified(Legacy.Text("tag: sea-peoples")))
  )

  test("searchResults renders a matches: sub-list with claim, place, period and why"):
    val md = SearchMarkdown.searchResults(SearchResult(RowCount(1), List(hit)))
    assert(md.startsWith("### 1 source(s)"), md)
    assert(md.contains("- **cline2014_collapse** — 1177 BC"), md)
    assert(md.contains("why: tag: sea-peoples"), md)
    assert(md.contains("matches:"), md)
    assert(md.contains("- Ugarit destroyed"), md)
    assert(md.contains("Ugarit (35.6, 35.8) · 1190 BCE–1185 BCE · period, bbox"), md)

  test("searchResults shows the page size against the total when paging"):
    assert(SearchMarkdown.searchResults(SearchResult(RowCount(42), List(hit))).startsWith("### 1 of 42 source(s)"))
    assertEquals(SearchMarkdown.searchResults(SearchResult(RowCount(0), Nil)), "_No matching sources._")

  test("a hit with no matched findings renders without a matches: list"):
    val md = SearchMarkdown.searchResults(
      SearchResult(RowCount(1), List(hit.modify(_.matched).setTo(Nil).modify(_.why).setTo(Nil)))
    )
    assert(!md.contains("matches:"), md)
    assert(!md.contains("why:"), md)

  test("facets render value (count)"):
    assertEquals(
      SearchMarkdown.facets(
        Facet.FieldText("tags"),
        Vector(Facet(Facet.Value("sea-peoples"), Facet.Count(12)), Facet(Facet.Value("steppe"), Facet.Count(3)))
      ),
      "### tags (2)\n\n- sea-peoples (12)\n- steppe (3)"
    )
    assertEquals(SearchMarkdown.facets(Facet.FieldText("tags"), Vector.empty), "_No tags in the current filter._")

  test("periodLabel is shared by the renderers and the SQL layer's explanations"):
    assertEquals(Markdown.periodLabel(Some(Year(-1300)), Some(Year(-1100))), "1300 BCE–1100 BCE")
    assertEquals(Markdown.periodLabel(Some(Year(-1300)), Some(Year(-1300))), "1300 BCE")
    assertEquals(Markdown.periodLabel(None, Some(Year(-1100))), "")

  // ── /api/findings + /api/aggregate inputs (they carry the SAME filter) ───────────────────────

  test("FindingsQuery folds the legacy `region` parameter in as one more place"):
    val f = Filter(places = List(Filter.Places("Levant")), tags = List(Filter.Tags("sea-peoples")))
    assertEquals(
      Api.FindingsQuery(f, Some(Api.FindingsQuery.Region(" Anatolia "))).toFilter.places,
      List("Levant", "Anatolia")
    )
    assertEquals(Api.FindingsQuery(f, None).toFilter, f)
    assertEquals(Api.FindingsQuery(f, Some(Api.FindingsQuery.Region("  "))).toFilter, f)
    assertEquals(Api.FindingsQuery.of(f).toFilter, f)
    // a bare `?region=…` is exactly the old behaviour: one place name
    assertEquals(
      Api.FindingsQuery(Filter(), Some(Api.FindingsQuery.Region("Anatolia"))).toFilter.places,
      List("Anatolia")
    )

  test("AggregateQuery.time defaults `by` to time and keeps the filter as given"):
    val f = Filter(tags = List(Filter.Tags("steppe")), period = Some(Period(Year(-3300), Year(-2600))))
    assertEquals(Api.AggregateQuery.time(f), Api.AggregateQuery(Some(Api.AggregateQuery.By("time")), None, f))
    assertEquals(Api.AggregateQuery.time(f, Some(Api.AggregateQuery.Unit("findings"))).unit, Some("findings"))

  test("AggregateQuery.time asks for the FLAT strip; .tree asks for the nested answer"):
    val f = Filter(period = Some(Period(Year(-1600), Year(-1200))))
    // no depth at all = the §1.7 strip: `by=time` must not build a tree the timeline ignores
    assertEquals(Api.AggregateQuery.time(f).depth, None)
    // `.tree` always asks for one; depth 0 means "the server's configured ui.tree-depth"
    assertEquals(Api.AggregateQuery.tree(Api.AggregateQuery.By("time"), f).depth, Some(0))
    assertEquals(
      Api.AggregateQuery.tree(Api.AggregateQuery.By("time,region"), f, depth = Some(Api.AggregateQuery.Depth(3))).depth,
      Some(3)
    )
    assertEquals(
      Api.AggregateQuery.tree(Api.AggregateQuery.By("region"), f, asOf = Some(Api.AggregateQuery.AsOf(-1400L))).asOf,
      Some(-1400L)
    )

  // ── half-open ranges must never build an INVERTED period ─────────────────────────────────────

  test("a `from` past the open-ended upper sentinel does not invert the range"):
    // `?from=2000000000` used to become Period(2000000000, Latest) with start > end, which Postgres
    // refuses outright (`range lower bound must be less than or equal to range upper bound`) — a 500
    // on a perfectly ordinary query string.
    val p = Filter.periodOf(Some(Year(2000000000L)), None).get
    assert(p.start.value <= p.end.value, p.toString)
    assertEquals(p, Period(Filter.Latest, Filter.Latest))

  test("a `to` below the open-ended lower sentinel does not invert it either"):
    val p = Filter.periodOf(None, Some(Year(-2000000000L))).get
    assert(p.start.value <= p.end.value, p.toString)
    assertEquals(p, Period(Filter.Earliest, Filter.Earliest))

  test("both bounds are clamped, and a reversed pair is still ordered"):
    assertEquals(
      Filter.periodOf(Some(Year(Long.MaxValue)), Some(Year(Long.MinValue))),
      Some(Period(Filter.Earliest, Filter.Latest))
    )
    assertEquals(Filter.periodOf(Some(Year(-1100)), Some(Year(-1300))), Some(Period(Year(-1300), Year(-1100))))

  test("ordinary deep time is untouched by the clamp"):
    assertEquals(Filter.periodOf(Some(Year(-3300)), Some(Year(-1200))), Some(Period(Year(-3300), Year(-1200))))
    assertEquals(Filter.periodOf(None, None), None)

  test("lowerGiven/upperGiven report which bound the caller actually gave"):
    assertEquals(Filter.lowerGiven(Filter.periodOf(Some(Year(-1200)), None)), Some(-1200L))
    assertEquals(Filter.upperGiven(Filter.periodOf(Some(Year(-1200)), None)), None)
    assertEquals(Filter.lowerGiven(Filter.periodOf(None, Some(Year(-800)))), None)
    assertEquals(Filter.upperGiven(Filter.periodOf(None, Some(Year(-800)))), Some(-800L))
    assertEquals(Filter.lowerGiven(None), None)
