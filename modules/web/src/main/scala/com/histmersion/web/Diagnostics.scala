package com.histmersion
package web

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.web.map.TheMap

/** What the page holds, readable from outside (#1949): `window.histmersionDiagnostics()` answers every bounded cache's
  * entries and weight against its ceilings, the names the map places, and the findings kept for time-only redraws. The
  * browser regressions (`modules/web/test/perf.test.mjs`) and the baseline read it after a soak session to tell a cache
  * that stays bounded from one that grows. Read-only and cheap: counts, no copies.
  */
object Diagnostics:
  type CacheName = CacheName.T
  object CacheName extends RefinedType[String, Pure]

  /** Every cache and its figures, by name. */

  def caches: List[(CacheName, BoundedCache.Stats)] =
    List(
      CacheName("places.geo") -> MapView.geoCacheStats,
      CacheName("ident") -> IdentLink.cacheStats,
      CacheName("places.labels") -> PlacesInput.cacheStats
    ) ++ Preview.cacheStats.map((n, s) => CacheName(n.value) -> s) ++ MapLabels.stats.map((n, s) => CacheName(n.value) -> s) ++
      List(CacheName("findings.kept") -> BoundedCache.Stats(App.keptFindings, BoundedCache.Stats.MaxEntries(0), BoundedCache.Stats.Weight(0L), BoundedCache.Stats.MaxWeight(0L)))

  /** Pure: the figures as a plain object (one per cache: entries, maxEntries, weight, maxWeight). */
  def toJs(cs: List[(CacheName, BoundedCache.Stats)]): js.Object =
    val o = js.Dictionary.empty[js.Any]
    cs.foreach { (n, s) =>
      o(n.value) = js.Dynamic.literal(
        entries = s.entries.value,
        maxEntries = s.maxEntries.value,
        weight = s.weight.value.toDouble,
        maxWeight = if s.maxWeight.value == Long.MaxValue then -1.0 else s.maxWeight.value.toDouble
      )
    }
    js.Dynamic.literal(caches = o).asInstanceOf[js.Object]

  def install(): Unit =
    dom.window.asInstanceOf[js.Dynamic].histmersionDiagnostics = (() =>
      val o = toJs(caches).asInstanceOf[js.Dynamic]
      // the map: the points its cluster layer holds, the DOM markers drawn, the shapes drawn
      if TheMap.isUp then
        o.map = js.Dynamic.literal(
          points = MapView.dots.size.value,
          markers = TheMap.markerCount.value,
          animations = MapView.dots.animations.value
        )
      o.asInstanceOf[js.Object]
    ): js.Function0[js.Object]
