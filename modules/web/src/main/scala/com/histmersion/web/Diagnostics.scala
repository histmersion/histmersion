package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** What the page holds, readable from outside (#1949): `window.histmersionDiagnostics()` answers every bounded cache's
  * entries and weight against its ceilings, the names the map places, and the findings kept for time-only redraws. The
  * browser regressions (`modules/web/test/perf.test.mjs`) and the baseline read it after a soak session to tell a cache
  * that stays bounded from one that grows. Read-only and cheap: counts, no copies.
  */
object Diagnostics:
  type CacheName = CacheName.T
  object CacheName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Every cache and its figures, by name. */

  def caches: List[(com.histmersion.web.Diagnostics.CacheName, BoundedCache.Stats)] =
    List(
      "places.geo" -> MapView.geoCacheStats,
      "ident" -> IdentLink.cacheStats,
      "places.labels" -> PlacesInput.cacheStats
    ) ++ Preview.cacheStats ++ MapLabels.stats ++
      List("findings.kept" -> BoundedCache.Stats(App.keptFindings, 0, 0L, 0L))

  /** Pure: the figures as a plain object (one per cache: entries, maxEntries, weight, maxWeight). */
  def toJs(cs: List[(com.histmersion.web.Diagnostics.CacheName, BoundedCache.Stats)]): js.Object =
    val o = js.Dictionary.empty[js.Any]
    cs.foreach { (n, s) =>
      o(n) = js.Dynamic.literal(
        entries = s.entries,
        maxEntries = s.maxEntries,
        weight = s.weight.toDouble,
        maxWeight = if s.maxWeight == Long.MaxValue then -1.0 else s.maxWeight.toDouble
      )
    }
    js.Dynamic.literal(caches = o).asInstanceOf[js.Object]

  def install(): Unit =
    dom.window.asInstanceOf[js.Dynamic].histmersionDiagnostics = (() =>
      val o = toJs(caches).asInstanceOf[js.Dynamic]
      // the map: the points its cluster layer holds, the DOM markers drawn, the shapes drawn
      if com.histmersion.web.map.TheMap.isUp then
        o.map = js.Dynamic.literal(
          points = MapView.dots.size,
          markers = com.histmersion.web.map.TheMap.markerCount,
          animations = MapView.dots.animations
        )
      o.asInstanceOf[js.Object]
    ): js.Function0[js.Object]
