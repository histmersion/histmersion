package histdb.web

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
