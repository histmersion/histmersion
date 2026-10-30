package com.histmersion.web

import scala.annotation.tailrec
import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Keeps the overlays attached to a map equal to a WANTED set. Idempotent — applying the same set twice attaches
  * nothing twice — which is what lets the map subscribe to `UiState.layers` and a restored state, a toggle and a replay
  * all go through one path.
  */
final class LayerSync(attach: MapLayer => Unit, detach: MapLayer => Unit):
  private val on = scala.collection.mutable.Set.empty[MapLayer]

  def attached: Set[MapLayer] = on.toSet

  def apply(wanted: Set[MapLayer]): Unit =
    MapLayer.values.foreach { l =>
      if wanted(l) && !on(l) then { on += l; attach(l) }
      else if !wanted(l) && on(l) then { on -= l; detach(l) }
    }
