package com.histmersion.web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** MapLibre GL JS 6.12.0 (BSD-3-Clause, vendored at `web/vendor/maplibre-gl/`), the map itself
  * (docs/research-map-styles.md, option C). It is ES modules only: the page imports it before the app and puts the
  * module on `window.maplibregl` (`web/index.html`), so this facade reads a global — the app's own bundle has no import
  * a test runner would have to resolve. Only what the app uses.
  */
@js.native
@JSGlobal("maplibregl")
object MapLibre extends js.Object:
  /** A page protocol (`name://…`) answering tile requests. */
  def addProtocol(name: String, load: js.Function2[js.Dynamic, js.Dynamic, js.Promise[js.Dynamic]]): Unit = js.native
