package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Html
import com.histmersion.web.HtmlSink
import com.histmersion.lint.{tolerated, LintRule}

/** A DOM marker at (`lat`, `lon`): its element's top-left at the point, or — `box` given — the box centred on it.
  * `.hd-marker` is the stylesheet's hook for every marker (and the map's sign that a pointer event is the marker's,
  * [[MapEvent.overMarker]]); the element answers the pointer and the keyboard itself (its kind binds them).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class DomMarker(
    private var latV: LatLon.Lat,
    private var lonV: LatLon.Lon,
    val el: dom.HTMLElement,
    box: Option[(DomMarker.BoxSize, DomMarker.BoxSize)] = None
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

  def lat: LatLon.Lat = latV
  def lon: LatLon.Lon = lonV
  def at: LatLon = LatLon(latV, lonV)

  def setLatLng(lat: LatLon.Lat, lon: LatLon.Lon): Unit =
    latV = lat
    lonV = lon
    if TheMap.isUp then place(View.of(TheMap.surface))

  def onAdded(f: () => Unit): Unit = onAdd += f
  def onRemoved(f: () => Unit): Unit = onRemove += f
  private[map] def added(): Unit = onAdd.foreach(_())
  private[map] def removed(): Unit = onRemove.foreach(_())

  /** Where it is drawn now, container pixels (NaN before it was drawn). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def screen: (ScreenPt.X, ScreenPt.Y) = (ScreenPt.X(lastX), ScreenPt.Y(lastY))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[map] def place(v: View): Unit =
    // the copy of the world nearest the view's centre (the map draws the world's copies side by side)
    val lon = lonV.value + 360 * math.round((v.centreLon.value - lonV.value) / 360).toDouble
    val p = v.surface.project(LatLon(latV, LatLon.Lon(lon)))
    val (w, h) = (v.w.value, v.h.value)
    val out = p.x.value < -400 || p.y.value < -400 || p.x.value > w + 400 || p.y.value > h + 400
    if out != hidden then
      hidden = out
      if painted then el.classList.toggle("is-offscreen", out)
      else el.asInstanceOf[js.Dynamic].inert = out
    if !out then
      val rx = math.round(p.x.value).toDouble
      val ry = math.round(p.y.value).toDouble
      if rx != lastX || ry != lastY then
        lastX = rx
        lastY = ry
        if painted then moveTo(ScreenPt.X(rx), ScreenPt.Y(ry))

  private[map] var painted = true

  private[map] def drawX: Double = lastX

  private[map] def drawY: Double = lastY

  private[map] def offscreen: Boolean = hidden

  /** Draw it at screen point (`x`, `y`) now (an animation's start), whatever the map says. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def moveTo(x: ScreenPt.X, y: ScreenPt.Y): Unit =
    el.style.setProperty("--tx", s"${x.value}px")
    el.style.setProperty("--ty", s"${y.value}px")

  /** Its stacking among the markers of its pane. */
  def setZ(z: DomMarker.ZIndex): Unit = el.style.setProperty("--z", z.toString)

object DomMarker:
  type BoxSize = BoxSize.T
  object BoxSize extends Newtype[Int, Pure]
  type ZIndex = ZIndex.T
  object ZIndex extends Newtype[Int, Pure]

  /** A marker element of class `cls` holding `html`. */
  def element(cls: String, html: Html): dom.HTMLElement =
    val el = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
    el.className = cls
    HtmlSink.replace(el, html)
    el
