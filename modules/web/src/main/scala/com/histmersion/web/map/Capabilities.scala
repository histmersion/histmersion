package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** What the browser can draw: `software` when its WebGL is rasterised on the CPU (SwiftShader, llvmpipe…) — the GL map
  * then breaks the interaction budgets (docs/research-map-styles.md §3.2) and the app draws its cheapest map.
  * `renderer` is the string it was told.
  */
final case class Capabilities(webgl: Capabilities.Webgl, software: Capabilities.Software, renderer: Capabilities.Renderer, webgl2: Capabilities.Webgl2 = com.histmersion.web.map.Capabilities.Webgl2(true))

object Capabilities:
  /** Pure: why an engine that needs WebGL 2 cannot draw in a browser with `c`, or None when it can. */

  type Webgl = Webgl.T
  object Webgl extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Software = Software.T
  object Software extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Renderer = Renderer.T
  object Renderer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Webgl2 = Webgl2.T
  object Webgl2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def withoutWebgl2(c: Capabilities): Option[com.histmersion.web.map.MapEngine.Problem] =
    if !c.webgl.value then Some(com.histmersion.web.map.MapEngine.Problem("this browser draws no WebGL (it is turned off, or its graphics driver is blocked)"))
    else if !c.webgl2.value then Some(com.histmersion.web.map.MapEngine.Problem("this browser draws WebGL 1 only, not WebGL 2"))
    else None

  /** Pure: a WebGL renderer string that names a software rasteriser. */
  /** A renderer name that hides the GPU (Chromium's and Safari's answer to `RENDERER`): ask the debug extension
    * instead. Pure.
    */
  def genericRenderer(renderer: com.histmersion.web.map.Capabilities.Renderer): Boolean =
    val r = renderer.value.trim.toLowerCase
    r.isEmpty || r == "webkit webgl" || r == "webgl" || r.startsWith("mozilla")

  def isSoftware(renderer: com.histmersion.web.map.Capabilities.Renderer): Boolean =
    val r = renderer.value.toLowerCase
    List("swiftshader", "llvmpipe", "softpipe", "software", "basic render", "mesa offscreen", "lavapipe").exists(
      r.contains
    )
