package com.histmersion.server

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
    assertEquals(p.slowDown(429, Some("2"), 0, 3, 300, now), Some(RetryWait(2000L, "HTTP 429")))
    assertEquals(p.slowDown(503, None, 1, 3, 300, now), Some(RetryWait(2000L, "HTTP 503")))
    assertEquals(p.slowDown(503, None, 3, 3, 300, now), None, "out of retries: the answer is taken")
    assertEquals(p.slowDown(429, Some("900"), 0, 3, 300, now), None, "asked to wait too long")
    assertEquals(p.slowDown(206, None, 0, 3, 300, now), None)
    assertEquals(p.slowDown(404, None, 0, 3, 300, now), None)
    assertEquals((p.backoffMs(0), p.backoffMs(2)), (1000L, 4000L))

  test("each sea-floor setting is the base map's grid of the same name"):
    val settings = SeaFloorSource.values.toList
    assertEquals(settings.map(BasemapWiring.seaFloor), List(SeaFloorGrid.Etopo1, SeaFloorGrid.Gebco))
    assertEquals(settings.map(BasemapWiring.seaFloor(_).wire), settings.map(_.wire))

  test("basemap.dir, else the per-user cache's basemap, else init.data-dir's"):
    assertEquals(UserPaths.basemapDir(" /y/maps ", "/d", _ => None), Path.of("/y/maps"))
    assertEquals(UserPaths.basemapDir("", "/d", Map("HOME" -> "/h").get), Path.of("/h/.cache/histmersion/basemap"))
    assertEquals(UserPaths.basemapDir("", "/d", _ => None), Path.of("/d/basemap"))
