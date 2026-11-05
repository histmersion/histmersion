package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** What the browser can draw: `software` when its WebGL is rasterised on the CPU (SwiftShader, llvmpipe…) — the GL map
  * then breaks the interaction budgets (docs/research-map-styles.md §3.2) and the app draws its cheapest map.
  * `renderer` is the string it was told.
  */
final case class Capabilities(webgl: Boolean, software: Boolean, renderer: String, webgl2: Boolean = true)

object Capabilities:
  /** Pure: why an engine that needs WebGL 2 cannot draw in a browser with `c`, or None when it can. */
  def withoutWebgl2(c: Capabilities): Option[String] =
    if !c.webgl then Some("this browser draws no WebGL (it is turned off, or its graphics driver is blocked)")
    else if !c.webgl2 then Some("this browser draws WebGL 1 only, not WebGL 2")
    else None

  /** Pure: a WebGL renderer string that names a software rasteriser. */
  /** A renderer name that hides the GPU (Chromium's and Safari's answer to `RENDERER`): ask the debug extension
    * instead. Pure.
    */
  def genericRenderer(renderer: String): Boolean =
    val r = renderer.trim.toLowerCase
    r.isEmpty || r == "webkit webgl" || r == "webgl" || r.startsWith("mozilla")

  def isSoftware(renderer: String): Boolean =
    val r = renderer.toLowerCase
    List("swiftshader", "llvmpipe", "softpipe", "software", "basic render", "mesa offscreen", "lavapipe").exists(
      r.contains
    )
