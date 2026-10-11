package com.histmersion.web

import com.histmersion.core.MapStack
import com.histmersion.web.leaflet.LeafletMap

/** The map's panes ([[MapStack]]): every layer names the pane it belongs in, so a dot is above a
  * region however late the region arrived. Their order is the stylesheet's `z-index`. */
object MapPanes:

  val Areas: String      = MapStack.Pane.Areas.name
  val AreaPoints: String = MapStack.Pane.AreaPoints.name
  val Labels: String     = MapStack.Pane.Labels.name
  val Sites: String      = MapStack.Pane.Sites.name
  val Events: String     = MapStack.Pane.Events.name

  /** Create the panes Leaflet does not have yet. Idempotent: the map, the label pass and playback
    * all call it before they draw. */
  def ensure(map: LeafletMap): Unit =
    MapStack.order.filterNot(_.builtIn).foreach { p =>
      if map.getPane(p.name).isEmpty then
        val _ = map.createPane(p.name)
    }
