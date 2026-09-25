package com.histmersion.web

class TimelineViewSuite extends munit.FunSuite:

  // ── bucketSizeFor ─────────────────────────────────────────────────────────────

  test("narrow span with normal screen produces 1-year buckets") {
    assertEquals(TimelineView.bucketSizeFor(100L, 600.0), 1L)
  }

  test("very narrow span produces 1-year buckets") {
    assertEquals(TimelineView.bucketSizeFor(10L, 600.0), 1L)
  }

  test("wide span with wide screen stays under MAX_BUCKETS") {
    val size = TimelineView.bucketSizeFor(14026L, 3392.0)
    val buckets = Math.ceil(14026.0 / size).toLong
    assert(buckets <= TimelineView.MAX_BUCKETS,
      s"$buckets buckets (size=$size) exceeds MAX_BUCKETS=${TimelineView.MAX_BUCKETS}")
  }

  test("wide span with narrow screen stays under MAX_BUCKETS") {
    val size = TimelineView.bucketSizeFor(14026L, 400.0)
    val buckets = Math.ceil(14026.0 / size).toLong
    assert(buckets <= TimelineView.MAX_BUCKETS,
      s"$buckets buckets (size=$size) exceeds MAX_BUCKETS=${TimelineView.MAX_BUCKETS}")
  }

  test("bucket size is always at least 1") {
    assertEquals(TimelineView.bucketSizeFor(1L, 10000.0), 1L)
    assertEquals(TimelineView.bucketSizeFor(0L, 600.0), 1L)
  }

  test("medium span with medium screen produces reasonable buckets") {
    val size = TimelineView.bucketSizeFor(3000L, 800.0)
    assert(size >= 1L, "size must be positive")
    val buckets = Math.ceil(3000.0 / size).toLong
    assert(buckets <= TimelineView.MAX_BUCKETS,
      s"$buckets buckets (size=$size) exceeds limit")
    assert(buckets >= 100, s"only $buckets buckets for 3000-year span on 800px is too coarse")
  }

  test("bucket count never exceeds MAX_BUCKETS for any reasonable input") {
    val cases = List(
      (14026L, 3392.0), (14026L, 600.0), (14026L, 100.0),
      (3000L, 1920.0), (100L, 1920.0), (50000L, 400.0),
      (1L, 5000.0), (100000L, 100.0)
    )
    for (span, px) <- cases do
      val size = TimelineView.bucketSizeFor(span, px)
      val buckets = Math.ceil(span.toDouble / size).toLong
      assert(buckets <= TimelineView.MAX_BUCKETS,
        s"span=$span px=$px → size=$size → $buckets buckets (limit=${TimelineView.MAX_BUCKETS})")
  }

  // ── realign: keeping a drawn curve under the span on screen ─────────────────────────────────

  test("realign is identity when nothing moved") {
    assertEquals(TimelineView.realign((-3000L, -1000L, 800.0), (-3000L, -1000L, 800.0)), (0.0, 1.0))
  }

  test("realign stretches a curve to a wider element") {
    val (tx, scale) = TimelineView.realign((0L, 100L, 400.0), (0L, 100L, 800.0))
    assertEquals(tx, 0.0)
    assertEquals(scale, 2.0)
  }

  test("realign shrinks and shifts a curve when the span widens (zoom out)") {
    // drawn for 0..100 at 1000px; now showing -100..200 → the old curve covers the middle third
    val (tx, scale) = TimelineView.realign((0L, 100L, 1000.0), (-100L, 200L, 1000.0))
    assertEqualsDouble(scale, 1.0 / 3, 1e-9)
    assertEqualsDouble(tx, 1000.0 / 3, 1e-9)
  }

  test("realign shifts a curve when the span pans") {
    val (tx, scale) = TimelineView.realign((0L, 100L, 1000.0), (50L, 150L, 1000.0))
    assertEquals(scale, 1.0)
    assertEquals(tx, -500.0)
  }

  // ── underlayBuckets: the overview slice drawn beneath a stale detail curve ─────────────────

  private val ov = (0 until 10).toList.map(i => com.histmersion.core.TimeBucket(i * 100L, i * 100L + 99, i))

  test("underlayBuckets keeps the overlapping buckets plus one neighbour each side") {
    assertEquals(TimelineView.underlayBuckets(ov, 300, 450).map(_.start), List(200L, 300L, 400L, 500L))
  }

  test("underlayBuckets clamps at the ends of the data") {
    assertEquals(TimelineView.underlayBuckets(ov, -50, 50).map(_.start), List(0L, 100L))
    assertEquals(TimelineView.underlayBuckets(ov, 950, 2000).map(_.start), List(800L, 900L))
  }

  test("underlayBuckets is empty outside the data") {
    assertEquals(TimelineView.underlayBuckets(ov, 5000, 6000), Nil)
  }
