package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.basemap.Basemap
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
      status: Basemap.Status,
      lang: BaseMapSpec.Vector.Lang,
      names: BaseMapSpec.Vector.Names,
      contours: BaseMapSpec.Vector.Contours,
      seaDepth: BaseMapSpec.Vector.SeaDepth,
      reduced: BaseMapSpec.Vector.Reduced = BaseMapSpec.Vector.Reduced(false)
  )

  /** An online raster map: its tile URL template (`{s}` for its subdomains), its deepest zoom, its credits (HTML),
    * drawn inverted (`dark`: the light tiles as a dark map) at `opacity`.
    */
  case Raster(template: BaseMapSpec.Raster.Template, maxZoom: BaseMapSpec.Raster.MaxZoom, attribution: BaseMapSpec.Raster.Attribution, dark: BaseMapSpec.Raster.Dark = BaseMapSpec.Raster.Dark(false), opacity: BaseMapSpec.Raster.Opacity = BaseMapSpec.Raster.Opacity(0.85))

object BaseMapSpec:
  object Vector:
    type Lang = Lang.T
    object Lang extends RefinedType[String, Pure]
    type Names = Names.T
    object Names extends RefinedType[Boolean, Pure]
    type Contours = Contours.T
    object Contours extends RefinedType[Boolean, Pure]
    type SeaDepth = SeaDepth.T
    object SeaDepth extends RefinedType[Boolean, Pure]
    type Reduced = Reduced.T
    object Reduced extends RefinedType[Boolean, Pure]
  object Raster:
    export com.histmersion.api.basemap.BasemapApi.{TileTemplate as Template}
    type MaxZoom = MaxZoom.T
    object MaxZoom extends RefinedType[Int, Pure]
    type Attribution = Attribution.T
    object Attribution extends RefinedType[String, Pure]
    type Dark = Dark.T
    object Dark extends RefinedType[Boolean, Pure]
    type Opacity = Opacity.T
    object Opacity extends RefinedType[Double, Pure]
