package com.histmersion.web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A map event: the map, and for a pointer event where it happened. */
@js.native
trait GlEvent extends js.Object:
  val `type`: com.histmersion.web.map.maplibre.GlEvent.Type = js.native
  val point: js.UndefOr[ScreenPoint] = js.native
  val lngLat: js.UndefOr[LngLat] = js.native
  val originalEvent: js.UndefOr[dom.Event] = js.native

object GlEvent:
  type Type = Type.T
  object Type extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
