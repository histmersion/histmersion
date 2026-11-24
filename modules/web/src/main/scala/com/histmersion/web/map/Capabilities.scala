package com.histmersion
package web.map
import com.histmersion.util.problem.{Legacy, Problem}

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** What the browser can draw: `software` when its WebGL is rasterised on the CPU (SwiftShader, llvmpipe…) — the GL map
  * then breaks the interaction budgets (docs/research-map-styles.md §3.2) and the app draws its cheapest map.
  * `renderer` is the string it was told.
  */
final case class Capabilities(
    webgl: Capabilities.Webgl,
    software: Capabilities.Software,
    renderer: Capabilities.Renderer,
    webgl2: Capabilities.Webgl2 = Capabilities.Webgl2(true)
)

object Capabilities:
  /** Pure: why an engine that needs WebGL 2 cannot draw in a browser with `c`, or None when it can. */

  type Webgl = Webgl.T
  object Webgl extends Newtype[Boolean, Pure]
  type Software = Software.T
  object Software extends Newtype[Boolean, Pure]
  type Renderer = Renderer.T
  object Renderer extends Newtype[String, Pure]
  type Webgl2 = Webgl2.T
  object Webgl2 extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def withoutWebgl2(c: Capabilities): Option[Problem] =
    if !c.webgl.value then
      Some(
        Problem.Unclassified(
          Legacy.Text("this browser draws no WebGL (it is turned off, or its graphics driver is blocked)")
        )
      )
    else if !c.webgl2.value then Some(Problem.Unclassified(Legacy.Text("this browser draws WebGL 1 only, not WebGL 2")))
    else None

  /** Pure: a WebGL renderer string that names a software rasteriser. */
  /** A renderer name that hides the GPU (Chromium's and Safari's answer to `RENDERER`): ask the debug extension
    * instead. Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def genericRenderer(renderer: Renderer): Boolean =
    val r = renderer.value.trim.toLowerCase
    r.isEmpty || r == "webkit webgl" || r == "webgl" || r.startsWith("mozilla")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isSoftware(renderer: Renderer): Boolean =
    val r = renderer.value.toLowerCase
    List("swiftshader", "llvmpipe", "softpipe", "software", "basic render", "mesa offscreen", "lavapipe").exists(
      r.contains
    )
