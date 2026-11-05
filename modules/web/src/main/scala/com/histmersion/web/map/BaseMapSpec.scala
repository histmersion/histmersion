package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.basemap.Basemap

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
      reduced: BaseMapSpec.Vector.Reduced = false
  )

  /** An online raster map: its tile URL template (`{s}` for its subdomains), its deepest zoom, its credits (HTML),
    * drawn inverted (`dark`: the light tiles as a dark map) at `opacity`.
    */
  case Raster(template: BaseMapSpec.Raster.Template, maxZoom: BaseMapSpec.Raster.MaxZoom, attribution: BaseMapSpec.Raster.Attribution, dark: BaseMapSpec.Raster.Dark = false, opacity: BaseMapSpec.Raster.Opacity = 0.85)

object BaseMapSpec:
  object Vector:
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contours = Contours.T
    object Contours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeaDepth = SeaDepth.T
    object SeaDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reduced = Reduced.T
    object Reduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Raster:
    type Template = Template.T
    object Template extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxZoom = MaxZoom.T
    object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attribution = Attribution.T
    object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dark = Dark.T
    object Dark extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Opacity = Opacity.T
    object Opacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
