package com.histmersion.web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A feature a query returned: its properties, its layer and source. */
@js.native
trait RenderedFeature extends js.Object:
  val id: js.UndefOr[js.Any] = js.native
  val properties: js.Dictionary[js.Any] = js.native
  val layer: js.Dynamic = js.native
  val source: String = js.native
