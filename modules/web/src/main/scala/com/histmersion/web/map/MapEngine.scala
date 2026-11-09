package com.histmersion
package web.map
import com.histmersion.util.problem.Problem

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** Makes surfaces. */
trait MapEngine:
  /** What the browser can draw, before any map is made. */
  def probe(): Capabilities

  /** Why this engine cannot draw in a browser with `c` (None: it can). */
  def unsupported(c: Capabilities): Option[Problem] = None

  /** A surface in `el`, its zoom limits and pixel ratio (`None`: the screen's). */
  def create(el: dom.HTMLElement, maxZoom: MapEngine.MaxZoom, pixelRatio: Option[MapEngine.PixelRatio]): MapSurface

object MapEngine:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Double, Pure]
  type PixelRatio = PixelRatio.T
  object PixelRatio extends Newtype[Double, Pure]
