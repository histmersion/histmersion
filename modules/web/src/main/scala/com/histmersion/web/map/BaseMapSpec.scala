package com.histmersion.web.map

import com.histmersion.api.basemap.BasemapStatus
import org.scalajs.dom

import scala.scalajs.js

/** A base map, as the app describes it; the adapter makes its library's style of it. */
enum BaseMapSpec:
  /** Nothing yet: the page's own background. */
  case Blank

  /** One of the vector styles over the server's extracts (`status`, what it has), names in `lang` (`local`: each in its
    * own script; `names = false`: none), contours where the relief is deeper than the extract — a focus region or the
    * on-demand relief (Physical) — and the sea coloured by its depth (`seaDepth`, where the sea floor is downloaded).
    * `reduced`: the cheapest drawing of it (no relief) for a browser without a GPU.
    */
  case Vector(
      style: VectorStyle,
      status: BasemapStatus,
      lang: String,
      names: Boolean,
      contours: Boolean,
      seaDepth: Boolean,
      reduced: Boolean = false
  )

  /** An online raster map: its tile URL template (`{s}` for its subdomains), its deepest zoom, its credits (HTML),
    * drawn inverted (`dark`: the light tiles as a dark map) at `opacity`.
    */
  case Raster(template: String, maxZoom: Int, attribution: String, dark: Boolean = false, opacity: Double = 0.85)
