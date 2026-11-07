package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.{SeaFloorSource, UserPaths}
import com.histmersion.util.basemap.{RetryWait, SeaFloorGrid}

import java.nio.file.Path
import java.time.Instant

/** The base map's wiring: the outbound policy its client is given, the sea-floor grid of each setting, and where
  * `basemap.dir` resolves. No network, no database.
  */
class BasemapWiringSuite extends munit.FunSuite:
  private val now = Instant.parse("2026-10-31T12:00:00Z")

  test("a slow-down answer is waited for after its Retry-After, else 1, 2, 4 … s; any other answer is taken"):
    val p = BasemapWiring.Retries
    assertEquals(p.slowDown(com.histmersion.util.basemap.RangeHttp.StatusCode(429), Some(com.histmersion.util.basemap.RetryPolicy.RetryAfterHeader("2")), 0, com.histmersion.util.basemap.RetryPolicy.MaxRetries(3), com.histmersion.util.basemap.RetryPolicy.RetryAfterCap(300), now), Some(RetryWait(com.histmersion.util.basemap.RetryWait.Ms(2000L), com.histmersion.util.basemap.RetryWait.Why("HTTP 429"))))
    assertEquals(p.slowDown(com.histmersion.util.basemap.RangeHttp.StatusCode(503), None, 1, com.histmersion.util.basemap.RetryPolicy.MaxRetries(3), com.histmersion.util.basemap.RetryPolicy.RetryAfterCap(300), now), Some(RetryWait(com.histmersion.util.basemap.RetryWait.Ms(2000L), com.histmersion.util.basemap.RetryWait.Why("HTTP 503"))))
    assertEquals(p.slowDown(com.histmersion.util.basemap.RangeHttp.StatusCode(503), None, 3, com.histmersion.util.basemap.RetryPolicy.MaxRetries(3), com.histmersion.util.basemap.RetryPolicy.RetryAfterCap(300), now), None, "out of retries: the answer is taken")
    assertEquals(p.slowDown(com.histmersion.util.basemap.RangeHttp.StatusCode(429), Some(com.histmersion.util.basemap.RetryPolicy.RetryAfterHeader("900")), 0, com.histmersion.util.basemap.RetryPolicy.MaxRetries(3), com.histmersion.util.basemap.RetryPolicy.RetryAfterCap(300), now), None, "asked to wait too long")
    assertEquals(p.slowDown(com.histmersion.util.basemap.RangeHttp.StatusCode(206), None, 0, com.histmersion.util.basemap.RetryPolicy.MaxRetries(3), com.histmersion.util.basemap.RetryPolicy.RetryAfterCap(300), now), None)
    assertEquals(p.slowDown(com.histmersion.util.basemap.RangeHttp.StatusCode(404), None, 0, com.histmersion.util.basemap.RetryPolicy.MaxRetries(3), com.histmersion.util.basemap.RetryPolicy.RetryAfterCap(300), now), None)
    assertEquals((p.backoffMs(0), p.backoffMs(2)), (1000L, 4000L))

  test("each sea-floor setting is the base map's grid of the same name"):
    val settings = SeaFloorSource.values.toList
    assertEquals(settings.map(BasemapWiring.seaFloor), List(SeaFloorGrid.Etopo1, SeaFloorGrid.Gebco))
    assertEquals(settings.map(BasemapWiring.seaFloor(_).wire), settings.map(_.wire))

  test("basemap.dir, else the per-user cache's basemap, else init.data-dir's"):
    assertEquals(UserPaths.basemapDir(com.histmersion.config.UserPaths.BasemapDir(" /y/maps "), com.histmersion.config.RuntimeConfig.DataDir("/d"), _ => None), Path.of("/y/maps"))
    assertEquals(UserPaths.basemapDir(com.histmersion.config.UserPaths.BasemapDir(""), com.histmersion.config.RuntimeConfig.DataDir("/d"), Map("HOME" -> "/h").get), Path.of("/h/.cache/histmersion/basemap"))
    assertEquals(UserPaths.basemapDir(com.histmersion.config.UserPaths.BasemapDir(""), com.histmersion.config.RuntimeConfig.DataDir("/d"), _ => None), Path.of("/d/basemap"))
