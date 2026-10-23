package com.histmersion.web.map

import com.histmersion.core.MapStack
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

/** The view as the markers are placed in it, read once a frame. */
final case class View(surface: MapSurface, centreLon: Double, w: Double, h: Double)

object View:
  def of(s: MapSurface): View =
    val (w, h) = s.size
    View(s, s.center.lon, w, h)

/** One pane of DOM markers over the map: each at its map point, moved every frame the map moves (one
  * `transform` write per marker in view; one far outside the view is hidden, not moved). */
final class MarkerPane(val el: dom.HTMLElement, surface: MapSurface):
  private val markers = js.Set[DomMarker]()

  // the pointer and the keyboard, for every marker of the pane at once (one set of listeners, not one per
  // marker: a few thousand points are drawn and re-drawn as the map zooms)
  private def markerOf(t: dom.EventTarget): Option[DomMarker] =
    Option(t).collect { case e: dom.Element => e }.flatMap(e => Option(e.closest(".hd-marker")))
      .flatMap(e => Option(e.asInstanceOf[js.Dynamic].hdMarker).filterNot(js.isUndefined).map(_.asInstanceOf[DomMarker]))
  private def related(e: dom.MouseEvent): Option[DomMarker] = markerOf(e.relatedTarget)
  el.addEventListener("mouseover", { (e: dom.MouseEvent) =>
    markerOf(e.target).filter(m => !related(e).contains(m)).foreach(m => Option(m.handlers).foreach(_.over(e)))
  })
  el.addEventListener("mouseout", { (e: dom.MouseEvent) =>
    markerOf(e.target).filter(m => !related(e).contains(m)).foreach(m => Option(m.handlers).foreach(_.out(e)))
  })
  el.addEventListener("mousemove", { (e: dom.MouseEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.move(e))) })
  el.addEventListener("click", { (e: dom.MouseEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.click(e))) })
  el.addEventListener("focusin", { (e: dom.FocusEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.focus(e))) })
  el.addEventListener("focusout", { (e: dom.FocusEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.blur(e))) })
  el.addEventListener("keydown", { (e: dom.KeyboardEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.key(e))) })

  def add(m: DomMarker): Unit =
    if !markers.contains(m) then
      markers.add(m)
      el.appendChild(m.el)
      m.place(View.of(surface))
      m.added()

  def remove(m: DomMarker): Unit =
    if markers.contains(m) then
      markers.remove(m)
      if m.el.parentNode == el then { val _ = el.removeChild(m.el) }
      m.removed()

  def has(m: DomMarker): Boolean = markers.contains(m)

  def size: Int = markers.size

  def reposition(v: View): Unit = markers.foreach(_.place(v))

/** A DOM marker at (`lat`, `lon`): its element's top-left at the point, or — `box` given — the box
  * centred on it. `.hd-marker` is the stylesheet's hook for every marker (and the map's sign that a
  * pointer event is the marker's, [[MapEvent.overMarker]]); the element answers the pointer and the
  * keyboard itself (its kind binds them). */
final class DomMarker(private var latV: Double, private var lonV: Double, val el: dom.HTMLElement,
    box: Option[(Int, Int)] = None):
  // `var`s: the last position written, whether it is hidden off screen, and the handlers of being shown
  private var lastX = Double.NaN
  private var lastY = Double.NaN
  private var hidden = false
  private val onAdd = mutable.ListBuffer.empty[() => Unit]
  private val onRemove = mutable.ListBuffer.empty[() => Unit]

  el.classList.add("hd-marker")
  el.asInstanceOf[js.Dynamic].hdMarker = this.asInstanceOf[js.Any]
  /** What it does with the pointer and the keyboard (its pane dispatches to it); `null`: nothing. */
  var handlers: MarkerHandlers = null
  // its box as data (`.hd-marker.has-box` centres it); its place is `--tx`/`--ty` (`.hd-marker`'s transform)
  box.foreach { (w, h) =>
    el.classList.add("has-box")
    el.style.setProperty("--mw", s"${w}px")
    el.style.setProperty("--mh", s"${h}px")
  }

  def lat: Double = latV
  def lon: Double = lonV
  def at: LatLon = LatLon(latV, lonV)

  def setLatLng(lat: Double, lon: Double): Unit =
    latV = lat
    lonV = lon
    if TheMap.isUp then place(View.of(TheMap.surface))

  def onAdded(f: () => Unit): Unit = onAdd += f
  def onRemoved(f: () => Unit): Unit = onRemove += f
  private[map] def added(): Unit = onAdd.foreach(_())
  private[map] def removed(): Unit = onRemove.foreach(_())

  /** Where it is drawn now, container pixels (NaN before it was drawn). */
  def screen: (Double, Double) = (lastX, lastY)

  private[map] def place(v: View): Unit =
    // the copy of the world nearest the view's centre (the map draws the world's copies side by side)
    val lon = lonV + 360 * math.round((v.centreLon - lonV) / 360).toDouble
    val p = v.surface.project(LatLon(latV, lon))
    val (w, h) = (v.w, v.h)
    val out = p.x < -400 || p.y < -400 || p.x > w + 400 || p.y > h + 400
    if out != hidden then
      hidden = out
      el.classList.toggle("is-offscreen", out)
    if !out then
      val rx = math.round(p.x).toDouble
      val ry = math.round(p.y).toDouble
      if rx != lastX || ry != lastY then
        lastX = rx
        lastY = ry
        moveTo(rx, ry)

  /** Draw it at screen point (`x`, `y`) now (an animation's start), whatever the map says. */
  def moveTo(x: Double, y: Double): Unit =
    el.style.setProperty("--tx", s"${x}px")
    el.style.setProperty("--ty", s"${y}px")

  /** Its stacking among the markers of its pane. */
  def setZ(z: Int): Unit = el.style.setProperty("--z", z.toString)

/** A marker's answers to the pointer and the keyboard, dispatched by its pane: `over`/`out` once per
  * entering and leaving the marker (not its children), `focus`/`blur` for anything in it. */
trait MarkerHandlers:
  def over(e: dom.MouseEvent): Unit = ()
  def out(e: dom.MouseEvent): Unit = ()
  def move(e: dom.MouseEvent): Unit = ()
  def click(e: dom.MouseEvent): Unit = ()
  def focus(e: dom.FocusEvent): Unit = ()
  def blur(e: dom.FocusEvent): Unit = ()
  def key(e: dom.KeyboardEvent): Unit = ()

object DomMarker:
  /** A marker element of class `cls` holding `html`. */
  def element(cls: String, html: String): dom.HTMLElement =
    val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    el.className = cls
    el.innerHTML = html
    el
