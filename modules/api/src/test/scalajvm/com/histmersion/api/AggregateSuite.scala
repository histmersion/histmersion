package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.{Aggregate, TimeHistogram}
import com.histmersion.api.catalogue.{Period, Year}

/** Pure tests for the timeline bucketing (plan §1.6 sizes, §1.7 strip). No DB involved. */
class AggregateSuite extends munit.FunSuite:

  private def row(a: Year, b: Year) = Period.Row(Some(a), Some(b))

  // ── bucket sizes, at the boundaries ────────────────────────────────────────────────────────
  test("bucketSize: ≥20k → 5k, ≥5k → 1k, ≥1k → 100, else 10"):
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(20000)), 5000L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(19999)), 1000L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(5000)), 1000L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(4999)), 100L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(1000)), 100L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(999)), 10L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(0)), 10L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(400)), 10L)

  test("bucketSize ignores the sign of the span (from/to given in either order)"):
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(-20000)), 5000L)
    assertEquals(Aggregate.bucketSize(com.histmersion.api.library.Aggregate.Span(-999)), 10L)

  test("finer: one step down the §1.6 ladder, floored at 10"):
    assertEquals(Aggregate.finer(com.histmersion.api.library.Aggregate.Width(5000)), 1000L)
    assertEquals(Aggregate.finer(com.histmersion.api.library.Aggregate.Width(1000)), 100L)
    assertEquals(Aggregate.finer(com.histmersion.api.library.Aggregate.Width(100)), 10L)
    assertEquals(Aggregate.finer(com.histmersion.api.library.Aggregate.Width(10)), 10L)

  // ── alignment ─────────────────────────────────────────────────────────────────────────────
  test("buckets are aligned to multiples of size, inclusive, and cover the window"):
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).head, (-1600L, -1501L))
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).last, (-1200L, -1101L))
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).size, 5)

  test("a window that does not start on a multiple snaps DOWN (floor, also for negatives)"):
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(-1637), com.histmersion.api.catalogue.Year(-1592), com.histmersion.api.library.Aggregate.Width(100)), List((-1700L, -1601L), (-1600L, -1501L)))
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(37), com.histmersion.api.catalogue.Year(250), com.histmersion.api.library.Aggregate.Width(100)), List((0L, 99L), (100L, 199L), (200L, 299L)))

  test("the same year lands in the same bucket whatever the window"):
    val a = Aggregate.buckets(com.histmersion.api.catalogue.Year(-3500), com.histmersion.api.catalogue.Year(-500), com.histmersion.api.library.Aggregate.Width(100)).find((s, e) => -1250 >= s.value && -1250 <= e.value)
    val b = Aggregate.buckets(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).find((s, e) => -1250 >= s.value && -1250 <= e.value)
    assertEquals(a, Some((-1300L, -1201L)))
    assertEquals(a, b)

  test("reversed from/to is normalised"):
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.library.Aggregate.Width(100)), Aggregate.buckets(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)))

  test("a zero-width window still yields the one bucket containing it"):
    assertEquals(Aggregate.buckets(com.histmersion.api.catalogue.Year(-1250), com.histmersion.api.catalogue.Year(-1250), com.histmersion.api.library.Aggregate.Width(100)), List((-1300L, -1201L)))

  test("size must be positive"):
    intercept[IllegalArgumentException](Aggregate.buckets(com.histmersion.api.catalogue.Year(0), com.histmersion.api.catalogue.Year(10), com.histmersion.api.library.Aggregate.Width(0)))

  // ── overlap counting ──────────────────────────────────────────────────────────────────────
  test("a row counts in EVERY bucket its period overlaps"):
    val h = Aggregate.histogram(List(row(Year(-1550), Year(-1350))), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100))
    assertEquals(h.map(b => (b.start, b.count)), List((-1600L, 1), (-1500L, 1), (-1400L, 1), (-1300L, 0), (-1200L, 0)))

  test("a point-in-time row hits exactly one bucket (the §1.7 acceptance case)"):
    val h = Aggregate.histogram(List(row(Year(-1250), Year(-1250))), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100))
    assertEquals(h.filter(_.count.value > 0).map(b => (b.start, b.end)), List((-1300L, -1201L)))

  test("bucket-boundary years belong to the bucket that starts on them"):
    assertEquals(Aggregate.histogram(List(row(Year(-1300), Year(-1300))), com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).map(_.count), List(0, 1, 0))
    assertEquals(Aggregate.histogram(List(row(Year(-1301), Year(-1301))), com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).map(_.count), List(1, 0, 0))

  test("counts overlap, so they do not sum to the number of rows"):
    val h = Aggregate.histogram(List(row(Year(-1600), Year(-1200)), row(Year(-1250), Year(-1240))), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100))
    assertEquals(h.map(_.count), List(1, 1, 1, 2, 1))
    assert(h.map(_.count.value).sum > 2)

  test("rows outside the window contribute nothing"):
    assertEquals(Aggregate.histogram(List(row(Year(-900), Year(-800))), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).map(_.count.value).sum, 0)

  test("undated rows are ignored; half-dated rows collapse to a point"):
    val rows = List(Period.Row(None, None), Period.Row(Some(com.histmersion.api.catalogue.Year(-1250)), None), Period.Row(None, Some(com.histmersion.api.catalogue.Year(-1450))))
    val h = Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100))
    assertEquals(h.map(b => (b.start, b.count)), List((-1600L, 0), (-1500L, 1), (-1400L, 0), (-1300L, 1), (-1200L, 0)))

  test("a row with inverted bounds is normalised, not dropped"):
    assertEquals(Aggregate.histogram(List(row(Year(-1350), Year(-1550))), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)).map(_.count), List(1, 1, 1, 0, 0))

  test("empty input still yields the full (all-zero) bucket grid"):
    val h = Aggregate.histogram(Nil, com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100))
    assertEquals(h.size, 5)
    assert(h.forall(_.count.value == 0))

  test("the size-less overload picks the adaptive size for the span"):
    // span 400 → 10-year buckets (§1.6)
    assertEquals(Aggregate.histogram(Nil, com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200)).size, 41)
    // span 3000 (the configured default window) → 100-year buckets
    assertEquals(Aggregate.histogram(Nil, com.histmersion.api.catalogue.Year(-3500), com.histmersion.api.catalogue.Year(-500)).size, 31)

  // ── Markdown rendering (CLI + MCP surface) ────────────────────────────────────────────────
  test("SearchMarkdown.histogram renders a table with a bar per non-empty bucket"):
    val h = TimeHistogram(com.histmersion.api.library.TimeHistogram.From(-1600), com.histmersion.api.library.TimeHistogram.To(-1200), com.histmersion.api.library.TimeHistogram.Bucket(100), Aggregate.histogram(List(row(Year(-1250), Year(-1250))), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100)))
    val md = SearchMarkdown.histogram(h, com.histmersion.api.library.Histogram.Unit("sources"))
    assert(md.contains("| period | sources | |"), md)
    // every table row must keep its leading pipe (a stripMargin here would silently eat them)
    assert(md.linesIterator.filter(_.contains("BCE–")).forall(_.startsWith("| ")), md)
    assert(md.contains("| 1300 BCE–1201 BCE | 1 | █"), md)
    assert(md.contains("1 non-empty bucket(s); each source is counted"), md)

  test("SearchMarkdown.histogram survives an all-empty histogram"):
    val md =
      SearchMarkdown.histogram(TimeHistogram(com.histmersion.api.library.TimeHistogram.From(-1600), com.histmersion.api.library.TimeHistogram.To(-1200), com.histmersion.api.library.TimeHistogram.Bucket(100), Aggregate.histogram(Nil, com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100))), com.histmersion.api.library.Histogram.Unit("sources"))
    assert(md.contains("0 non-empty bucket(s)"), md)

  // ── unbounded windows and Long overflow ──────────────────────────────────────────────────────

  test("bucketCount says how many buckets a window needs without building them"):
    assertEquals(Aggregate.bucketCount(com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.catalogue.Year(-1), com.histmersion.api.library.Aggregate.Width(100)), BigInt(10))
    assertEquals(Aggregate.bucketCount(com.histmersion.api.catalogue.Year(0), com.histmersion.api.catalogue.Year(0), com.histmersion.api.library.Aggregate.Width(10)), BigInt(1))
    assertEquals(Aggregate.bucketCount(com.histmersion.api.catalogue.Year(-3500), com.histmersion.api.catalogue.Year(-500), com.histmersion.api.library.Aggregate.Width(100)), BigInt(31))
    // …and it answers, exactly, for a window nobody could ever build
    assertEquals(Aggregate.bucketCount(com.histmersion.api.catalogue.Year(-1000000000000000L), com.histmersion.api.catalogue.Year(1000000000000000L), com.histmersion.api.library.Aggregate.Width(5000)), BigInt("400000000001"))
    assert(Aggregate.bucketCount(Year(-Long.MaxValue), Year(Long.MaxValue), com.histmersion.api.library.Aggregate.Width(10)) > BigInt(10).pow(17))

  test("bucketCount agrees with buckets on windows small enough to build"):
    List((-1000L, -1L, 100L), (-3500L, -500L, 10L), (7L, 7L, 5L), (-20L, 20L, 3L))
      .foreach { (f, t, s) =>
        assertEquals(BigInt(Aggregate.buckets(Year(f), Year(t), Aggregate.Width(s)).size), Aggregate.bucketCount(Year(f), Year(t), Aggregate.Width(s)), s"($f, $t, $s)")
      }

  test("to = Long.MaxValue terminates instead of looping on overflow"):
    // `Iterator.iterate(_ + size)` wraps to a negative number here, `takeWhile(_ <= hi)` goes true
    // again, and the iterator never ends. The last bucket is clamped instead.
    val bs = Aggregate.buckets(Year(Long.MaxValue - 25), Year(Long.MaxValue), com.histmersion.api.library.Aggregate.Width(10))
    assert(bs.size < 10, bs.toString)
    assert(bs.forall((s, e) => s.value <= e.value), bs.toString)
    assertEquals(bs.last._2, Long.MaxValue)

  test("a bucket's end never precedes its start, anywhere near the boundaries"):
    List(Long.MinValue + 1, -1L, 0L, 1L, Long.MaxValue - 1).foreach { v =>
      Aggregate.buckets(Year(v), Year(v), com.histmersion.api.library.Aggregate.Width(5000)).foreach((s, e) => assert(s.value <= e.value, s"($v): ($s, $e)"))
    }

/** `unit=starts` — one bar per source, at the bucket its period begins in (UX item 11). Pure. */
class AggregateStartsSuite extends munit.FunSuite:
  import com.histmersion.api.library.Aggregate.Counting

  private def row(a: Year, b: Year) = Period.Row(Some(a), Some(b))

  test("Starts counts a long-range source ONCE, where it begins"):
    val rows = List(row(Year(-3300), Year(-1200)))
    val over = Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-3300), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100), Counting.Overlap)
    val st = Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-3300), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.library.Aggregate.Width(100), Counting.Starts)
    assertEquals(over.count(_.count.value > 0), over.size, "overlap fills every bar")
    assertEquals(st.count(_.count.value > 0), 1)
    assertEquals(st.find(_.count.value > 0).map(_.start), Some(-3300L))

  test("Starts bars sum to the number of dated rows in the window"):
    val rows = List(row(Year(-2000), Year(-1000)), row(Year(-1950), Year(-1900)), row(Year(-1200), Year(-1100)), row(Year(-1150), Year(-900)))
    val st = Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-900), com.histmersion.api.library.Aggregate.Width(100), Counting.Starts)
    assertEquals(st.map(_.count.value).sum, 4)

  test("a source starting before the window is not counted into the first bar"):
    val st = Aggregate.histogram(List(row(Year(-5000), Year(-1500))), com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.library.Aggregate.Width(100), Counting.Starts)
    assertEquals(st.map(_.count.value).sum, 0)

  test("undated rows are dropped by both countings"):
    val rows = List(Period.Row(None, None), row(Year(-1500), Year(-1400)))
    assertEquals(Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.library.Aggregate.Width(100), Counting.Starts).map(_.count.value).sum, 1)

  test("Overlap stays the default, byte for byte"):
    val rows = List(row(Year(-2000), Year(-1500)), row(Year(-1600), Year(-1000)))
    assertEquals(
      Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.library.Aggregate.Width(100)),
      Aggregate.histogram(rows, com.histmersion.api.catalogue.Year(-2000), com.histmersion.api.catalogue.Year(-1000), com.histmersion.api.library.Aggregate.Width(100), Counting.Overlap)
    )

  test("the wire spelling parses, and nothing else does"):
    assertEquals(Counting.parse(com.histmersion.api.library.Aggregate.Counting.Wire("starts")), Some(Counting.Starts))
    assertEquals(Counting.parse(com.histmersion.api.library.Aggregate.Counting.Wire("overlap")), Some(Counting.Overlap))
    assertEquals(Counting.parse(com.histmersion.api.library.Aggregate.Counting.Wire("midpoint")), None)
