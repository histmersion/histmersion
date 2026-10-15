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

/** The pure rules of a pinned place card (#1441). */
object PinRules:
  /** Does this key close a pinned card? */
  def dismisses(key: String): Boolean = key == "Escape" || key == "Esc"

  /** Does this key pin a focused place's card (and, on the map, choose the place)? */
  def activates(key: String): Boolean = key == "Enter" || key == " "

  /** After a click (or Enter) on a map place that leaves it chosen as the filter or not: its card is
    * pinned while it is chosen; the click that unchooses it closes the card too. */
  def pinAfterClick(chosenNow: Boolean): Boolean = chosenNow

  /** Pure: a point in the map container (`x`, `y`, from its top-left) as a viewport point, when it is
    * inside the container's `w`×`h` box at (`left`, `top`) — `None` while it is panned out of view. */
  def inView(x: Double, y: Double, left: Double, top: Double, w: Double, h: Double): Option[(Double, Double)] =
    Option.when(x >= 0 && y >= 0 && x <= w && y <= h)((left + x, top + y))

/** The map's places — sites, polities, peoples, point labels, the chosen place's outline — answer the
  * pointer the way the app's links do, with the same card ([[Preview]]):
  *   - hovering one (after [[Layout.hoverDelayMs]] at rest) floats its card at the pointer — a card
  *     that follows the pointer, to read;
  *   - a click chooses it as THE place filter ([[PlacesInput.toggleFromMap]]) and PINS its card where
  *     it was clicked, with a close button: it stays there (moving with the map) so its links can be
  *     clicked, until its ✕, Escape, or a click on the empty map. A click on the chosen place clears
  *     the filter and closes the card;
  *   - a tap does the same as a click;
  *   - on the keyboard each place is a button: focus shows the card, Enter or Space chooses the place
  *     and pins the card with the focus inside it (Escape returns it to the place). */
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
      leaflet = map
      map.getContainer().addEventListener("pointerdown",
        (e: dom.PointerEvent) => lastPointer = e.pointerType, useCapture = true)
      // a click on the empty map (a place's own click stops before it) lets every card go
      map.on("click", (_: LeafletEvent) => { hovered = None; Preview.hide(); Preview.unpin(restoreFocus = false) })
      // a pinned card stays at its place while the map moves under it
      map.on("move", (_: LeafletEvent) => Preview.reposition())
      map.on("zoomend", (_: LeafletEvent) => Preview.reposition())

  // `var`: the map the click anchors are read against, once installed
  private var leaflet: LeafletMap = null

  /** A pinned card's anchor at map point `ll`: where it is on screen now, `None` while panned away. */
  private def anchorAt(ll: LatLng): () => Option[Preview.Anchor] = () =>
    Option(leaflet).flatMap { m =>
      val p = m.latLngToContainerPoint(ll)
      val r = m.getContainer().getBoundingClientRect()
      PinRules.inView(p.x, p.y, r.left, r.top, r.width, r.height).map((x, y) => Preview.Anchor.At(x, y))
    }

  /** A pinned card's anchor at an element's centre (a keyboard pin), `None` once it is gone. */
  private def anchorOn(el: dom.Element): () => Option[Preview.Anchor] = () =>
    Option.when(el.isConnected) {
      val r = el.getBoundingClientRect()
      Preview.Anchor.At(r.left + r.width / 2, r.top + r.height / 2)
    }

  /** Pin `t`'s card after a click or Enter that left the place `chosenNow`, or close it. */
  private def settle(t: Preview.Target, chosenNow: Boolean, anchor: () => Option[Preview.Anchor],
      owner: Option[dom.Element], keyboard: Boolean): Unit =
    intent.cancelPending()
    if PinRules.pinAfterClick(chosenNow) then
      hovered = Some(t)
      Preview.pin(t, anchor, owner, keyboard)
    else
      hovered = None
      Preview.hide()
      Preview.unpin(restoreFocus = false)

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
    hover(layer, () => target)
    layer.on("click", { (e: LeafletEvent) =>
      DomEvent.stopPropagation(e)   // the map's own click would close the card this click pins
      at(e)
      val on = choose()
      val anchor = e.latlng.toOption.map(anchorAt).getOrElse { val (x, y) = (lastX, lastY); () => Some(Preview.Anchor.At(x, y)) }
      settle(target, on, anchor, None, keyboard = false)
    })
    // the keyboard: the shape's own element becomes a button once Leaflet has drawn it
    layer.on("add", { (_: LeafletEvent) =>
      val d = layer.asInstanceOf[js.Dynamic]
      if js.typeOf(d.getElement) == "function" then
        val raw = d.getElement()
        if !js.isUndefined(raw) && raw != null then keyboard(raw.asInstanceOf[dom.Element], label, () => target, choose)
    })

  /** The HOVER half of [[bind]], for any card: resting the pointer on `layer` (after
    * [[Layout.hoverDelayMs]]) floats `target`'s card at the pointer, moving with it; leaving drops it.
    * A touch never floats one (a tap is the layer's click). */
  def hover(layer: Layer, target: () => Preview.Target): Unit =
    layer.on("mouseover", { (e: LeafletEvent) =>
      if !isTouch(lastPointer) then
        at(e)
        val t = target()
        hovered = Some(t)
        intent.enter(t)
    })
    layer.on("mousemove", { (e: LeafletEvent) =>
      at(e)
      hovered.filter(Preview.showingAt).foreach(_ => Preview.moveTo(lastX, lastY))
    })
    layer.on("mouseout", { (_: LeafletEvent) =>
      if !isTouch(lastPointer) then dropHover()
    })

  /** Forget the hovered target: its pending card is cancelled, its showing card hidden. */
  private def dropHover(): Unit =
    hovered.foreach { t => intent.leave(t); Preview.hideAt(t) }
    hovered = None

  /** A point on the map — any kind of [[PointLayer]] marker — answers like the app's links, the same way
  * for every kind: resting the pointer on it (after [[Layout.hoverDelayMs]]) floats its card at the
  * pointer (`target`, re-read on each hover: the point's content may have changed), focus shows it
  * too, leaving drops it; it is a keyboard button named `label` (Enter or Space activates it); a click
  * or Enter clears the hover card out of the way and `activate`s it — what that means is the kind's
  * (a finding's popup, a place chosen and pinned, an event's card pinned…). A point that leaves the map
  * (re-drawn, clustered, filtered away) takes its hover card with it. */
  def bindPoint(layer: Layer, target: () => Option[Preview.Target], label: () => String,
      activate: Activation => Unit): Unit =
    layer.on("mouseover", { (e: LeafletEvent) =>
      if !isTouch(lastPointer) then
        at(e)
        target().foreach { t =>
          hovered = Some(t)
          intent.enter(t)
        }
    })
    layer.on("mousemove", { (e: LeafletEvent) =>
      at(e)
      hovered.filter(Preview.showingAt).foreach(_ => Preview.moveTo(lastX, lastY))
    })
    layer.on("mouseout", { (_: LeafletEvent) => if !isTouch(lastPointer) then dropHover() })
    layer.on("click", { (e: LeafletEvent) =>
      DomEvent.stopPropagation(e)   // the map's own click would close a card this click pins
      at(e)
      intent.cancelPending()
      hovered = None
      Preview.hide()
      activate(Activation(keyboard = false, elementOf(layer), e.latlng.toOption))
    })
    layer.on("remove", { (_: LeafletEvent) => if target().exists(t => hovered.contains(t)) then dropHover() })
    layer.on("add", { (_: LeafletEvent) =>
      elementOf(layer).foreach { el =>
        el.setAttribute("aria-label", label())
        // Leaflet re-uses a marker's element now and then (a new icon on the same element): wire it once
        if !el.hasAttribute("data-hover-bound") then
          el.setAttribute("data-hover-bound", "")
          el.setAttribute("tabindex", "0")
          el.setAttribute("role", "button")
          el.addEventListener("focus", { (_: dom.FocusEvent) =>
            target().foreach { t =>
              val r = el.getBoundingClientRect()
              hovered = Some(t)
              Preview.showAt(t, r.left + r.width / 2, r.top + r.height / 2)
            }
          })
          el.addEventListener("blur", { (_: dom.FocusEvent) => dropHover() })
          el.addEventListener("keydown", { (e: dom.KeyboardEvent) =>
            if PinRules.activates(e.key) then
              e.preventDefault()
              intent.cancelPending()
              Preview.hide()
              activate(Activation(keyboard = true, Some(el), None))
          })
      }
    })

  private def elementOf(layer: Layer): Option[dom.Element] =
    val d = layer.asInstanceOf[js.Dynamic]
    if js.typeOf(d.getElement) != "function" then None
    else
      val raw = d.getElement()
      if js.isUndefined(raw) || raw == null then None else Some(raw.asInstanceOf[dom.Element])

  /** A place's point activated (a click or Enter): it is chosen as THE place filter and its card pinned
    * there — beside the point clicked, or on the focused element with the focus moving in; activating
    * the chosen place again lets it go and closes the card. */
  def activatePlace(id: String, label: String, kind: String, asOf: () => Option[Long], how: Activation): Unit =
    val t = Preview.Target.Place(id, asOf())
    val on = PlacesInput.toggleFromMap(id, label, kind)
    val anchor = (how.keyboard, how.element, how.latlng) match
      case (true, Some(el), _) => anchorOn(el)
      case (_, _, Some(ll))    => anchorAt(ll)
      case _                   => { val (x, y) = (lastX, lastY); () => Some(Preview.Anchor.At(x, y)) }
    settle(t, on, anchor, how.element.filter(_ => how.keyboard), how.keyboard)

  private def keyboard(el: dom.Element, label: String, target: () => Preview.Target, choose: () => Boolean): Unit =
    el.setAttribute("tabindex", "0")
    el.setAttribute("role", "button")
    el.setAttribute("aria-label", s"$label — show only this place and pin its card")
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
      if PinRules.activates(e.key) then
        e.preventDefault()
        val on = choose()
        settle(target(), on, anchorOn(el), Some(el), keyboard = true)
    })
