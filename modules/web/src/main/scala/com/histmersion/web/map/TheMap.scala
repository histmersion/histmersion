package com.histmersion
package web.map

import com.histmersion.api.MapStack
import com.histmersion.web.Store
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The page's one map: its surface, and the DOM panes over it ([[MapStack]]'s order). */
object TheMap:
  // `var`: the surface, made once by `install`

  private var s: MapSurface = null

  def surface: MapSurface = s
  def isUp: Boolean = s != null

  private val panes = mutable.LinkedHashMap.empty[MapStack.Pane.Name, MarkerPane]

  private var ready = false
  private val waiting = mutable.ListBuffer.empty[MapSurface => Unit]

  def whenReady(f: MapSurface => Unit): Unit = if ready then f(s) else waiting += f

  def markReady(): Unit =
    ready = true
    val due = waiting.toList
    waiting.clear()
    due.foreach(_(s))

  def pane(p: MapStack.Pane): MarkerPane = panes(p.name)

  /** Take `surface` as the page's map: its panes over it, every marker following each frame of a move. */
  def install(surface: MapSurface): Unit =
    s = surface
    val els = MapStack.domPanes.map { p =>
      val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
      el.className = s"hd-pane ${p.cssClass}"
      surface.overlayRoot.appendChild(el)
      p -> el
    }
    canvas = Option
      .when(
        !Store.Browser.get(MarkerCanvas.StorageKey).contains("off") &&
          surface.capabilities.webgl == Capabilities.Webgl(true) &&
          surface.capabilities.webgl2 == Capabilities.Webgl2(true)
      )(())
      .flatMap(_ => els.find((p, _) => pointPane(p)))
      .map((_, first) => MarkerCanvas(surface.overlayRoot, first, surface.container))
    els.foreach { (p, el) =>
      val drawing = canvas.filter(_ => pointPane(p))
      panes(p.name) =
        MarkerPane(el, surface, drawing.fold(el)(_.group(s"${p.cssClass}")), m => drawing.foreach(_.prepare(m)))
    }
    surface.on(MapEventKind.Move)(_ => reposition())
    surface.on(MapEventKind.Resize) { _ =>
      canvas.foreach(_.resized())
      reposition()
    }

  private var canvas: Option[MarkerCanvas] = None

  private val pointPane: MapStack.Pane => Boolean = _.point == MapStack.Pane.Point(true)

  def reposition(): Unit =
    // the view read once for every marker: a layout read between two markers' writes forced a layout each
    val v = View.of(s)
    panes.values.foreach(_.reposition(v))
    canvas.foreach(_.paint(() => drawnPoints))

  private def drawnPoints: List[List[DomMarker]] =
    MapStack.domPanes.filter(pointPane).flatMap(p => panes.get(p.name)).map(pn => pn.live ++ pn.leaving)

  def markerCanvas: Option[MarkerCanvas] = canvas

  def drawnMarkerAt(clientX: Double, clientY: Double): Boolean = canvas.exists(_.hit(clientX, clientY).isDefined)

  def drawnMarkerElAt(clientX: Double, clientY: Double): Option[dom.Element] =
    canvas.flatMap(_.hit(clientX, clientY)).map(_.el)

  /** Every marker on the map now, by pane (for [[com.histmersion.web.Diagnostics]]). */
  def markerCount: MarkerPane.MarkerCount = MarkerPane.MarkerCount(panes.values.map(_.size.value).sum)
