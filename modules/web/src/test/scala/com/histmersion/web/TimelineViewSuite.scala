package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Years
import com.histmersion.api.library.TimeBucket

class TimelineViewSuite extends munit.FunSuite:

  // ── bucketSizeFor ─────────────────────────────────────────────────────────────

  test("narrow span with normal screen produces 1-year buckets") {
    assertEquals(TimelineView.bucketSizeFor(Years.Shift(100L), 600.0), 1L)
  }

  test("very narrow span produces 1-year buckets") {
    assertEquals(TimelineView.bucketSizeFor(Years.Shift(10L), 600.0), 1L)
  }

  test("wide span with wide screen stays under MAX_BUCKETS") {
    val size = TimelineView.bucketSizeFor(Years.Shift(14026L), 3392.0)
    val buckets = Math.ceil(14026.0 / size.value).toLong
    assert(
      buckets <= TimelineView.MAX_BUCKETS.value,
      s"$buckets buckets (size=$size) exceeds MAX_BUCKETS=${TimelineView.MAX_BUCKETS}"
    )
  }

  test("wide span with narrow screen stays under MAX_BUCKETS") {
    val size = TimelineView.bucketSizeFor(Years.Shift(14026L), 400.0)
    val buckets = Math.ceil(14026.0 / size.value).toLong
    assert(
      buckets <= TimelineView.MAX_BUCKETS.value,
      s"$buckets buckets (size=$size) exceeds MAX_BUCKETS=${TimelineView.MAX_BUCKETS}"
    )
  }

  test("bucket size is always at least 1") {
    assertEquals(TimelineView.bucketSizeFor(Years.Shift(1L), 10000.0), 1L)
    assertEquals(TimelineView.bucketSizeFor(Years.Shift(0L), 600.0), 1L)
  }

  test("medium span with medium screen produces reasonable buckets") {
    val size = TimelineView.bucketSizeFor(Years.Shift(3000L), 800.0)
    assert(size.value >= 1L, "size must be positive")
    val buckets = Math.ceil(3000.0 / size.value).toLong
    assert(buckets <= TimelineView.MAX_BUCKETS.value, s"$buckets buckets (size=$size) exceeds limit")
    assert(buckets >= 100, s"only $buckets buckets for 3000-year span on 800px is too coarse")
  }

  test("bucket count never exceeds MAX_BUCKETS for any reasonable input") {
    val cases = List(
      (14026L, 3392.0),
      (14026L, 600.0),
      (14026L, 100.0),
      (3000L, 1920.0),
      (100L, 1920.0),
      (50000L, 400.0),
      (1L, 5000.0),
      (100000L, 100.0)
    )
    for (span, px) <- cases do
      val size = TimelineView.bucketSizeFor(Years.Shift(span), px)
      val buckets = Math.ceil(span.toDouble / size.value).toLong
      assert(
        buckets <= TimelineView.MAX_BUCKETS.value,
        s"span=$span px=$px → size=$size → $buckets buckets (limit=${TimelineView.MAX_BUCKETS})"
      )
  }

  // ── realign: keeping a drawn curve under the span on screen ─────────────────────────────────

  test("realign is identity when nothing moved") {
    assertEquals(
      TimelineView.realign(
        (Years.Astronomical(-3000L), Years.Astronomical(-1000L), 800.0),
        (Years.Astronomical(-3000L), Years.Astronomical(-1000L), 800.0)
      ),
      (0.0, 1.0)
    )
  }

  test("realign stretches a curve to a wider element") {
    val (tx, scale) = TimelineView.realign(
      (Years.Astronomical(0L), Years.Astronomical(100L), 400.0),
      (Years.Astronomical(0L), Years.Astronomical(100L), 800.0)
    )
    assertEquals(tx, 0.0)
    assertEquals(scale, 2.0)
  }

  test("realign shrinks and shifts a curve when the span widens (zoom out)") {
    // drawn for 0..100 at 1000px; now showing -100..200 → the old curve covers the middle third
    val (tx, scale) = TimelineView.realign(
      (Years.Astronomical(0L), Years.Astronomical(100L), 1000.0),
      (Years.Astronomical(-100L), Years.Astronomical(200L), 1000.0)
    )
    assertEqualsDouble(scale, 1.0 / 3, 1e-9)
    assertEqualsDouble(tx, 1000.0 / 3, 1e-9)
  }

  test("realign shifts a curve when the span pans") {
    val (tx, scale) = TimelineView.realign(
      (Years.Astronomical(0L), Years.Astronomical(100L), 1000.0),
      (Years.Astronomical(50L), Years.Astronomical(150L), 1000.0)
    )
    assertEquals(scale, 1.0)
    assertEquals(tx, -500.0)
  }

  // ── underlayBuckets: the overview slice drawn beneath a stale detail curve ─────────────────

  private val ov = (0 until 10).toList.map(i =>
    TimeBucket(TimeBucket.Start(i * 100L), TimeBucket.End(i * 100L + 99), TimeBucket.Count(i))
  )

  test("underlayBuckets keeps the overlapping buckets plus one neighbour each side") {
    assertEquals(
      TimelineView.underlayBuckets(ov, Years.Astronomical(300), Years.Astronomical(450)).map(_.start),
      List(200L, 300L, 400L, 500L)
    )
  }

  test("underlayBuckets clamps at the ends of the data") {
    assertEquals(
      TimelineView.underlayBuckets(ov, Years.Astronomical(-50), Years.Astronomical(50)).map(_.start),
      List(0L, 100L)
    )
    assertEquals(
      TimelineView.underlayBuckets(ov, Years.Astronomical(950), Years.Astronomical(2000)).map(_.start),
      List(800L, 900L)
    )
  }

  test("underlayBuckets is empty outside the data") {
    assertEquals(TimelineView.underlayBuckets(ov, Years.Astronomical(5000), Years.Astronomical(6000)), Nil)
  }
