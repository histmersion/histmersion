package com.histmersion.db

import com.histmersion.domain.library.findings.FindingFixtures
import com.histmersion.domain.library.labels.LabelFixtures
import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.{BBox, GeoPoint}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Filter, Finding, Hit, SearchResult, Sort, Source}
import com.histmersion.api.library.Facet

/** `sources(filter)` / `facets(...)` against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise.
  * Three synthetic sources exercise the plan §1.0 rules: the SOURCE is the unit, a source matches through its own
  * metadata OR its findings, and every count is DISTINCT sources.
  */
class FilterRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d)
    seed(d)
    d

  private def finding(
      ck: String,
      n: Int,
      claim: String,
      region: String,
      lon: Double,
      lat: Double,
      from: Long,
      to: Long,
      tags: List[String]
  ) =
    Finding(
      s"$ck#$n",
      ck,
      claim,
      Some(region),
      Some(GeoPoint(lon, lat)),
      Some(Period(from, to)),
      None,
      tags,
      Some(Confidence.High)
    )

  private def source(
      ck: String,
      title: String,
      authors: List[String],
      year: Int,
      status: com.histmersion.domain.library.Source.Status,
      topics: List[String],
      regions: List[String],
      tags: List[String],
      period: Option[Period],
      cited: Int,
      findings: List[Finding]
  ) =
    Source(
      ck,
      title,
      authors,
      None,
      Some(year),
      None,
      status,
      topics,
      regions,
      tags,
      period,
      Some(s"summary of $title"),
      Some(cited),
      None,
      None,
      None,
      None,
      findings,
      Nil
    )

  // A: Levantine collapse, dated; two findings, only one inside the LBA window.
  private val alpha = source(
    "zzf_alpha",
    "Collapse of the Bronze Age",
    List("Eric H. Cline"),
    2014,
    com.histmersion.domain.library.Source.Status.Current,
    List("collapse"),
    List("Levant"),
    List("sea-peoples"),
    Some(Period(-1300, -1100)),
    100,
    List(
      finding("zzf_alpha", 0, "Ugarit destroyed", "Ugarit", 35.78, 35.60, -1200, -1180, List("destruction")),
      finding("zzf_alpha", 1, "Cypriot trade revives", "Cyprus", 33.00, 35.00, -800, -700, List("trade"))
    )
  )

  // B: steppe, far older, no overlap with A.
  private val beta = source(
    "zzf_beta",
    "Steppe horses",
    List("David W. Anthony"),
    2007,
    com.histmersion.domain.library.Source.Status.Foundational,
    List("migration"),
    List("Pontic Steppe"),
    List("steppe"),
    Some(Period(-4000, -3000)),
    500,
    List(
      finding(
        "zzf_beta",
        0,
        "Yamnaya expansion",
        "Pontic Steppe",
        40.0,
        47.0,
        -3300,
        -2600,
        List("steppe", "migration")
      )
    )
  )

  // C: UNDATED source whose findings straddle the antimeridian — matches only through them.
  private val gamma = source(
    "zzf_gamma",
    "Pacific island chronology",
    List("Pacific Author"),
    2020,
    com.histmersion.domain.library.Source.Status.Unassessed,
    List("chronology"),
    List("Oceania"),
    List("sea-peoples"),
    None,
    5,
    List(
      finding("zzf_gamma", 0, "Lapita site west of the line", "Fiji", 178.0, -17.7, -1000, -900, List("island")),
      finding("zzf_gamma", 1, "Site east of the line", "Samoa", -172.0, -13.8, -1000, -900, List("island"))
    )
  )

  // Case-fold fixtures (plan §1.3): the same vocabulary spelled two ways. Prefixed `zzc_` so the
  // `zzf_` assertions are untouched, and the words are unique to this suite so the shared test DB
  // cannot pollute the counts. "Zzftolia" is on two sources, "zzftolia" on one → it is the label.
  private def cased(ck: String, region: String, tag: String) =
    source(
      ck,
      s"Case study $ck",
      List("Case Author"),
      2021,
      com.histmersion.domain.library.Source.Status.Current,
      Nil,
      List(region),
      List(tag),
      None,
      0,
      List(finding(ck, 0, s"cased finding $ck", region, 1.0, 1.0, -500, -400, List(tag)))
    )

  private val cases =
    List(
      cased("zzc_one", "Zzftolia", "Zzftag"),
      cased("zzc_two", "Zzftolia", "Zzftag"),
      cased("zzc_three", "zzftolia", "zzftag")
    )

  private def seed(d: javax.sql.DataSource): Unit =
    (List(alpha, beta, gamma) ++ cases).foreach(SourceFixtures.upsert(d, _, 4000))

  /** Only ever look at the synthetic rows — the test DB may hold other streams' fixtures. */
  private def mine(r: SearchResult): List[Hit] = r.hits.filter(_.source.citekey.startsWith("zzf_"))
  private def keys(r: SearchResult): List[String] = mine(r).map(_.source.citekey)
  private def find(r: SearchResult, ck: String): Hit = mine(r).find(_.source.citekey == ck).get
  private val words = AppConfig.load().limits.snippetWords
  private def run(f: Filter) =
    SearchFixtures.sources(ds, f.copy(limit = if f.limit <= 0 then 50 else f.limit), words)
  // scoped to this suite's fixtures (the test DB is shared with other suites' rows)
  private val own = Filter(places = List("Levant", "Pontic", "Oceania"))
  private def facetMap(field: Facet.Field, prefix: Option[String] = None, f: Filter = own) =
    LabelFixtures.facets(ds, field, prefix, f.copy(limit = 200), 200).map(x => x.value -> x.count).toMap

  test("acceptance: --tag sea-peoples --from -1300 --to -1100 returns sources WITH matched findings"):
    val r = run(Filter(tags = List("sea-peoples"), period = Some(Period(-1300, -1100))))
    assertEquals(keys(r), List("zzf_alpha")) // gamma has the tag but no finding in that window
    val hit = find(r, "zzf_alpha")
    assertEquals(hit.matched.map(_.id), List("zzf_alpha#0"))
    assertEquals(hit.matched.head.why, List("period"))
    assertEquals(hit.matched.head.regionName, Some("Ugarit"))
    assert(hit.why.contains("tag: sea-peoples"), hit.why.toString)
    assert(hit.why.contains("period: 1300 BCE–1100 BCE"), hit.why.toString)

  test("a source matches a period through its findings even when itself undated"):
    val r = run(Filter(period = Some(Period(-1000, -950))))
    assertEquals(keys(r), List("zzf_gamma"))
    assertEquals(find(r, "zzf_gamma").matched.size, 2)
    assertEquals(find(r, "zzf_gamma").why, Nil) // no source-level reason: the findings are the reason

  test("tags are AND semantics over sources.tags ∪ findings.tags"):
    assertEquals(keys(run(Filter(tags = List("steppe")))), List("zzf_beta"))
    assertEquals(keys(run(Filter(tags = List("steppe", "migration")))), List("zzf_beta"))
    assertEquals(keys(run(Filter(tags = List("steppe", "sea-peoples")))), Nil)
    // "destruction" exists only on a finding — the source still matches
    assertEquals(keys(run(Filter(tags = List("destruction")))), List("zzf_alpha"))

  test("authors are any-of and case-insensitive substrings"):
    assertEquals(keys(run(Filter(authors = List("cline")))), List("zzf_alpha"))
    assertEquals(keys(run(Filter(authors = List("cline", "anthony")))).sorted, List("zzf_alpha", "zzf_beta"))
    assertEquals(find(run(Filter(authors = List("cline"))), "zzf_alpha").why, List("author: Eric H. Cline"))
    // the row carries its authors, for a table of sources
    assertEquals(find(run(Filter(authors = List("cline"))), "zzf_alpha").source.authors, List("Eric H. Cline"))

  test("places match sources.regions ∪ findings.region_name"):
    assertEquals(keys(run(Filter(places = List("Levant")))), List("zzf_alpha"))
    val r = run(Filter(places = List("cyprus"))) // only a finding carries this name
    assertEquals(keys(r), List("zzf_alpha"))
    assertEquals(find(r, "zzf_alpha").matched.map(_.id), List("zzf_alpha#1"))

  test("bbox matches through findings; an antimeridian box catches both sides of the line"):
    val split = run(Filter(bbox = Some(BBox(170.0, -20.0, -170.0, -5.0))))
    assertEquals(keys(split), List("zzf_gamma"))
    assertEquals(find(split, "zzf_gamma").matched.map(_.id).sorted, List("zzf_gamma#0", "zzf_gamma#1"))
    val west = run(Filter(bbox = Some(BBox(170.0, -20.0, 180.0, -5.0))))
    assertEquals(find(west, "zzf_gamma").matched.map(_.id), List("zzf_gamma#0"))
    assertEquals(keys(run(Filter(bbox = Some(BBox(30.0, 30.0, 40.0, 40.0))))), List("zzf_alpha"))

  test("free text matches title, summary or body; why says which"):
    val r = run(Filter(q = Some("Steppe horses")))
    assertEquals(keys(r), List("zzf_beta"))
    assert(find(r, "zzf_beta").why.contains("title matches 'Steppe horses'"))
    assertEquals(find(r, "zzf_beta").matched, Nil) // q is not a finding-level criterion

  test("status and publication-year filters"):
    assertEquals(keys(run(Filter(status = List("foundational")))), List("zzf_beta"))
    assertEquals(keys(run(Filter(years = Some(Period(2010, 2016))))), List("zzf_alpha"))

  test("total is count(DISTINCT source) and ignores limit/offset"):
    val f = Filter(places = List("Levant", "Pontic", "Oceania"))
    assertEquals(run(f).total, 3)
    val page = SearchFixtures.sources(ds, f.copy(limit = 1, sort = Sort.Title), words)
    assertEquals(page.total, 3)
    assertEquals(page.hits.size, 1)
    val next = SearchFixtures.sources(ds, f.copy(limit = 1, offset = 1, sort = Sort.Title), words)
    assertEquals(next.total, 3)
    assertNotEquals(page.hits.head.source.citekey, next.hits.head.source.citekey)

  test("sorting: by publication year, newest or oldest first"):
    val f = Filter(places = List("Levant", "Pontic", "Oceania"))
    assertEquals(
      keys(SearchFixtures.sources(ds, f.copy(limit = 50, sort = Sort.Year), words)),
      List("zzf_gamma", "zzf_alpha", "zzf_beta")
    )
    assertEquals(
      keys(SearchFixtures.sources(ds, f.copy(limit = 50, sort = Sort.Oldest), words)),
      List("zzf_beta", "zzf_alpha", "zzf_gamma")
    )

  test("sorting: by title, by citations, by period start"):
    val f = Filter(places = List("Levant", "Pontic", "Oceania"))
    assertEquals(
      keys(SearchFixtures.sources(ds, f.copy(limit = 50, sort = Sort.Title), words)),
      List("zzf_alpha", "zzf_gamma", "zzf_beta")
    )
    assertEquals(
      keys(SearchFixtures.sources(ds, f.copy(limit = 50, sort = Sort.Cited), words)),
      List("zzf_beta", "zzf_alpha", "zzf_gamma")
    )
    // undated sources sort last on period
    assertEquals(
      keys(SearchFixtures.sources(ds, f.copy(limit = 50, sort = Sort.PeriodStart), words)),
      List("zzf_beta", "zzf_alpha", "zzf_gamma")
    )

  test("facets count DISTINCT sources, not findings"):
    val tags = facetMap(Facet.Field.Tags)
    assertEquals(tags.get("sea-peoples"), Some(2)) // alpha + gamma
    assertEquals(tags.get("island"), Some(1)) // two findings, ONE source
    assertEquals(tags.get("steppe"), Some(1)) // source tag and finding tag, one source
    assertEquals(tags.get("destruction"), Some(1))

  test("facets are computed inside the current filter"):
    val tags = facetMap(Facet.Field.Tags, f = Filter(period = Some(Period(-1300, -1100))))
    assertEquals(tags.get("sea-peoples"), Some(1))
    assertEquals(tags.get("island"), None)

  test("facet prefix is a case-insensitive prefix, not a substring"):
    assertEquals(facetMap(Facet.Field.Places, Some("pon")).get("Pontic Steppe"), Some(1))
    assertEquals(facetMap(Facet.Field.Places, Some("steppe")).get("Pontic Steppe"), None)

  test("places facet unions sources.regions with findings.region_name"):
    val places = facetMap(Facet.Field.Places)
    assertEquals(places.get("Levant"), Some(1))
    assertEquals(places.get("Ugarit"), Some(1))
    assertEquals(places.get("Fiji"), Some(1))

  test("author, topic and status facets"):
    assertEquals(facetMap(Facet.Field.Authors).get("David W. Anthony"), Some(1))
    assertEquals(facetMap(Facet.Field.Topics).get("collapse"), Some(1))
    assertEquals(facetMap(Facet.Field.Status).get("foundational"), Some(1))

  test("facets are ordered by count desc, then value"):
    val rows = LabelFixtures
      .facets(ds, Facet.Field.Tags, None, Filter(limit = 200), 200)
      .filter(f => Set("sea-peoples", "destruction", "island")(f.value))
    assertEquals(rows.map(_.value).toList, List("sea-peoples", "destruction", "island"))

  test("an empty filter returns everything, with no evidence and no reasons"):
    val r = run(Filter())
    assertEquals(keys(r).sorted, List("zzf_alpha", "zzf_beta", "zzf_gamma"))
    assert(mine(r).forall(h => h.matched.isEmpty && h.why.isEmpty))

  test("LIKE wildcards in user input are literal"):
    assertEquals(keys(run(Filter(places = List("%")))), Nil)
    assertEquals(keys(run(Filter(q = Some("100%")))), Nil)

  // ── the map feed: `/api/findings` through the SAME filter (plan §1.0) ────────────────────────
  /** Findings of THIS suite's fixtures only (the test DB is shared with other suites' rows). */
  private def feed(f: Filter) =
    FindingFixtures
      .findingsFor(ds, f.copy(limit = if f.limit <= 0 then 200 else f.limit))
      .filter(_.citekey.startsWith("zzf_"))

  test("map feed: a wrapping bbox returns only findings east of 170 or west of -170"):
    val hits = feed(Filter(bbox = Some(BBox(170.0, -60.0, -170.0, 60.0))))
    assertEquals(hits.map(_.claim).toList.sorted, List("Lapita site west of the line", "Site east of the line"))
    assertEquals(hits.flatMap(_.lon).toList.sorted, List(-172.0, 178.0))
    // the Levantine/steppe findings (lon 35.78, 33.0, 40.0) are NOT in a box that wraps the Pacific
    assert(!hits.exists(_.citekey == "zzf_alpha"), hits.toString)

  test("map feed: tag= restricts it, over findings.tags ∪ sources.tags"):
    assertEquals(feed(Filter(tags = List("destruction"))).map(_.claim).toList, List("Ugarit destroyed"))
    // a SOURCE tag qualifies every finding of that source
    assertEquals(feed(Filter(tags = List("steppe"))).map(_.claim).toList, List("Yamnaya expansion"))
    assertEquals(
      feed(Filter(tags = List("sea-peoples"))).map(_.citekey).toList.distinct.sorted,
      List("zzf_alpha", "zzf_gamma")
    )
    assertEquals(feed(Filter(tags = List("no-such-tag"))).toList, Nil)

  test("map feed: q= restricts it to the findings of sources whose text matches"):
    assertEquals(feed(Filter(q = Some("Steppe horses"))).map(_.claim).toList, List("Yamnaya expansion"))
    assertEquals(feed(Filter(q = Some("Collapse of the Bronze Age"))).map(_.citekey).toList.distinct, List("zzf_alpha"))
    assertEquals(feed(Filter(q = Some("zzz no such text anywhere"))).toList, Nil)

  test("map feed: period and place are judged per FINDING, not per source"):
    // alpha itself is dated -1300…-1100, but only its Ugarit finding lies in this window
    assertEquals(feed(Filter(period = Some(Period(-1200, -1180)))).map(_.claim).toList, List("Ugarit destroyed"))
    assertEquals(feed(Filter(places = List("cyprus"))).map(_.claim).toList, List("Cypriot trade revives"))

  test("map feed: the limit is honoured"):
    assertEquals(FindingFixtures.findingsFor(ds, Filter(tags = List("sea-peoples"), limit = 1)).size, 1)

  // ── facets fold case (plan §1.3) ────────────────────────────────────────────────────────────
  private val caseScope = Filter(places = List("zzftolia"))
  private def casedKeys(r: SearchResult) =
    r.hits.map(_.source.citekey).filter(_.startsWith("zzc_")).sorted

  test("facets group values case-insensitively, labelled with the commonest spelling"):
    val places = facetMap(Facet.Field.Places, f = caseScope)
    assertEquals(places.get("Zzftolia"), Some(3))
    assertEquals(places.get("zzftolia"), None)
    val tags = facetMap(Facet.Field.Tags, f = caseScope)
    assertEquals(tags.get("Zzftag"), Some(3))
    assertEquals(tags.get("zzftag"), None)

  test("a facet prefix stays case-insensitive over the folded bucket"):
    assertEquals(facetMap(Facet.Field.Places, Some("ZZFT"), caseScope).get("Zzftolia"), Some(3))
    assertEquals(facetMap(Facet.Field.Places, Some("zzft"), caseScope).get("Zzftolia"), Some(3))

  test("tags and places match case-insensitively, so either spelling finds the whole bucket"):
    val all = List("zzc_one", "zzc_three", "zzc_two")
    assertEquals(casedKeys(run(Filter(tags = List("zzftag")))), all)
    assertEquals(casedKeys(run(Filter(tags = List("ZZFTAG")))), all)
    assertEquals(casedKeys(run(Filter(places = List("ZZFTOLIA")))), all)
