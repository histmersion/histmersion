package com.histmersion.web

import com.histmersion.core.GeoHit
import com.histmersion.web.map.ClusterMotion

class PlaybackFramesSuite extends munit.FunSuite:

  private def hit(start: Long, end: Long, claim: String = "c"): GeoHit =
    GeoHit("zz", "Zz", claim, None, Some(30.0), Some(37.0), Some(start), Some(end), None)

  // ── PlaybackFeed ─────────────────────────────────────────────────────────────

  test("a stretch covers seconds of playback, and at least four windows") {
    assertEquals(PlaybackFeed.chunkYears(50, 67.0, 4.0), 400L)
    assertEquals(PlaybackFeed.chunkYears(50, 200.0, 4.0), 800L)
    assertEquals(PlaybackFeed.chunkYears(0, 0.0, 4.0), 8L)
  }

  test("the first stretch starts at the window, the next one is asked half a stretch before it is needed") {
    assertEquals(PlaybackFeed.next(None, -3000, 50, 400, -1000), Some((-3050L, -2550L)))
    // the window's end (-2950) is far from what is held (-2550): nothing yet
    assertEquals(PlaybackFeed.next(Some(-2550), -3000, 50, 400, -1000), None)
    // within half a stretch: the next one, from the year after what is held
    assertEquals(PlaybackFeed.next(Some(-2550), -2800, 50, 400, -1000), Some((-2549L, -2149L)))
    // never past the last year played (and its window)
    assertEquals(PlaybackFeed.next(Some(-1100), -1200, 50, 400, -1000), Some((-1099L, -950L)))
    assertEquals(PlaybackFeed.next(Some(-950), -1000, 50, 400, -1000), None)
  }

  test("a full answer covers only up to the year before its latest start; a short one all it was asked") {
    val got = Seq(hit(-3000, -2900), hit(-2800, -2700), hit(-2600, -2500))
    assertEquals(PlaybackFeed.covered((-3050, -2550), got, 10), -2550L)
    assertEquals(PlaybackFeed.covered((-3050, -2550), got, 3), -2601L)
    // never before what was asked
    assertEquals(PlaybackFeed.covered((-2600, -2550), Seq(hit(-2600, -2500)), 1), -2601L)
  }

  test("a window holds what overlaps it; undated findings never") {
    val pts = Seq(hit(-3000, -2900, "a"), hit(-2950, -2950, "b"), hit(-2800, -2700, "c"),
      GeoHit("zz", "Zz", "u", None, Some(30.0), Some(37.0), None, None, None))
    assertEquals(PlaybackFeed.inWindow(pts, -2960, -2900).map(_.claim), List("a", "b"))
    assertEquals(PlaybackFeed.inWindow(pts, -2899, -2801).map(_.claim), Nil)
    assertEquals(PlaybackFeed.keep(pts, -2900).map(_.claim), List("a", "c", "u"))
  }

  // ── PlaybackAreas ────────────────────────────────────────────────────────────

  test("a state is wholly drawn in its years and fades in and out over the fade") {
    assertEquals(PlaybackAreas.alpha(-1500, -1600, -1400, 20), 1.0)
    assertEquals(PlaybackAreas.alpha(-1610, -1600, -1400, 20), 0.5)
    assertEquals(PlaybackAreas.alpha(-1390, -1600, -1400, 20), 0.5)
    assertEquals(PlaybackAreas.alpha(-1700, -1600, -1400, 20), 0.0)
    assertEquals(PlaybackAreas.alpha(-1601, -1600, -1400, 0), 0.0)
    assertEquals(PlaybackAreas.step(0.512), 0.5)
    assertEquals(PlaybackAreas.step(0.53), 0.55)
  }

  test("a state's key names its place and its years; it lives a fade past its end") {
    assertEquals(PlaybackAreas.key("hittite", Some(-1650.0), Some(-1180.0)), "hittite@-1650..-1180")
    assertEquals(PlaybackAreas.key("x", None, None), "x@..")
    assert(PlaybackAreas.live(-1170, -1180, 20))
    assert(!PlaybackAreas.live(-1150, -1180, 20))
  }

  // ── ClusterMotion ────────────────────────────────────────────────────────────

  test("a marker made of one former place comes out of it; one of several (a merge) stays") {
    assertEquals(ClusterMotion.origin(Seq((10.0, 20.0), (10.0, 20.0))), Some((10.0, 20.0)))
    assertEquals(ClusterMotion.origin(Seq((10.0, 20.0), (30.0, 20.0))), None)
    assertEquals(ClusterMotion.origin(Nil), None)
  }

  test("a marker merges into a bigger group that holds all its points; a split's bubble does not") {
    assert(ClusterMotion.merges(Seq("a"), Seq("a", "b")))
    assert(ClusterMotion.merges(Seq("a", "b"), Seq("a", "b", "c")))
    assert(!ClusterMotion.merges(Seq("a", "b"), Seq("a")))
    assert(!ClusterMotion.merges(Seq("a", "b"), Seq("a", "b")))
    assert(!ClusterMotion.merges(Seq("a", "x"), Seq("a", "b", "c")))
  }
