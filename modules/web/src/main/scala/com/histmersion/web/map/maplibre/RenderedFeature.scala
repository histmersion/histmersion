package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import io.github.iltotore.iron.constraint.any.Pure

/** A feature a query returned: its properties, its layer and source. */
@js.native
trait RenderedFeature extends js.Object:
  val id: js.UndefOr[js.Any] = js.native
  val properties: js.Dictionary[js.Any] = js.native
  val layer: js.Dynamic = js.native
  val source: RenderedFeature.Source = js.native

object RenderedFeature:
  type Source = Source.T
  object Source extends Newtype[String, Pure]
