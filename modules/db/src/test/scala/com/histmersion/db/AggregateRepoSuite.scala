package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.api.{BBox, GeoPoint}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{Latitude, Longitude, Period, Year}
import com.histmersion.api.library.{Aggregate, Filter, Finding, Source}
import com.histmersion.util.database.given
import com.histmersion.api.library.Citekey
import com.histmersion.config.Limits

/** `periods` against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise. The bucketing itself is pure
  * and covered by `core/AggregateSuite`.
  */
class AggregateRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  /** A source dated −1300…−1200 with one Anatolian finding at −1250 (the §1.7 acceptance shape). */
  private val dated = Source(
    citekey = Citekey("zz_agg_dated"),
    title = api.library.Title("Dated"),
    authors = (List("A")).map(api.library.AuthorName.apply),
    doi = None,
    year = (Some(2020)).map(Year.apply),
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = List(Source.Regions("Anatolia")),
    tags = Nil,
    period = Some(Period(Year(-1300), Year(-1200))),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        Finding.Id("zz_agg_dated#0"),
        Citekey("zz_agg_dated"),
        Finding.Claim("c"),
        Some(Finding.Region("Anatolia")),
        Some(GeoPoint(Longitude(32.0), Latitude(39.0))),
        Some(Period(Year(-1250), Year(-1250))),
        None,
        Nil,
        None
      )
    )
  )

  /** No source-level period, but a dated finding → the finding envelope stands in for it. */
  private val undatedSourceDatedFinding = Source(
    citekey = Citekey("zz_agg_findingonly"),
    title = api.library.Title("Finding only"),
    authors = (Nil).map(api.library.AuthorName.apply),
    doi = None,
    year = None,
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        Finding.Id("zz_agg_findingonly#0"),
        Citekey("zz_agg_findingonly"),
        Finding.Claim("c"),
        Some(Finding.Region("Aegean")),
        Some(GeoPoint(Longitude(23.0), Latitude(38.0))),
        Some(Period(Year(-1500), Year(-1400))),
        None,
        Nil,
        None
      )
    )
  )

  /** Nothing dated at all → must never appear in the histogram (plan §1.7). */
  private val undated = Source(
    citekey = Citekey("zz_agg_undated"),
    title = api.library.Title("Undated"),
    authors = (Nil).map(api.library.AuthorName.apply),
    doi = None,
    year = None,
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = Nil,
    period = None,
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = Nil
  )

  /** Tagged, in its own century, so a `tag=` filter can be shown to narrow the histogram. */
  private val tagged = Source(
    citekey = Citekey("zz_agg_tagged"),
    title = api.library.Title("Tagged"),
    authors = (Nil).map(api.library.AuthorName.apply),
    doi = None,
    year = None,
    venue = None,
    status = Source.Status.Current,
    topics = Nil,
    regions = Nil,
    tags = List(Source.Tags("zzaggtag")),
    period = Some(Period(Year(-900), Year(-800))),
    summary = None,
    citedByCount = None,
    influentialCitations = None,
    provenance = None,
    calibreId = None,
    findings = List(
      Finding(
        Finding.Id("zz_agg_tagged#0"),
        Citekey("zz_agg_tagged"),
        Finding.Claim("c"),
        Some(Finding.Region("Aegean")),
        Some(GeoPoint(Longitude(23.0), Latitude(38.0))),
        Some(Period(Year(-900), Year(-800))),
        None,
        Nil,
        None
      )
    )
  )

  private def seed(): Unit =
    List(dated, undatedSourceDatedFinding, undated, tagged).foreach(
      SourceFixtures.upsert(ds, _, Limits.ChunkChars(4000))
    )

  /** The window to bucket travels in the filter's `period` (as the handler sets it). */
  private def win(from: Year, to: Year, f: Filter = Filter()): Filter = f.modify(_.period).setTo(Some(Period(from, to)))

  test("unit=sources: one row per matching source, undated sources dropped from the histogram"):
    seed()
    val rows = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("sources"), win(Year(-2000), Year(-1000)))
    val hist = Aggregate.histogram(rows.toList, Year(-2000), Year(-1000), Aggregate.Width(100))
    // a source with neither its own period nor a dated finding cannot overlap the window, so the
    // window predicate already drops it in SQL (`histogram` would ignore it anyway — belt and braces)
    assert(!rows.contains(Period.Row(None, None)), rows.toString)
    assertEquals(hist.find(_.start.value == -1300).map(_.count.value > 0), Some(true))
    // the source with only a dated finding is bucketed by the finding envelope
    assertEquals(hist.find(_.start.value == -1500).map(_.count.value > 0), Some(true))

  test("unit=sources: a source is in the window when its OWN period overlaps it"):
    seed()
    val in = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("sources"), win(Year(-1250), Year(-1250)))
    val out = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("sources"), win(Year(-500), Year(-400)))
    assert(in.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))))
    assert(!out.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))))

  test("unit=sources: a source is in the window when only a FINDING overlaps it"):
    seed()
    val rows = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("sources"), win(Year(-1450), Year(-1440)))
    assert(rows.contains(Period.Row(Some(Year(-1500)), Some(Year(-1400)))), rows.toString)

  test("region and bbox filter as geoWhen does (finding-level)"):
    seed()
    val anatolia = SearchFixtures.periods(
      ds,
      SearchFixtures.AggregateUnit("sources"),
      win(Year(-2000), Year(-1000), Filter(places = List(Filter.Places("anatol"))))
    )
    assert(anatolia.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))))
    assert(!anatolia.contains(Period.Row(Some(Year(-1500)), Some(Year(-1400)))))
    val bbox =
      SearchFixtures.periods(
        ds,
        SearchFixtures.AggregateUnit("sources"),
        win(
          Year(-2000),
          Year(-1000),
          Filter(bbox = Some(BBox(Longitude(30.0), Latitude(38.0), Longitude(34.0), Latitude(40.0))))
        )
      )
    assert(bbox.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))))
    assert(!bbox.contains(Period.Row(Some(Year(-1500)), Some(Year(-1400)))))
    // place filters are finding-level, so a source with no findings at all cannot match
    assert(!anatolia.contains(Period.Row(None, None)))

  test("unit=findings: one row per finding, its own period"):
    seed()
    val rows = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("findings"), win(Year(-2000), Year(-1000)))
    assert(rows.contains(Period.Row(Some(Year(-1250)), Some(Year(-1250)))))
    assert(rows.contains(Period.Row(Some(Year(-1500)), Some(Year(-1400)))))
    // and the source-level row for zz_agg_dated (-1300..-1200) is NOT there
    assert(!rows.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))))

  test("a source dated -1250 lands only in buckets covering -1250"):
    seed()
    val rows = SearchFixtures.periods(
      ds,
      SearchFixtures.AggregateUnit("findings"),
      win(Year(-1600), Year(-1200), Filter(places = List(Filter.Places("anatol"))))
    )
    val hist = Aggregate.histogram(rows.toList, Year(-1600), Year(-1200), Aggregate.Width(100))
    assertEquals(hist.filter(_.count.value > 0).map(b => (b.start, b.end)), List((-1300L, -1201L)))

  test("tag= narrows the histogram (the same filter as /api/sources and /api/findings)"):
    seed()
    val all = SearchFixtures.periods(ds, SearchFixtures.AggregateUnit("sources"), win(Year(-2000), Year(-700)))
    val tagged = SearchFixtures.periods(
      ds,
      SearchFixtures.AggregateUnit("sources"),
      win(Year(-2000), Year(-700), Filter(tags = List(Filter.Tags("zzaggtag"))))
    )
    assert(all.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))), all.toString)
    assert(all.contains(Period.Row(Some(Year(-900)), Some(Year(-800)))), all.toString)
    // only the tagged source survives — and its bucket is the only non-empty one
    assert(tagged.contains(Period.Row(Some(Year(-900)), Some(Year(-800)))), tagged.toString)
    assert(!tagged.contains(Period.Row(Some(Year(-1300)), Some(Year(-1200)))), tagged.toString)
    val hist = Aggregate.histogram(tagged.toList, Year(-2000), Year(-700), Aggregate.Width(100))
    assertEquals(hist.filter(_.count.value > 0).map(_.start), List(-900L, -800L))
