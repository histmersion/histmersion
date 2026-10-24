package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** `periods` against a real Postgres/PostGIS (HISTMERSION_TEST_DB_URL); skipped otherwise.
  * The bucketing itself is pure and covered by `core/AggregateSuite`. */
class AggregateRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val ds =
    val base = AppConfig.load().db
    val d    = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  /** A source dated −1300…−1200 with one Anatolian finding at −1250 (the §1.7 acceptance shape). */
  private val dated = Source(
    citekey = "zz_agg_dated", title = "Dated", authors = List("A"), doi = None, year = Some(2020),
    venue = None, status = SourceStatus.Current, topics = Nil, regions = List("Anatolia"), tags = Nil,
    period = Some(Period(-1300, -1200)), summary = None, citedByCount = None, influentialCitations = None,
    provenance = None, calibreId = None,
    findings = List(Finding("zz_agg_dated#0", "zz_agg_dated", "c", Some("Anatolia"),
      Some(GeoPoint(32.0, 39.0)), Some(Period(-1250, -1250)), None, Nil, None)))

  /** No source-level period, but a dated finding → the finding envelope stands in for it. */
  private val undatedSourceDatedFinding = Source(
    citekey = "zz_agg_findingonly", title = "Finding only", authors = Nil, doi = None, year = None,
    venue = None, status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil,
    period = None, summary = None, citedByCount = None, influentialCitations = None,
    provenance = None, calibreId = None,
    findings = List(Finding("zz_agg_findingonly#0", "zz_agg_findingonly", "c", Some("Aegean"),
      Some(GeoPoint(23.0, 38.0)), Some(Period(-1500, -1400)), None, Nil, None)))

  /** Nothing dated at all → must never appear in the histogram (plan §1.7). */
  private val undated = Source(
    citekey = "zz_agg_undated", title = "Undated", authors = Nil, doi = None, year = None,
    venue = None, status = SourceStatus.Current, topics = Nil, regions = Nil, tags = Nil,
    period = None, summary = None, citedByCount = None, influentialCitations = None,
    provenance = None, calibreId = None, findings = Nil)

  /** Tagged, in its own century, so a `tag=` filter can be shown to narrow the histogram. */
  private val tagged = Source(
    citekey = "zz_agg_tagged", title = "Tagged", authors = Nil, doi = None, year = None,
    venue = None, status = SourceStatus.Current, topics = Nil, regions = Nil, tags = List("zzaggtag"),
    period = Some(Period(-900, -800)), summary = None, citedByCount = None, influentialCitations = None,
    provenance = None, calibreId = None,
    findings = List(Finding("zz_agg_tagged#0", "zz_agg_tagged", "c", Some("Aegean"),
      Some(GeoPoint(23.0, 38.0)), Some(Period(-900, -800)), None, Nil, None)))

  private def seed(): Unit =
    List(dated, undatedSourceDatedFinding, undated, tagged).foreach(SourceRepo.upsert(ds, _, 4000))

  /** The window to bucket travels in the filter's `period` (as the handler sets it). */
  private def win(from: Long, to: Long, f: Filter = Filter()): Filter = f.copy(period = Some(Period(from, to)))

  test("unit=sources: one row per matching source, undated sources dropped from the histogram"):
    seed()
    val rows = AggregateRepo.periods(ds, "sources", win(-2000, -1000))
    val hist = Aggregate.histogram(rows.toList, -2000, -1000, 100)
    // a source with neither its own period nor a dated finding cannot overlap the window, so the
    // window predicate already drops it in SQL (`histogram` would ignore it anyway — belt and braces)
    assert(!rows.contains(PeriodRow(None, None)), rows.toString)
    assertEquals(hist.find(_.start == -1300).map(_.count > 0), Some(true))
    // the source with only a dated finding is bucketed by the finding envelope
    assertEquals(hist.find(_.start == -1500).map(_.count > 0), Some(true))

  test("unit=sources: a source is in the window when its OWN period overlaps it"):
    seed()
    val in  = AggregateRepo.periods(ds, "sources", win(-1250, -1250))
    val out = AggregateRepo.periods(ds, "sources", win(-500, -400))
    assert(in.contains(PeriodRow(Some(-1300), Some(-1200))))
    assert(!out.contains(PeriodRow(Some(-1300), Some(-1200))))

  test("unit=sources: a source is in the window when only a FINDING overlaps it"):
    seed()
    val rows = AggregateRepo.periods(ds, "sources", win(-1450, -1440))
    assert(rows.contains(PeriodRow(Some(-1500), Some(-1400))), rows.toString)

  test("region and bbox filter as geoWhen does (finding-level)"):
    seed()
    val anatolia = AggregateRepo.periods(ds, "sources", win(-2000, -1000, Filter(places = List("anatol"))))
    assert(anatolia.contains(PeriodRow(Some(-1300), Some(-1200))))
    assert(!anatolia.contains(PeriodRow(Some(-1500), Some(-1400))))
    val bbox = AggregateRepo.periods(ds, "sources", win(-2000, -1000, Filter(bbox = Some(BBox(30.0, 38.0, 34.0, 40.0)))))
    assert(bbox.contains(PeriodRow(Some(-1300), Some(-1200))))
    assert(!bbox.contains(PeriodRow(Some(-1500), Some(-1400))))
    // place filters are finding-level, so a source with no findings at all cannot match
    assert(!anatolia.contains(PeriodRow(None, None)))

  test("unit=findings: one row per finding, its own period"):
    seed()
    val rows = AggregateRepo.periods(ds, "findings", win(-2000, -1000))
    assert(rows.contains(PeriodRow(Some(-1250), Some(-1250))))
    assert(rows.contains(PeriodRow(Some(-1500), Some(-1400))))
    // and the source-level row for zz_agg_dated (-1300..-1200) is NOT there
    assert(!rows.contains(PeriodRow(Some(-1300), Some(-1200))))

  test("a source dated -1250 lands only in buckets covering -1250"):
    seed()
    val rows = AggregateRepo.periods(ds, "findings", win(-1600, -1200, Filter(places = List("anatol"))))
    val hist = Aggregate.histogram(rows.toList, -1600, -1200, 100)
    assertEquals(hist.filter(_.count > 0).map(b => (b.start, b.end)), List((-1300L, -1201L)))

  test("tag= narrows the histogram (the same filter as /api/sources and /api/findings)"):
    seed()
    val all    = AggregateRepo.periods(ds, "sources", win(-2000, -700))
    val tagged = AggregateRepo.periods(ds, "sources", win(-2000, -700, Filter(tags = List("zzaggtag"))))
    assert(all.contains(PeriodRow(Some(-1300), Some(-1200))), all.toString)
    assert(all.contains(PeriodRow(Some(-900), Some(-800))), all.toString)
    // only the tagged source survives — and its bucket is the only non-empty one
    assert(tagged.contains(PeriodRow(Some(-900), Some(-800))), tagged.toString)
    assert(!tagged.contains(PeriodRow(Some(-1300), Some(-1200))), tagged.toString)
    val hist = Aggregate.histogram(tagged.toList, -2000, -700, 100)
    assertEquals(hist.filter(_.count > 0).map(_.start), List(-900L, -800L))
