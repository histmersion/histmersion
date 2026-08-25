package histdb.core

/** Pure tests for the timeline bucketing (plan §1.6 sizes, §1.7 strip). No DB involved. */
class AggregateSuite extends munit.FunSuite:

  private def row(a: Long, b: Long) = PeriodRow(Some(a), Some(b))

  // ── bucket sizes, at the boundaries ────────────────────────────────────────────────────────
  test("bucketSize: ≥20k → 5k, ≥5k → 1k, ≥1k → 100, else 10"):
    assertEquals(Aggregate.bucketSize(20000), 5000L)
    assertEquals(Aggregate.bucketSize(19999), 1000L)
    assertEquals(Aggregate.bucketSize(5000), 1000L)
    assertEquals(Aggregate.bucketSize(4999), 100L)
    assertEquals(Aggregate.bucketSize(1000), 100L)
    assertEquals(Aggregate.bucketSize(999), 10L)
    assertEquals(Aggregate.bucketSize(0), 10L)
    assertEquals(Aggregate.bucketSize(400), 10L)

  test("bucketSize ignores the sign of the span (from/to given in either order)"):
    assertEquals(Aggregate.bucketSize(-20000), 5000L)
    assertEquals(Aggregate.bucketSize(-999), 10L)

  test("finer: one step down the §1.6 ladder, floored at 10"):
    assertEquals(Aggregate.finer(5000), 1000L)
    assertEquals(Aggregate.finer(1000), 100L)
    assertEquals(Aggregate.finer(100), 10L)
    assertEquals(Aggregate.finer(10), 10L)

  // ── alignment ─────────────────────────────────────────────────────────────────────────────
  test("buckets are aligned to multiples of size, inclusive, and cover the window"):
    assertEquals(Aggregate.buckets(-1600, -1200, 100).head, (-1600L, -1501L))
    assertEquals(Aggregate.buckets(-1600, -1200, 100).last, (-1200L, -1101L))
    assertEquals(Aggregate.buckets(-1600, -1200, 100).size, 5)

  test("a window that does not start on a multiple snaps DOWN (floor, also for negatives)"):
    assertEquals(Aggregate.buckets(-1637, -1592, 100), List((-1700L, -1601L), (-1600L, -1501L)))
    assertEquals(Aggregate.buckets(37, 250, 100), List((0L, 99L), (100L, 199L), (200L, 299L)))

  test("the same year lands in the same bucket whatever the window"):
    val a = Aggregate.buckets(-3500, -500, 100).find((s, e) => -1250 >= s && -1250 <= e)
    val b = Aggregate.buckets(-1600, -1200, 100).find((s, e) => -1250 >= s && -1250 <= e)
    assertEquals(a, Some((-1300L, -1201L)))
    assertEquals(a, b)

  test("reversed from/to is normalised"):
    assertEquals(Aggregate.buckets(-1200, -1600, 100), Aggregate.buckets(-1600, -1200, 100))

  test("a zero-width window still yields the one bucket containing it"):
    assertEquals(Aggregate.buckets(-1250, -1250, 100), List((-1300L, -1201L)))

  test("size must be positive"):
    intercept[IllegalArgumentException](Aggregate.buckets(0, 10, 0))

  // ── overlap counting ──────────────────────────────────────────────────────────────────────
  test("a row counts in EVERY bucket its period overlaps"):
    val h = Aggregate.histogram(List(row(-1550, -1350)), -1600, -1200, 100)
    assertEquals(h.map(b => (b.start, b.count)),
      List((-1600L, 1), (-1500L, 1), (-1400L, 1), (-1300L, 0), (-1200L, 0)))

  test("a point-in-time row hits exactly one bucket (the §1.7 acceptance case)"):
    val h = Aggregate.histogram(List(row(-1250, -1250)), -1600, -1200, 100)
    assertEquals(h.filter(_.count > 0).map(b => (b.start, b.end)), List((-1300L, -1201L)))

  test("bucket-boundary years belong to the bucket that starts on them"):
    assertEquals(Aggregate.histogram(List(row(-1300, -1300)), -1400, -1200, 100).map(_.count), List(0, 1, 0))
    assertEquals(Aggregate.histogram(List(row(-1301, -1301)), -1400, -1200, 100).map(_.count), List(1, 0, 0))

  test("counts overlap, so they do not sum to the number of rows"):
    val h = Aggregate.histogram(List(row(-1600, -1200), row(-1250, -1240)), -1600, -1200, 100)
    assertEquals(h.map(_.count), List(1, 1, 1, 2, 1))
    assert(h.map(_.count).sum > 2)

  test("rows outside the window contribute nothing"):
    assertEquals(Aggregate.histogram(List(row(-900, -800)), -1600, -1200, 100).map(_.count).sum, 0)

  test("undated rows are ignored; half-dated rows collapse to a point"):
    val rows = List(PeriodRow(None, None), PeriodRow(Some(-1250), None), PeriodRow(None, Some(-1450)))
    val h    = Aggregate.histogram(rows, -1600, -1200, 100)
    assertEquals(h.map(b => (b.start, b.count)),
      List((-1600L, 0), (-1500L, 1), (-1400L, 0), (-1300L, 1), (-1200L, 0)))

  test("a row with inverted bounds is normalised, not dropped"):
    assertEquals(Aggregate.histogram(List(row(-1350, -1550)), -1600, -1200, 100).map(_.count), List(1, 1, 1, 0, 0))

  test("empty input still yields the full (all-zero) bucket grid"):
    val h = Aggregate.histogram(Nil, -1600, -1200, 100)
    assertEquals(h.size, 5)
    assert(h.forall(_.count == 0))

  test("the size-less overload picks the adaptive size for the span"):
    // span 400 → 10-year buckets (§1.6)
    assertEquals(Aggregate.histogram(Nil, -1600, -1200).size, 41)
    // span 3000 (the configured default window) → 100-year buckets
    assertEquals(Aggregate.histogram(Nil, -3500, -500).size, 31)

  // ── Markdown rendering (CLI + MCP surface) ────────────────────────────────────────────────
  test("Markdown.histogram renders a table with a bar per non-empty bucket"):
    val h  = TimeHistogram(-1600, -1200, 100, Aggregate.histogram(List(row(-1250, -1250)), -1600, -1200, 100))
    val md = Markdown.histogram(h, "sources")
    assert(md.contains("| period | sources | |"), md)
    // every table row must keep its leading pipe (a stripMargin here would silently eat them)
    assert(md.linesIterator.filter(_.contains("BCE–")).forall(_.startsWith("| ")), md)
    assert(md.contains("| 1300 BCE–1201 BCE | 1 | █"), md)
    assert(md.contains("1 non-empty bucket(s); each source is counted"), md)

  test("Markdown.histogram survives an all-empty histogram"):
    val md = Markdown.histogram(TimeHistogram(-1600, -1200, 100, Aggregate.histogram(Nil, -1600, -1200, 100)), "sources")
    assert(md.contains("0 non-empty bucket(s)"), md)
