package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.findings.FindingFixtures
import com.histmersion.domain.library.labels.LabelFixtures
import com.histmersion.domain.library.search.{FilterSql, SearchFixtures}
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.{BBox, GeoPoint}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Confidence
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Latitude, Longitude, Period, Year}
import com.histmersion.api.library.{Filter, Finding, Hit, SearchResult, Sort, Source}
import com.histmersion.api.library.Facet
import com.histmersion.api.library.{AuthorName, Citekey, Title}
import com.histmersion.util.database.given

/** `sources(filter)` / `facets(...)` against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise.
  * Three synthetic sources exercise the plan §1.0 rules: the SOURCE is the unit, a source matches through its own
  * metadata OR its findings, and every count is DISTINCT sources.
  */
class FilterRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(testDb.get))
    Migrate.run(d)
    seed(d)
    d

  private def finding(
      ck: com.histmersion.api.library.Citekey,
      n: Int,
      claim: Finding.Claim,
      region: Finding.Region,
      lon: Longitude,
      lat: Latitude,
      from: Year,
      to: Year,
      tags: List[Finding.Tags]
  ) =
    Finding(
      Finding.Id(s"$ck#$n"),
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
      ck: com.histmersion.api.library.Citekey,
      title: com.histmersion.api.library.Title,
      authors: List[com.histmersion.api.library.AuthorName],
      year: Year,
      status: com.histmersion.api.library.Source.Status,
      topics: List[Source.Topics],
      regions: List[Source.Regions],
      tags: List[Source.Tags],
      period: Option[Period],
      cited: Source.CitedByCount,
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
      Some(Source.Summary(s"summary of $title")),
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
    Citekey("zzf_alpha"),
    Title("Collapse of the Bronze Age"),
    List(AuthorName("Eric H. Cline")),
    Year(2014),
    com.histmersion.api.library.Source.Status.Current,
    List(Source.Topics("collapse")),
    List(Source.Regions("Levant")),
    List(Source.Tags("sea-peoples")),
    Some(Period(com.histmersion.api.catalogue.Year(-1300), com.histmersion.api.catalogue.Year(-1100))),
    Source.CitedByCount(100),
    List(
      finding(
        Citekey("zzf_alpha"),
        0,
        Finding.Claim("Ugarit destroyed"),
        Finding.Region("Ugarit"),
        Longitude(35.78),
        Latitude(35.60),
        Year(-1200),
        Year(-1180),
        List(Finding.Tags("destruction"))
      ),
      finding(
        Citekey("zzf_alpha"),
        1,
        Finding.Claim("Cypriot trade revives"),
        Finding.Region("Cyprus"),
        Longitude(33.00),
        Latitude(35.00),
        Year(-800),
        Year(-700),
        List(Finding.Tags("trade"))
      )
    )
  )

  // B: steppe, far older, no overlap with A.
  private val beta = source(
    Citekey("zzf_beta"),
    Title("Steppe horses"),
    List(AuthorName("David W. Anthony")),
    Year(2007),
    com.histmersion.api.library.Source.Status.Foundational,
    List(Source.Topics("migration")),
    List(Source.Regions("Pontic Steppe")),
    List(Source.Tags("steppe")),
    Some(Period(com.histmersion.api.catalogue.Year(-4000), com.histmersion.api.catalogue.Year(-3000))),
    Source.CitedByCount(500),
    List(
      finding(
        Citekey("zzf_beta"),
        0,
        Finding.Claim("Yamnaya expansion"),
        Finding.Region("Pontic Steppe"),
        Longitude(40.0),
        Latitude(47.0),
        Year(-3300),
        Year(-2600),
        List(Finding.Tags("steppe"), Finding.Tags("migration"))
      )
    )
  )

  // C: UNDATED source whose findings straddle the antimeridian — matches only through them.
  private val gamma = source(
    Citekey("zzf_gamma"),
    Title("Pacific island chronology"),
    List(AuthorName("Pacific Author")),
    Year(2020),
    com.histmersion.api.library.Source.Status.Unassessed,
    List(Source.Topics("chronology")),
    List(Source.Regions("Oceania")),
    List(Source.Tags("sea-peoples")),
    None,
    Source.CitedByCount(5),
    List(
      finding(
        Citekey("zzf_gamma"),
        0,
        Finding.Claim("Lapita site west of the line"),
        Finding.Region("Fiji"),
        Longitude(178.0),
        Latitude(-17.7),
        Year(-1000),
        Year(-900),
        List(Finding.Tags("island"))
      ),
      finding(
        Citekey("zzf_gamma"),
        1,
        Finding.Claim("Site east of the line"),
        Finding.Region("Samoa"),
        Longitude(-172.0),
        Latitude(-13.8),
        Year(-1000),
        Year(-900),
        List(Finding.Tags("island"))
      )
    )
  )

  // Case-fold fixtures (plan §1.3): the same vocabulary spelled two ways. Prefixed `zzc_` so the
  // `zzf_` assertions are untouched, and the words are unique to this suite so the shared test DB
  // cannot pollute the counts. "Zzftolia" is on two sources, "zzftolia" on one → it is the label.
  private def cased(ck: Citekey, region: Source.Regions, tag: Source.Tags) =
    source(
      ck,
      Title(s"Case study $ck"),
      List(AuthorName("Case Author")),
      Year(2021),
      com.histmersion.api.library.Source.Status.Current,
      Nil,
      List(region),
      List(tag),
      None,
      Source.CitedByCount(0),
      List(
        finding(
          ck,
          0,
          Finding.Claim(s"cased finding $ck"),
          Finding.Region(region.value),
          Longitude(1.0),
          Latitude(1.0),
          Year(-500),
          Year(-400),
          List(Finding.Tags(tag.value))
        )
      )
    )

  private val cases =
    List(
      cased(Citekey("zzc_one"), Source.Regions("Zzftolia"), Source.Tags("Zzftag")),
      cased(Citekey("zzc_two"), Source.Regions("Zzftolia"), Source.Tags("Zzftag")),
      cased(Citekey("zzc_three"), Source.Regions("zzftolia"), Source.Tags("zzftag"))
    )

  private def seed(d: javax.sql.DataSource): Unit =
    (List(alpha, beta, gamma) ++ cases).foreach(SourceFixtures.upsert(d, _, Limits.ChunkChars(4000)))

  /** Only ever look at the synthetic rows — the test DB may hold other streams' fixtures. */
  private def mine(r: SearchResult): List[Hit] = r.hits.filter(_.source.citekey.value.startsWith("zzf_"))
  private def keys(r: SearchResult): List[String] = mine(r).map(_.source.citekey.value)
  private def find(r: SearchResult, ck: String): Hit = mine(r).find(_.source.citekey.value == ck).get
  private val words = com.histmersion.domain.library.search.Search.SnippetWords(AppConfig.load().limits.snippetWords.value)
  private def run(f: Filter) =
    SearchFixtures.sources(
      ds,
      f.modify(_.limit).setTo(Filter.Limit(if f.limit.value <= 0 then 50 else f.limit.value)),
      words
    )
  // scoped to this suite's fixtures (the test DB is shared with other suites' rows)
  private val own = Filter(places = List(com.histmersion.api.library.Filter.Places("Levant"), com.histmersion.api.library.Filter.Places("Pontic"), com.histmersion.api.library.Filter.Places("Oceania")))
  private def facetMap(field: Facet.Field, prefix: Option[FilterSql.FacetPrefix] = None, f: Filter = own) =
    LabelFixtures
      .facets(ds, field, prefix, f.modify(_.limit).setTo(Filter.Limit(200)), com.histmersion.PageSize(200))
      .map(x => x.value -> x.count)
      .toMap

  test("acceptance: --tag sea-peoples --from -1300 --to -1100 returns sources WITH matched findings"):
    val r = run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples")), period = Some(Period(com.histmersion.api.catalogue.Year(-1300), com.histmersion.api.catalogue.Year(-1100)))))
    assertEquals(keys(r), List("zzf_alpha")) // gamma has the tag but no finding in that window
    val hit = find(r, "zzf_alpha")
    assertEquals(hit.matched.map(_.id), List("zzf_alpha#0"))
    assertEquals(hit.matched.head.why, List("period"))
    assertEquals(hit.matched.head.regionName, Some("Ugarit"))
    assert(hit.why.contains("tag: sea-peoples"), hit.why.toString)
    assert(hit.why.contains("period: 1300 BCE–1100 BCE"), hit.why.toString)

  test("a source matches a period through its findings even when itself undated"):
    val r = run(Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.catalogue.Year(-950)))))
    assertEquals(keys(r), List("zzf_gamma"))
    assertEquals(find(r, "zzf_gamma").matched.size, 2)
    assertEquals(find(r, "zzf_gamma").why, Nil) // no source-level reason: the findings are the reason

  test("tags are AND semantics over sources.tags ∪ findings.tags"):
    assertEquals(keys(run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("steppe"))))), List("zzf_beta"))
    assertEquals(keys(run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("steppe"), com.histmersion.api.library.Filter.Tags("migration"))))), List("zzf_beta"))
    assertEquals(keys(run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("steppe"), com.histmersion.api.library.Filter.Tags("sea-peoples"))))), Nil)
    // "destruction" exists only on a finding — the source still matches
    assertEquals(keys(run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("destruction"))))), List("zzf_alpha"))

  test("authors are any-of and case-insensitive substrings"):
    assertEquals(keys(run(Filter(authors = List(com.histmersion.api.library.Filter.Authors("cline"))))), List("zzf_alpha"))
    assertEquals(keys(run(Filter(authors = List(com.histmersion.api.library.Filter.Authors("cline"), com.histmersion.api.library.Filter.Authors("anthony"))))).sorted, List("zzf_alpha", "zzf_beta"))
    assertEquals(find(run(Filter(authors = List(com.histmersion.api.library.Filter.Authors("cline")))), "zzf_alpha").why, List("author: Eric H. Cline"))
    // the row carries its authors, for a table of sources
    assertEquals(find(run(Filter(authors = List(com.histmersion.api.library.Filter.Authors("cline")))), "zzf_alpha").source.authors, List("Eric H. Cline"))

  test("places match sources.regions ∪ findings.region_name"):
    assertEquals(keys(run(Filter(places = List(com.histmersion.api.library.Filter.Places("Levant"))))), List("zzf_alpha"))
    val r = run(Filter(places = List(com.histmersion.api.library.Filter.Places("cyprus")))) // only a finding carries this name
    assertEquals(keys(r), List("zzf_alpha"))
    assertEquals(find(r, "zzf_alpha").matched.map(_.id), List("zzf_alpha#1"))

  test("bbox matches through findings; an antimeridian box catches both sides of the line"):
    val split = run(Filter(bbox = Some(BBox(Longitude(170.0), Latitude(-20.0), Longitude(-170.0), Latitude(-5.0)))))
    assertEquals(keys(split), List("zzf_gamma"))
    assertEquals(find(split, "zzf_gamma").matched.map(_.id.value).sorted, List("zzf_gamma#0", "zzf_gamma#1"))
    val west = run(Filter(bbox = Some(BBox(Longitude(170.0), Latitude(-20.0), Longitude(180.0), Latitude(-5.0)))))
    assertEquals(find(west, "zzf_gamma").matched.map(_.id), List("zzf_gamma#0"))
    assertEquals(keys(run(Filter(bbox = Some(BBox(Longitude(30.0), Latitude(30.0), Longitude(40.0), Latitude(40.0)))))), List("zzf_alpha"))

  test("free text matches title, summary or body; why says which"):
    val r = run(Filter(q = Some(com.histmersion.api.library.Filter.Q("Steppe horses"))))
    assertEquals(keys(r), List("zzf_beta"))
    assert(find(r, "zzf_beta").why.contains("title matches 'Steppe horses'"))
    assertEquals(find(r, "zzf_beta").matched, Nil) // q is not a finding-level criterion

  test("status and publication-year filters"):
    assertEquals(keys(run(Filter(status = List(com.histmersion.api.library.Filter.Status("foundational"))))), List("zzf_beta"))
    assertEquals(keys(run(Filter(years = Some(Period(com.histmersion.api.catalogue.Year(2010), com.histmersion.api.catalogue.Year(2016)))))), List("zzf_alpha"))

  test("total is count(DISTINCT source) and ignores limit/offset"):
    val f = Filter(places = List(com.histmersion.api.library.Filter.Places("Levant"), com.histmersion.api.library.Filter.Places("Pontic"), com.histmersion.api.library.Filter.Places("Oceania")))
    assertEquals((run(f).total.value: Int), 3)
    val page = SearchFixtures.sources(ds, f.modify(_.limit).setTo(Filter.Limit(1)).modify(_.sort).setTo(Sort.Title), words)
    assertEquals((page.total.value: Int), 3)
    assertEquals(page.hits.size, 1)
    val next = SearchFixtures.sources(
      ds,
      f.modify(_.limit).setTo(Filter.Limit(1)).modify(_.offset).setTo(Filter.Offset(1)).modify(_.sort).setTo(Sort.Title),
      words
    )
    assertEquals((next.total.value: Int), 3)
    assertNotEquals(page.hits.head.source.citekey, next.hits.head.source.citekey)

  test("sorting: by publication year, newest or oldest first"):
    val f = Filter(places = List(com.histmersion.api.library.Filter.Places("Levant"), com.histmersion.api.library.Filter.Places("Pontic"), com.histmersion.api.library.Filter.Places("Oceania")))
    assertEquals(
      keys(SearchFixtures.sources(ds, f.modify(_.limit).setTo(Filter.Limit(50)).modify(_.sort).setTo(Sort.Year), words)),
      List("zzf_gamma", "zzf_alpha", "zzf_beta")
    )
    assertEquals(
      keys(SearchFixtures.sources(ds, f.modify(_.limit).setTo(Filter.Limit(50)).modify(_.sort).setTo(Sort.Oldest), words)),
      List("zzf_beta", "zzf_alpha", "zzf_gamma")
    )

  test("sorting: by title, by citations, by period start"):
    val f = Filter(places = List(com.histmersion.api.library.Filter.Places("Levant"), com.histmersion.api.library.Filter.Places("Pontic"), com.histmersion.api.library.Filter.Places("Oceania")))
    assertEquals(
      keys(SearchFixtures.sources(ds, f.modify(_.limit).setTo(Filter.Limit(50)).modify(_.sort).setTo(Sort.Title), words)),
      List("zzf_alpha", "zzf_gamma", "zzf_beta")
    )
    assertEquals(
      keys(SearchFixtures.sources(ds, f.modify(_.limit).setTo(Filter.Limit(50)).modify(_.sort).setTo(Sort.Cited), words)),
      List("zzf_beta", "zzf_alpha", "zzf_gamma")
    )
    // undated sources sort last on period
    assertEquals(
      keys(SearchFixtures.sources(ds, f.modify(_.limit).setTo(Filter.Limit(50)).modify(_.sort).setTo(Sort.PeriodStart), words)),
      List("zzf_beta", "zzf_alpha", "zzf_gamma")
    )

  test("facets count DISTINCT sources, not findings"):
    val tags = facetMap(Facet.Field.Tags)
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("sea-peoples")), Some(2)) // alpha + gamma
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("island")), Some(1)) // two findings, ONE source
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("steppe")), Some(1)) // source tag and finding tag, one source
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("destruction")), Some(1))

  test("facets are computed inside the current filter"):
    val tags = facetMap(Facet.Field.Tags, f = Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1300), com.histmersion.api.catalogue.Year(-1100)))))
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("sea-peoples")), Some(1))
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("island")), None)

  test("facet prefix is a case-insensitive prefix, not a substring"):
    assertEquals(facetMap(Facet.Field.Places, Some(FilterSql.FacetPrefix("pon"))).get(com.histmersion.api.library.Facet.Value("Pontic Steppe")), Some(1))
    assertEquals(facetMap(Facet.Field.Places, Some(FilterSql.FacetPrefix("steppe"))).get(com.histmersion.api.library.Facet.Value("Pontic Steppe")), None)

  test("places facet unions sources.regions with findings.region_name"):
    val places = facetMap(Facet.Field.Places)
    assertEquals(places.get(com.histmersion.api.library.Facet.Value("Levant")), Some(1))
    assertEquals(places.get(com.histmersion.api.library.Facet.Value("Ugarit")), Some(1))
    assertEquals(places.get(com.histmersion.api.library.Facet.Value("Fiji")), Some(1))

  test("author, topic and status facets"):
    assertEquals(facetMap(Facet.Field.Authors).get(com.histmersion.api.library.Facet.Value("David W. Anthony")), Some(1))
    assertEquals(facetMap(Facet.Field.Topics).get(com.histmersion.api.library.Facet.Value("collapse")), Some(1))
    assertEquals(facetMap(Facet.Field.Status).get(com.histmersion.api.library.Facet.Value("foundational")), Some(1))

  test("facets are ordered by count desc, then value"):
    val rows = LabelFixtures
      .facets(ds, Facet.Field.Tags, None, Filter(limit = com.histmersion.api.library.Filter.Limit(200)), com.histmersion.PageSize(200))
      .filter(f => Set("sea-peoples", "destruction", "island")(f.value.value))
    assertEquals(rows.map(_.value).toList, List("sea-peoples", "destruction", "island"))

  test("an empty filter returns everything, with no evidence and no reasons"):
    val r = run(Filter())
    assertEquals(keys(r).sorted, List("zzf_alpha", "zzf_beta", "zzf_gamma"))
    assert(mine(r).forall(h => h.matched.isEmpty && h.why.isEmpty))

  test("LIKE wildcards in user input are literal"):
    assertEquals(keys(run(Filter(places = List(com.histmersion.api.library.Filter.Places("%"))))), Nil)
    assertEquals(keys(run(Filter(q = Some(com.histmersion.api.library.Filter.Q("100%"))))), Nil)

  // ── the map feed: `/api/findings` through the SAME filter (plan §1.0) ────────────────────────
  /** Findings of THIS suite's fixtures only (the test DB is shared with other suites' rows). */
  private def feed(f: Filter) =
    FindingFixtures
      .findingsFor(ds, f.modify(_.limit).setTo(Filter.Limit(if f.limit.value <= 0 then 200 else f.limit.value)))
      .filter(_.citekey.value.startsWith("zzf_"))

  test("map feed: a wrapping bbox returns only findings east of 170 or west of -170"):
    val hits = feed(Filter(bbox = Some(BBox(Longitude(170.0), Latitude(-60.0), Longitude(-170.0), Latitude(60.0)))))
    assertEquals(hits.map(_.claim).toList.sortBy(_.value), List("Lapita site west of the line", "Site east of the line"))
    assertEquals(hits.flatMap(_.lon).toList.sortBy(_.value), List(-172.0, 178.0))
    // the Levantine/steppe findings (lon 35.78, 33.0, 40.0) are NOT in a box that wraps the Pacific
    assert(!hits.exists(_.citekey.value == "zzf_alpha"), hits.toString)

  test("map feed: tag= restricts it, over findings.tags ∪ sources.tags"):
    assertEquals(feed(Filter(tags = List(com.histmersion.api.library.Filter.Tags("destruction")))).map(_.claim).toList, List("Ugarit destroyed"))
    // a SOURCE tag qualifies every finding of that source
    assertEquals(feed(Filter(tags = List(com.histmersion.api.library.Filter.Tags("steppe")))).map(_.claim).toList, List("Yamnaya expansion"))
    assertEquals(
      feed(Filter(tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples")))).map(_.citekey.value).toList.distinct.sorted,
      List("zzf_alpha", "zzf_gamma")
    )
    assertEquals(feed(Filter(tags = List(com.histmersion.api.library.Filter.Tags("no-such-tag")))).toList, Nil)

  test("map feed: q= restricts it to the findings of sources whose text matches"):
    assertEquals(feed(Filter(q = Some(com.histmersion.api.library.Filter.Q("Steppe horses")))).map(_.claim).toList, List("Yamnaya expansion"))
    assertEquals(feed(Filter(q = Some(com.histmersion.api.library.Filter.Q("Collapse of the Bronze Age")))).map(_.citekey).toList.distinct, List("zzf_alpha"))
    assertEquals(feed(Filter(q = Some(com.histmersion.api.library.Filter.Q("zzz no such text anywhere")))).toList, Nil)

  test("map feed: period and place are judged per FINDING, not per source"):
    // alpha itself is dated -1300…-1100, but only its Ugarit finding lies in this window
    assertEquals(feed(Filter(period = Some(Period(com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.catalogue.Year(-1180))))).map(_.claim).toList, List("Ugarit destroyed"))
    assertEquals(feed(Filter(places = List(com.histmersion.api.library.Filter.Places("cyprus")))).map(_.claim).toList, List("Cypriot trade revives"))

  test("map feed: the limit is honoured"):
    assertEquals(FindingFixtures.findingsFor(ds, Filter(tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples")), limit = com.histmersion.api.library.Filter.Limit(1))).size, 1)

  // ── facets fold case (plan §1.3) ────────────────────────────────────────────────────────────
  private val caseScope = Filter(places = List(com.histmersion.api.library.Filter.Places("zzftolia")))
  private def casedKeys(r: SearchResult) =
    r.hits.map(_.source.citekey.value).filter(_.startsWith("zzc_")).sorted

  test("facets group values case-insensitively, labelled with the commonest spelling"):
    val places = facetMap(Facet.Field.Places, f = caseScope)
    assertEquals(places.get(com.histmersion.api.library.Facet.Value("Zzftolia")), Some(3))
    assertEquals(places.get(com.histmersion.api.library.Facet.Value("zzftolia")), None)
    val tags = facetMap(Facet.Field.Tags, f = caseScope)
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("Zzftag")), Some(3))
    assertEquals(tags.get(com.histmersion.api.library.Facet.Value("zzftag")), None)

  test("a facet prefix stays case-insensitive over the folded bucket"):
    assertEquals(facetMap(Facet.Field.Places, Some(FilterSql.FacetPrefix("ZZFT")), caseScope).get(com.histmersion.api.library.Facet.Value("Zzftolia")), Some(3))
    assertEquals(facetMap(Facet.Field.Places, Some(FilterSql.FacetPrefix("zzft")), caseScope).get(com.histmersion.api.library.Facet.Value("Zzftolia")), Some(3))

  test("tags and places match case-insensitively, so either spelling finds the whole bucket"):
    val all = List("zzc_one", "zzc_three", "zzc_two")
    assertEquals(casedKeys(run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("zzftag"))))), all)
    assertEquals(casedKeys(run(Filter(tags = List(com.histmersion.api.library.Filter.Tags("ZZFTAG"))))), all)
    assertEquals(casedKeys(run(Filter(places = List(com.histmersion.api.library.Filter.Places("ZZFTOLIA"))))), all)
