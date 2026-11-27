package com.histmersion
package web
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.web.map.{Capabilities, NoMap}
import com.histmersion.web.map.maplibre.MapLibreEngine
import com.histmersion.web.map.NoMapSurface
import com.histmersion.lint.{tolerated, LintRule}

/** The map without WebGL 2: why it cannot be drawn, and what its place says. Pure (the DOM half is the browser test "no
  * WebGL 2").
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class NoMapSuite extends munit.FunSuite:

  test("MapLibre needs WebGL 2: WebGL off, or WebGL 1 only, is said as such; a software WebGL 2 draws"):
    assertEquals(
      MapLibreEngine
        .unsupported(
          Capabilities(
            webgl = Capabilities.Webgl.Unsupported,
            software = Capabilities.Software.Software,
            renderer = Capabilities.Renderer(""),
            webgl2 = Capabilities.Webgl2.Unsupported
          )
        )
        .map(_.sentence),
      Some("this browser draws no WebGL (it is turned off, or its graphics driver is blocked)")
    )
    assertEquals(
      MapLibreEngine
        .unsupported(
          Capabilities(
            webgl = Capabilities.Webgl.Supported,
            software = Capabilities.Software.Hardware,
            renderer = Capabilities.Renderer("Apple M4"),
            webgl2 = Capabilities.Webgl2.Unsupported
          )
        )
        .map(_.sentence),
      Some("this browser draws WebGL 1 only, not WebGL 2")
    )
    assertEquals(
      MapLibreEngine.unsupported(
        Capabilities(
          webgl = Capabilities.Webgl.Supported,
          software = Capabilities.Software.Software,
          renderer = Capabilities.Renderer("SwiftShader")
        )
      ),
      None
    )

  test("the message says why, then what to do, and that the rest works; markup is escaped"):
    val (head, body) = NoMap.message(Problem.Unclassified(Legacy.Text("this browser draws WebGL 1 only, not WebGL 2")))
    assertEquals(head, "The map cannot be drawn here: this browser draws WebGL 1 only, not WebGL 2.")
    assert(
      body.value.contains("online ones and the local ones") && body.value.contains("hardware") && body.value.contains(
        "work without the map"
      )
    )
    assert(
      NoMap
        .html(Problem.Unclassified(Legacy.Text("the map engine did not start (<b>x</b>)")))
        .value
        .contains("(&lt;b&gt;x&lt;/b&gt;)")
    )
