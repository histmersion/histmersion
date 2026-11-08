package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import io.github.iltotore.iron.constraint.any.Pure

/** A map event: the map, and for a pointer event where it happened. */
@js.native
trait GlEvent extends js.Object:
  val `type`: GlEvent.Type = js.native
  val point: js.UndefOr[ScreenPoint] = js.native
  val lngLat: js.UndefOr[LngLat] = js.native
  val originalEvent: js.UndefOr[dom.Event] = js.native

object GlEvent:
  type Type = Type.T
  object Type extends Newtype[String, Pure]
