package com.histmersion.web

import com.histmersion.web.leaflet.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js

/** A hover that waits: `enter(k)` fires `k` only after `delayMs` without a `leave(k)` or another
  * `enter`, so sweeping the pointer across twenty polygons fetches nothing and the one it rests on
  * fetches once. `later`/`cancel` are injectable so the timing is a unit test, like [[Throttle]]. */
final class HoverIntent[K](
    delayMs: Int,
    fire: K => Unit,
    later: (Double, () => Unit) => Int = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: Int => Unit = id => dom.window.clearTimeout(id)
):
  // `var`: the one pending hover and its timer
  private var pending: Option[(K, Int)] = None

  def isPending(k: K): Boolean = pending.exists(_._1 == k)

  def enter(k: K): Unit =
    if !isPending(k) then
      cancelPending()
      val id = later(delayMs.toDouble, () => { pending = None; fire(k) })
      pending = Some(k -> id)

  /** The pointer left `k`: its pending card is dropped (a pending card for another target stays). */
  def leave(k: K): Unit = if isPending(k) then cancelPending()

  def cancelPending(): Unit =
    pending.foreach((_, id) => cancel(id))
    pending = None

/** The map's places — sites, polities, the chosen place's outline — answer the pointer the way the
  * app's links do, with the same card ([[Preview]]):
  *   - hovering one (after [[Layout.hoverDelayMs]] at rest) floats its card at the pointer;
  *   - a click chooses it as THE place filter ([[PlacesInput.toggleFromMap]]); a click on the chosen
  *     place clears it again;
  *   - a tap does the same and also shows the card, which stays until the map is tapped or moved;
  *   - on the keyboard each place is a button: focus shows the card, Enter or Space chooses it. */
object PlaceHover:

  // `var`s: where the pointer last was over a place, and what kind of pointer last pressed the map
  // (Leaflet's `click` does not say whether a finger or a mouse made it)
  private var lastX: Double = 0
  private var lastY: Double = 0
  private var lastPointer: String = "mouse"
  private var hovered: Option[Preview.Target] = None
  // `var`: installing twice must not double-bind
  private var installed = false

  private val intent = HoverIntent[Preview.Target](Layout.hoverDelayMs, t =>
    if hovered.contains(t) then Preview.showAt(t, lastX, lastY))

  /** Is this pointer type a finger? (`pen` hovers like a mouse.) */
  private[web] def isTouch(pointerType: String): Boolean = pointerType == "touch"

  /** Once per map: remember the pointer type of each press, and drop a tapped card when the map
    * itself is tapped or starts to move. */
  def install(map: LeafletMap): Unit =
    if !installed then
      installed = true
      map.getContainer().addEventListener("pointerdown",
        (e: dom.PointerEvent) => lastPointer = e.pointerType, useCapture = true)
      map.on("click", (_: LeafletEvent) => { hovered = None; Preview.hide() })
      map.on("movestart", (_: LeafletEvent) => if isTouch(lastPointer) then { hovered = None; Preview.hide() })

  private def at(e: LeafletEvent): Unit =
    val oe = e.asInstanceOf[js.Dynamic].originalEvent
    if !js.isUndefined(oe) && oe != null && !js.isUndefined(oe.clientX) then
      lastX = oe.clientX.asInstanceOf[Double]
      lastY = oe.clientY.asInstanceOf[Double]

  /** Wire one Leaflet layer (a path or a marker) to place `id`. `asOf` is the year the card reads the
    * hierarchy at — the map's year when the pointer arrives. */
  def bind(layer: Layer, id: String, label: String, kind: String, asOf: () => Option[Long]): Unit =
    def target = Preview.Target.Place(id, asOf())
    def choose(): Boolean = PlacesInput.toggleFromMap(id, label, kind)
    layer.on("mouseover", { (e: LeafletEvent) =>
      if !isTouch(lastPointer) then
        at(e)
        val t = target
        hovered = Some(t)
        intent.enter(t)
    })
    layer.on("mousemove", { (e: LeafletEvent) =>
      at(e)
      hovered.filter(Preview.showingAt).foreach(_ => Preview.moveTo(lastX, lastY))
    })
    layer.on("mouseout", { (_: LeafletEvent) =>
      if !isTouch(lastPointer) then
        hovered.foreach { t => intent.leave(t); Preview.hideAt(t) }
        hovered = None
    })
    layer.on("click", { (e: LeafletEvent) =>
      DomEvent.stopPropagation(e)   // the map's own click would hide the card the tap just showed
      at(e)
      val on = choose()
      if isTouch(lastPointer) then
        val t = target
        intent.cancelPending()
        if on then { hovered = Some(t); Preview.showAt(t, lastX, lastY) }
        else { hovered = None; Preview.hide() }
    })
    // the keyboard: the shape's own element becomes a button once Leaflet has drawn it
    layer.on("add", { (_: LeafletEvent) =>
      val d = layer.asInstanceOf[js.Dynamic]
      if js.typeOf(d.getElement) == "function" then
        val raw = d.getElement()
        if !js.isUndefined(raw) && raw != null then keyboard(raw.asInstanceOf[dom.Element], label, () => target, choose)
    })

  private def keyboard(el: dom.Element, label: String, target: () => Preview.Target, choose: () => Boolean): Unit =
    el.setAttribute("tabindex", "0")
    el.setAttribute("role", "button")
    el.setAttribute("aria-label", s"$label — show only this place")
    def centre(): (Double, Double) =
      val r = el.getBoundingClientRect()
      (r.left + r.width / 2, r.top + r.height / 2)
    el.addEventListener("focus", { (_: dom.FocusEvent) =>
      val (x, y) = centre()
      val t = target()
      hovered = Some(t)
      Preview.showAt(t, x, y)
    })
    el.addEventListener("blur", { (_: dom.FocusEvent) => hovered.foreach(Preview.hideAt); hovered = None })
    el.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
      if e.key == "Enter" || e.key == " " then
        e.preventDefault()
        val _ = choose()
    })
