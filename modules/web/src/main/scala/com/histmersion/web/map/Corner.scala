package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js

/** A corner of the map, where controls (the legend, the notice and the credits, the zoom buttons) go. Every corner
  * stacks above the marker panes and below the popups (#3844): the map's z-index scale is the stylesheet's `--z-map-*`
  * tokens — the canvas, then the panes (core MapStack), then the corners (`.hd-map-corner`, `--z-map-controls`), then
  * the popups (`--z-map-popups`); windows and modals open over the whole map.
  */
enum Corner derives CanEqual:
  case TopLeft, TopRight, BottomLeft, BottomRight
