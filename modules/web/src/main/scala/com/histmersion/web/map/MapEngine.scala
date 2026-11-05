package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** Makes surfaces. */
trait MapEngine:
  /** What the browser can draw, before any map is made. */
  def probe(): Capabilities

  /** Why this engine cannot draw in a browser with `c` (None: it can). */
  def unsupported(c: Capabilities): Option[com.histmersion.web.map.MapEngine.UnsupportedResult] = None

  /** A surface in `el`, its zoom limits and pixel ratio (`None`: the screen's). */
  def create(el: dom.HTMLElement, maxZoom: com.histmersion.web.map.MapEngine.CreateMaxZoom, pixelRatio: Option[com.histmersion.web.map.MapEngine.CreatePixelRatio]): MapSurface

object MapEngine:
  type UnsupportedResult = UnsupportedResult.T
  object UnsupportedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateMaxZoom = CreateMaxZoom.T
  object CreateMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatePixelRatio = CreatePixelRatio.T
  object CreatePixelRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
