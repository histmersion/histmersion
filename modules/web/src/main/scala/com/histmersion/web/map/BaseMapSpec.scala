package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.basemap.Basemap
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A base map, as the app describes it; the adapter makes its library's style of it. */
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
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
      status: Basemap.Status,
      lang: BaseMapSpec.Vector.Lang,
      names: BaseMapSpec.Vector.Names,
      contours: BaseMapSpec.Vector.Contours,
      seaDepth: BaseMapSpec.Vector.SeaDepth,
      reduced: BaseMapSpec.Vector.Reduced = BaseMapSpec.Vector.Reduced.Full
  )

  /** An online raster map: its tile URL template (`{s}` for its subdomains), its deepest zoom, its credits (HTML),
    * drawn inverted (`dark`: the light tiles as a dark map) at `opacity`.
    */
  case Raster(
      template: BaseMapSpec.Raster.Template,
      maxZoom: BaseMapSpec.Raster.MaxZoom,
      attribution: BaseMapSpec.Raster.Attribution,
      dark: BaseMapSpec.Raster.Dark = BaseMapSpec.Raster.Dark.Light,
      opacity: BaseMapSpec.Raster.Opacity = BaseMapSpec.Raster.Opacity(0.85)
  )

object BaseMapSpec:
  object Vector:
    type Lang = Lang.T
    object Lang extends Newtype[String, Pure]
    enum Names derives CanEqual:
      case Shown, Hidden
    object Names:
      extension (names: Names) def shown: Boolean = names == Shown
    enum Contours derives CanEqual:
      case Shown, Hidden
    object Contours:
      extension (contours: Contours) def shown: Boolean = contours == Shown
    enum SeaDepth derives CanEqual:
      case Shown, Hidden
    object SeaDepth:
      extension (seaDepth: SeaDepth) def shown: Boolean = seaDepth == Shown
    enum Reduced derives CanEqual:
      case Reduced, Full
    object Reduced:
      extension (reduced: Reduced) def reduced: Boolean = reduced == Reduced
  object Raster:
    export api.basemap.BasemapApi.TileTemplate as Template
    type MaxZoom = MaxZoom.T
    object MaxZoom extends Newtype[Int, Pure]
    type Attribution = Attribution.T
    object Attribution extends Newtype[String, Pure]
    enum Dark derives CanEqual:
      case Dark, Light
    object Dark:
      extension (dark: Dark) def dark: Boolean = dark == Dark
    type Opacity = Opacity.T
    object Opacity extends Newtype[Double, Pure]
