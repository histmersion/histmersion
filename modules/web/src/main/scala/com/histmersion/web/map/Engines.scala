package com.histmersion
package web.map
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.web.map.maplibre.MapLibreEngine
import scala.util.control.NonFatal
import com.histmersion.lint.{tolerated, LintRule}

/** The engine the app runs on: the one place outside the adapter that names it. */
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
object Engines:
  def default: MapEngine = MapLibreEngine

  /** The page's map in `el` — or, when the browser cannot run the engine or the engine fails to start, a
    * [[NoMapSurface]] that says so in the map's place and draws nothing. `pixelRatio` from what the browser can draw.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def surface(
      el: org.scalajs.dom.HTMLElement,
      maxZoom: MapSurface.MaxZoom,
      pixelRatio: Capabilities => Option[MapEngine.PixelRatio],
      engine: MapEngine = default
  ): MapSurface =
    val caps = engine.probe()
    engine.unsupported(caps) match
      case Some(why) => NoMapSurface(el, why, caps, maxZoom)
      case None      =>
        try engine.create(el, maxZoom, pixelRatio(caps))
        catch
          case NonFatal(e) =>
            val msg = Option(e.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
            NoMapSurface(el, Problem.Unclassified(Legacy.Text(s"the map engine did not start ($msg)")), caps, maxZoom)
