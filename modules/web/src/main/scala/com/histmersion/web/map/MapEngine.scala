package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Makes surfaces. */
trait MapEngine:
  /** What the browser can draw, before any map is made. */
  def probe(): Capabilities

  /** Why this engine cannot draw in a browser with `c` (None: it can). */
  def unsupported(c: Capabilities): Option[MapEngine.Problem] = None

  /** A surface in `el`, its zoom limits and pixel ratio (`None`: the screen's). */
  def create(el: dom.HTMLElement, maxZoom: MapEngine.MaxZoom, pixelRatio: Option[MapEngine.PixelRatio]): MapSurface

object MapEngine:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends RefinedType[Double, Pure]
  type PixelRatio = PixelRatio.T
  object PixelRatio extends RefinedType[Double, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
