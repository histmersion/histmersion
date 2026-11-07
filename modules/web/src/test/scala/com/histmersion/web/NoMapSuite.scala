package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.web.map.{Capabilities, NoMap}
import com.histmersion.web.map.maplibre.MapLibreEngine

/** The map without WebGL 2: why it cannot be drawn, and what its place says. Pure (the DOM half is the browser test "no
  * WebGL 2").
  */
class NoMapSuite extends munit.FunSuite:

  test("MapLibre needs WebGL 2: WebGL off, or WebGL 1 only, is said as such; a software WebGL 2 draws"):
    assertEquals(
      MapLibreEngine.unsupported(Capabilities(webgl = com.histmersion.web.map.Capabilities.Webgl(false), software = com.histmersion.web.map.Capabilities.Software(true), renderer = com.histmersion.web.map.Capabilities.Renderer(""), webgl2 = com.histmersion.web.map.Capabilities.Webgl2(false))),
      Some("this browser draws no WebGL (it is turned off, or its graphics driver is blocked)")
    )
    assertEquals(
      MapLibreEngine.unsupported(Capabilities(webgl = com.histmersion.web.map.Capabilities.Webgl(true), software = com.histmersion.web.map.Capabilities.Software(false), renderer = com.histmersion.web.map.Capabilities.Renderer("Apple M4"), webgl2 = com.histmersion.web.map.Capabilities.Webgl2(false))),
      Some("this browser draws WebGL 1 only, not WebGL 2")
    )
    assertEquals(
      MapLibreEngine.unsupported(Capabilities(webgl = com.histmersion.web.map.Capabilities.Webgl(true), software = com.histmersion.web.map.Capabilities.Software(true), renderer = com.histmersion.web.map.Capabilities.Renderer("SwiftShader"))),
      None
    )

  test("the message says why, then what to do, and that the rest works; markup is escaped"):
    val (head, body) = NoMap.message(com.histmersion.web.map.NoMapSurface.Why("this browser draws WebGL 1 only, not WebGL 2"))
    assertEquals(head, "The map cannot be drawn here: this browser draws WebGL 1 only, not WebGL 2.")
    assert(
      body.contains("online ones and the local ones") && body.contains("hardware") && body.contains(
        "work without the map"
      )
    )
    assert(NoMap.html(com.histmersion.web.map.NoMapSurface.Why("the map engine did not start (<b>x</b>)")).contains("(&lt;b&gt;x&lt;/b&gt;)"))
