package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** A DOM marker at (`lat`, `lon`): its element's top-left at the point, or — `box` given — the box centred on it.
  * `.hd-marker` is the stylesheet's hook for every marker (and the map's sign that a pointer event is the marker's,
  * [[MapEvent.overMarker]]); the element answers the pointer and the keyboard itself (its kind binds them).
  */
final class DomMarker(
    private var latV: Double,
    private var lonV: Double,
    val el: dom.HTMLElement,
    box: Option[(Int, Int)] = None
):
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

object DomMarker:
  /** A marker element of class `cls` holding `html`. */
  def element(cls: String, html: String): dom.HTMLElement =
    val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    el.className = cls
    el.innerHTML = html
    el
