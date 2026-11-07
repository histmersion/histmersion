package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.GeoHit
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.web.map.{ClusterMotion, ScreenPt}

class PlaybackFramesSuite extends munit.FunSuite:

  private def hit(start: Year, end: Year, claim: GeoHit.Claim = GeoHit.Claim("c")): GeoHit =
    GeoHit(com.histmersion.api.library.Citekey("zz"), com.histmersion.api.library.GeoHit.Title("Zz"), claim, None, Some(Longitude(30.0)), Some(Latitude(37.0)), Some(start), Some(end), None)

  // ── PlaybackFeed ─────────────────────────────────────────────────────────────

  test("a stretch covers seconds of playback, and at least four windows") {
    assertEquals(PlaybackFeed.chunkYears(com.histmersion.web.PlaybackFeed.WindowHalfYears(50), PlaybackBuckets.YearsPerSecond(67.0), PlaybackBuckets.Seconds(4.0)), 400L)
    assertEquals(PlaybackFeed.chunkYears(com.histmersion.web.PlaybackFeed.WindowHalfYears(50), PlaybackBuckets.YearsPerSecond(200.0), PlaybackBuckets.Seconds(4.0)), 800L)
    assertEquals(PlaybackFeed.chunkYears(com.histmersion.web.PlaybackFeed.WindowHalfYears(0), PlaybackBuckets.YearsPerSecond(0.0), PlaybackBuckets.Seconds(4.0)), 8L)
  }

  test("the first stretch starts at the window, the next one is asked half a stretch before it is needed") {
    assertEquals(PlaybackFeed.next(None, com.histmersion.api.Years.Astronomical(-3000), com.histmersion.web.PlaybackFeed.WindowHalfYears(50), com.histmersion.web.PlaybackBuckets.BucketYears(400), com.histmersion.api.Years.Astronomical(-1000)), Some((-3050L, -2550L)))
    // the window's end (-2950) is far from what is held (-2550): nothing yet
    assertEquals(PlaybackFeed.next(Some(com.histmersion.api.Years.Astronomical(-2550)), com.histmersion.api.Years.Astronomical(-3000), com.histmersion.web.PlaybackFeed.WindowHalfYears(50), com.histmersion.web.PlaybackBuckets.BucketYears(400), com.histmersion.api.Years.Astronomical(-1000)), None)
    // within half a stretch: the next one, from the year after what is held
    assertEquals(PlaybackFeed.next(Some(com.histmersion.api.Years.Astronomical(-2550)), com.histmersion.api.Years.Astronomical(-2800), com.histmersion.web.PlaybackFeed.WindowHalfYears(50), com.histmersion.web.PlaybackBuckets.BucketYears(400), com.histmersion.api.Years.Astronomical(-1000)), Some((-2549L, -2149L)))
    // never past the last year played (and its window)
    assertEquals(PlaybackFeed.next(Some(com.histmersion.api.Years.Astronomical(-1100)), com.histmersion.api.Years.Astronomical(-1200), com.histmersion.web.PlaybackFeed.WindowHalfYears(50), com.histmersion.web.PlaybackBuckets.BucketYears(400), com.histmersion.api.Years.Astronomical(-1000)), Some((-1099L, -950L)))
    assertEquals(PlaybackFeed.next(Some(com.histmersion.api.Years.Astronomical(-950)), com.histmersion.api.Years.Astronomical(-1000), com.histmersion.web.PlaybackFeed.WindowHalfYears(50), com.histmersion.web.PlaybackBuckets.BucketYears(400), com.histmersion.api.Years.Astronomical(-1000)), None)
  }

  test("a full answer covers only up to the year before its latest start; a short one all it was asked") {
    val got = Seq(hit(Year(-3000), Year(-2900)), hit(Year(-2800), Year(-2700)), hit(Year(-2600), Year(-2500)))
    assertEquals(PlaybackFeed.covered((com.histmersion.api.Years.Astronomical(-3050), com.histmersion.api.Years.Astronomical(-2550)), got, com.histmersion.api.Paging.Limit(10)), -2550L)
    assertEquals(PlaybackFeed.covered((com.histmersion.api.Years.Astronomical(-3050), com.histmersion.api.Years.Astronomical(-2550)), got, com.histmersion.api.Paging.Limit(3)), -2601L)
    // never before what was asked
    assertEquals(PlaybackFeed.covered((com.histmersion.api.Years.Astronomical(-2600), com.histmersion.api.Years.Astronomical(-2550)), Seq(hit(Year(-2600), Year(-2500))), com.histmersion.api.Paging.Limit(1)), -2601L)
  }

  test("a window holds what overlaps it; undated findings never") {
    val pts = Seq(
      hit(Year(-3000), Year(-2900), GeoHit.Claim("a")),
      hit(Year(-2950), Year(-2950), GeoHit.Claim("b")),
      hit(Year(-2800), Year(-2700), GeoHit.Claim("c")),
      GeoHit(com.histmersion.api.library.Citekey("zz"), com.histmersion.api.library.GeoHit.Title("Zz"), com.histmersion.api.library.GeoHit.Claim("u"), None, Some(Longitude(30.0)), Some(Latitude(37.0)), None, None, None)
    )
    assertEquals(PlaybackFeed.inWindow(pts, com.histmersion.api.Years.Astronomical(-2960), com.histmersion.api.Years.Astronomical(-2900)).map(_.claim), List("a", "b"))
    assertEquals(PlaybackFeed.inWindow(pts, com.histmersion.api.Years.Astronomical(-2899), com.histmersion.api.Years.Astronomical(-2801)).map(_.claim), Nil)
    assertEquals(PlaybackFeed.keep(pts, com.histmersion.api.Years.Astronomical(-2900)).map(_.claim), List("a", "c", "u"))
  }

  // ── PlaybackAreas ────────────────────────────────────────────────────────────

  test("a state is wholly drawn in its years and fades in and out over the fade") {
    assertEquals(PlaybackAreas.alpha(com.histmersion.web.PlaybackAreas.FractionalYear(-1500), com.histmersion.web.PlaybackAreas.FractionalYear(-1600), com.histmersion.web.PlaybackAreas.FractionalYear(-1400), com.histmersion.web.PlaybackAreas.FadeYears(20)), 1.0)
    assertEquals(PlaybackAreas.alpha(com.histmersion.web.PlaybackAreas.FractionalYear(-1610), com.histmersion.web.PlaybackAreas.FractionalYear(-1600), com.histmersion.web.PlaybackAreas.FractionalYear(-1400), com.histmersion.web.PlaybackAreas.FadeYears(20)), 0.5)
    assertEquals(PlaybackAreas.alpha(com.histmersion.web.PlaybackAreas.FractionalYear(-1390), com.histmersion.web.PlaybackAreas.FractionalYear(-1600), com.histmersion.web.PlaybackAreas.FractionalYear(-1400), com.histmersion.web.PlaybackAreas.FadeYears(20)), 0.5)
    assertEquals(PlaybackAreas.alpha(com.histmersion.web.PlaybackAreas.FractionalYear(-1700), com.histmersion.web.PlaybackAreas.FractionalYear(-1600), com.histmersion.web.PlaybackAreas.FractionalYear(-1400), com.histmersion.web.PlaybackAreas.FadeYears(20)), 0.0)
    assertEquals(PlaybackAreas.alpha(com.histmersion.web.PlaybackAreas.FractionalYear(-1601), com.histmersion.web.PlaybackAreas.FractionalYear(-1600), com.histmersion.web.PlaybackAreas.FractionalYear(-1400), com.histmersion.web.PlaybackAreas.FadeYears(0)), 0.0)
    assertEquals(PlaybackAreas.step(PlaybackAreas.Opacity(0.512)), 0.5)
    assertEquals(PlaybackAreas.step(PlaybackAreas.Opacity(0.53)), 0.55)
  }

  test("a state's key names its place and its years; it lives a fade past its end") {
    assertEquals(PlaybackAreas.key(com.histmersion.web.PlaybackAreas.AreaKey("hittite"), Some(PlaybackAreas.FractionalYear(-1650.0)), Some(PlaybackAreas.FractionalYear(-1180.0))), "hittite@-1650..-1180")
    assertEquals(PlaybackAreas.key(com.histmersion.web.PlaybackAreas.AreaKey("x"), None, None), "x@..")
    assert(PlaybackAreas.live(com.histmersion.web.PlaybackAreas.FractionalYear(-1170), com.histmersion.web.PlaybackAreas.FractionalYear(-1180), com.histmersion.web.PlaybackAreas.FadeYears(20)))
    assert(!PlaybackAreas.live(com.histmersion.web.PlaybackAreas.FractionalYear(-1150), com.histmersion.web.PlaybackAreas.FractionalYear(-1180), com.histmersion.web.PlaybackAreas.FadeYears(20)))
  }

  // ── ClusterMotion ────────────────────────────────────────────────────────────

  test("a marker made of one former place comes out of it; one of several (a merge) stays") {
    assertEquals(ClusterMotion.origin(Seq((ScreenPt.X(10.0), ScreenPt.Y(20.0)), (ScreenPt.X(10.0), ScreenPt.Y(20.0)))), Some((10.0, 20.0)))
    assertEquals(ClusterMotion.origin(Seq((ScreenPt.X(10.0), ScreenPt.Y(20.0)), (ScreenPt.X(30.0), ScreenPt.Y(20.0)))), None)
    assertEquals(ClusterMotion.origin(Nil), None)
  }

  test("a marker merges into a bigger group that holds all its points; a split's bubble does not") {
    assert(ClusterMotion.merges(Seq(com.histmersion.web.map.Clustering.GroupKey("a")), Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b"))))
    assert(ClusterMotion.merges(Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b")), Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b"), com.histmersion.web.map.Clustering.GroupKey("c"))))
    assert(!ClusterMotion.merges(Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b")), Seq(com.histmersion.web.map.Clustering.GroupKey("a"))))
    assert(!ClusterMotion.merges(Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b")), Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b"))))
    assert(!ClusterMotion.merges(Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("x")), Seq(com.histmersion.web.map.Clustering.GroupKey("a"), com.histmersion.web.map.Clustering.GroupKey("b"), com.histmersion.web.map.Clustering.GroupKey("c"))))
  }

  test("a zoom animates while at most the limit of markers is drawn before and after it, whatever is held") {
    assert(ClusterMotion.within(drawnBefore = 465, drawnAfter = 217, max = 1000))
    assert(ClusterMotion.within(drawnBefore = 1000, drawnAfter = 1000, max = 1000))
    assert(!ClusterMotion.within(drawnBefore = 1001, drawnAfter = 10, max = 1000))
    assert(!ClusterMotion.within(drawnBefore = 10, drawnAfter = 1001, max = 1000))
  }
