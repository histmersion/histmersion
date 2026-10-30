package com.histmersion.web.map

import com.histmersion.api.MapStack
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The page's one map: its surface, and the DOM panes over it ([[MapStack]]'s order). */
object TheMap:

  // `var`: the surface, made once by `install`
  private var s: MapSurface = null

  def surface: MapSurface = s
  def isUp: Boolean = s != null

  private val panes = mutable.LinkedHashMap.empty[String, MarkerPane]

  def pane(p: MapStack.Pane): MarkerPane = panes(p.name)

  /** Take `surface` as the page's map: its panes over it, every marker following each frame of a move. */
  def install(surface: MapSurface): Unit =
    s = surface
    MapStack.domPanes.foreach { p =>
      val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      el.className = s"hd-pane ${p.cssClass}"
      surface.overlayRoot.appendChild(el)
      panes(p.name) = MarkerPane(el, surface)
    }
    surface.on(MapEventKind.Move)(_ => reposition())
    surface.on(MapEventKind.Resize)(_ => reposition())

  def reposition(): Unit =
    // the view read once for every marker: a layout read between two markers' writes forced a layout each
    val v = View.of(s)
    panes.values.foreach(_.reposition(v))

  /** Every marker on the map now, by pane (for [[com.histmersion.web.Diagnostics]]). */
  def markerCount: Int = panes.values.map(_.size).sum
