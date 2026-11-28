package com.histmersion
package web

import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Year

/** The map's places — sites, polities, peoples, point labels, the chosen place's outline — answer the pointer the way
  * the app's links do, with the same card ([[Preview]]):
  *   - hovering one (after [[Layout.hoverDelayMs]] at rest) floats its card at the pointer — a card that follows the
  *     pointer, to read;
  *   - a click chooses it as THE place filter ([[PlacesInput.toggleFromMap]]) and PINS its card where it was clicked,
  *     with a close button: it stays there (moving with the map) so its links can be clicked, until its ✕, Escape, or a
  *     click on the empty map. A click on the chosen place clears the filter and closes the card;
  *   - a tap does the same as a click;
  *   - on the keyboard each place is a button: focus shows the card, Enter or Space chooses the place and pins the card
  *     with the focus inside it (Escape returns it to the place).
  * A place drawn as a SHAPE (the map's canvas) is found under the pointer by the map ([[MapSurface.shapesAt]]); one
  * drawn as a marker answers through its element.
  */
object PlaceHover:
  export Pages.PlaceId
  type PlaceKind = PlaceKind.T
  object PlaceKind extends Newtype[String, Pure]
  type PointerType = PointerType.T
  object PointerType extends Newtype[String, Pure]
  export ShapeHit.Set as ShapeSetName

  // `var`s: where the pointer last was over a place, and what kind of pointer last pressed the map

  private var lastX: Preview.ScreenPixels = Preview.ScreenPixels(0)
  private var lastY: Preview.ScreenPixels = Preview.ScreenPixels(0)
  private var lastPointer: PointerType = PointerType("mouse")
  private var hovered: Option[Preview.Target] = None
  // `var`s: the map, once installed (installing twice must not double-bind), and the shape hovered
  private var surface: MapSurface = null
  private var hoveredShape: Option[ShapeHit] = None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val intent =
    HoverIntent[Preview.Target](
      HoverIntent.DelayMs(Layout.hoverDelayMs.value),
      t => if hovered.contains(t) then Preview.showAt(t, lastX, lastY)
    )

  /** A set of shapes whose shapes are places: `place(id)` is the place a shape stands for. */
  final case class Places(
      place: PlaceId => Option[(PlaceId, UiLabels.Text, PlaceKind)],
      asOf: () => Option[Year.Astronomical]
  )

  private val shapeSets = scala.collection.mutable.LinkedHashMap.empty[ShapeHit.Set, Places]

  /** The shapes of set `set` answer as the places `place` names. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def shapes(set: ShapeSetName, places: Places): Unit = shapeSets(set) = places

  /** Is this pointer type a finger? (`pen` hovers like a mouse.) */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[web] def isTouch(pointerType: PointerType): Boolean = pointerType.value == "touch"

  /** The place shape topmost under `p`, if any. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def placeAt(p: ScreenPt): Option[(ShapeHit, (PlaceId, UiLabels.Text, PlaceKind), Places)] =
    if surface == null then None
    else
      surface
        .shapesAt(p)
        .iterator
        .flatMap(h => shapeSets.get(h.set).flatMap(ps => ps.place(PlaceId(h.id.value)).map(pl => (h, pl, ps))))
        .nextOption()

  /** Once per map: remember the pointer type of each press, the shapes' hover and click, and drop a tapped card when
    * the map itself is tapped or starts to move.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def install(m: MapSurface): Unit =
    if surface == null then
      surface = m
      m.container.addEventListener(
        "pointerdown",
        (e: dom.PointerEvent) => lastPointer = PointerType(e.pointerType),
        useCapture = true
      )
      m.on(MapEventKind.PointerMove) { e =>
        e.clientXY.foreach { (x, y) => lastX = Preview.ScreenPixels(x); lastY = Preview.ScreenPixels(y) }
        val hit = if e.overMarker || isTouch(lastPointer) then None else e.point.flatMap(placeAt)
        if hit.map(_._1) != hoveredShape then
          hoveredShape.foreach(_ => dropHover())
          hoveredShape = hit.map(_._1)
          m.container.classList.toggle("over-place", hit.isDefined)
          hit.foreach { (_, pl, ps) =>
            val t = Preview.Target.Place(pl._1, ps.asOf())
            hovered = Some(t)
            intent.enter(t)
          }
        else if hit.isDefined then hovered.filter(Preview.showingAt).foreach(_ => Preview.moveTo(lastX, lastY))
      }
      m.on(MapEventKind.PointerOut) { _ =>
        if hoveredShape.isDefined then { hoveredShape = None; m.container.classList.remove("over-place"); dropHover() }
      }
      m.on(MapEventKind.Click) { e =>
        if !e.overMarker then
          e.clientXY.foreach { (x, y) => lastX = Preview.ScreenPixels(x); lastY = Preview.ScreenPixels(y) }
          e.point.flatMap(placeAt) match
            case Some((_, (id, label, kind), ps)) =>
              val on = PlacesInput.toggleFromMap(id, label, kind)
              val anchor =
                e.at.map(anchorAt).getOrElse { val (x, y) = (lastX, lastY); () => Some(Preview.Anchor.At(x, y)) }
              settle(Preview.Target.Place(id, ps.asOf()), on, anchor, None, keyboard = Preview.KeyboardDriven.Pointer)
            case None =>
              // a click on the empty map lets every card go
              hovered = None
              Preview.hide()
              Preview.unpin(restoreFocus = Preview.RestoreFocus.Leave)
      }
      // a pinned card stays at its place while the map moves under it
      m.on(MapEventKind.Move)(_ => Preview.reposition())
      m.on(MapEventKind.ZoomEnd)(_ => Preview.reposition())

  /** A pinned card's anchor at map point `ll`: where it is on screen now, `None` while panned away. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def anchorAt(ll: LatLon): () => Option[Preview.Anchor] = () =>
    Option(surface).flatMap { m =>
      val p = m.project(ll)
      val r = m.container.getBoundingClientRect()
      PinRules
        .inView(
          PinRules.ScreenPixels(p.x.value),
          PinRules.ScreenPixels(p.y.value),
          PinRules.ScreenPixels(r.left),
          PinRules.ScreenPixels(r.top),
          PinRules.ElementSize(r.width),
          PinRules.ElementSize(r.height)
        )
        .map((x, y) => Preview.Anchor.At(Preview.ScreenPixels(x.value), Preview.ScreenPixels(y.value)))
    }

  /** A pinned card's anchor at an element's centre (a keyboard pin), `None` once it is gone. */
  private def anchorOn(el: dom.Element): () => Option[Preview.Anchor] = () =>
    Option.when(el.isConnected) {
      val r = el.getBoundingClientRect()
      Preview.Anchor.At(Preview.ScreenPixels(r.left + r.width / 2), Preview.ScreenPixels(r.top + r.height / 2))
    }

  /** Pin `t`'s card after a click or Enter that left the place `chosenNow`, or close it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def settle(
      t: Preview.Target,
      chosenNow: Boolean,
      anchor: () => Option[Preview.Anchor],
      owner: Option[dom.Element],
      keyboard: Preview.KeyboardDriven
  ): Unit =
    intent.cancelPending()
    if PinRules.pinAfterClick((if chosenNow then PinRules.ChosenNow.ChosenNow else PinRules.ChosenNow.ChosenBefore))
    then
      hovered = Some(t)
      Preview.pin(t, anchor, owner, keyboard)
    else
      hovered = None
      Preview.hide()
      Preview.unpin(restoreFocus = Preview.RestoreFocus.Leave)

  private def at(e: dom.MouseEvent): Unit =
    lastX = Preview.ScreenPixels(e.clientX)
    lastY = Preview.ScreenPixels(e.clientY)

  /** Where a marker's event happened on the map. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def latLonOf(e: dom.MouseEvent): Option[LatLon] =
    Option(surface).map { m =>
      val r = m.container.getBoundingClientRect()
      m.unproject(ScreenPt(ScreenPt.X(e.clientX - r.left), ScreenPt.Y(e.clientY - r.top)))
    }

  /** A place drawn as a MARKER (a point-only region's name, a chosen place's point): its card on hover, a click (or
    * Enter) chooses it. `asOf` is the year the card reads the hierarchy at.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bind(
      m: DomMarker,
      id: PlaceId,
      label: UiLabels.Text,
      kind: PlaceKind,
      asOf: () => Option[Year.Astronomical]
  ): Unit =
    def target = Preview.Target.Place(id, asOf())
    def choose(): Boolean = PlacesInput.toggleFromMap(id, label, kind)
    m.handlers = new MarkerHandlers:
      override def over(e: dom.MouseEvent): Unit = hoverIn(e, Some(target))
      override def move(e: dom.MouseEvent): Unit = hoverMove(e)
      override def out(e: dom.MouseEvent): Unit = if !isTouch(lastPointer) then dropHover()
      @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
      override def click(e: dom.MouseEvent): Unit =
        e.stopPropagation()
        at(e)
        val on = choose()
        val anchor =
          latLonOf(e).map(anchorAt).getOrElse { val (x, y) = (lastX, lastY); () => Some(Preview.Anchor.At(x, y)) }
        settle(target, on, anchor, None, keyboard = Preview.KeyboardDriven.Pointer)
      override def focus(e: dom.FocusEvent): Unit = focusCard(m.el, target)
      override def blur(e: dom.FocusEvent): Unit = dropHover()
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
      override def key(e: dom.KeyboardEvent): Unit =
        if PinRules.activates(PinRules.KeyName(e.key)) then
          e.preventDefault()
          settle(target, choose(), anchorOn(m.el), Some(m.el), keyboard = Preview.KeyboardDriven.Keyboard)
    button(m.el, shown"$label — show only this place and pin its card")

  /** A place's NAME (an area's label on the map): a keyboard button for the place it names — the pointer passes through
    * it to the shape under it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bindName(
      m: DomMarker,
      id: PlaceId,
      label: UiLabels.Text,
      kind: PlaceKind,
      asOf: () => Option[Year.Astronomical]
  ): Unit =
    def target = Preview.Target.Place(id, asOf())
    val el = Option(m.el.firstElementChild).getOrElse(m.el)
    m.handlers = new MarkerHandlers:
      override def focus(e: dom.FocusEvent): Unit = focusCard(el, target)
      override def blur(e: dom.FocusEvent): Unit = dropHover()
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
      override def key(e: dom.KeyboardEvent): Unit =
        if PinRules.activates(PinRules.KeyName(e.key)) then
          e.preventDefault()
          settle(
            target,
            PlacesInput.toggleFromMap(id, label, kind),
            anchorOn(el),
            Some(el),
            keyboard = Preview.KeyboardDriven.Keyboard
          )
    button(el, shown"$label — show only this place and pin its card")

  private def button(el: dom.Element, label: String): Unit =
    el.setAttribute("tabindex", "0")
    el.setAttribute("role", "button")
    el.setAttribute("aria-label", label)

  private def hoverIn(e: dom.MouseEvent, t: Option[Preview.Target]): Unit =
    if !isTouch(lastPointer) then
      at(e)
      t.foreach { t =>
        hovered = Some(t)
        intent.enter(t)
      }

  private def hoverMove(e: dom.MouseEvent): Unit =
    at(e)
    hovered.filter(Preview.showingAt).foreach(_ => Preview.moveTo(lastX, lastY))

  private def focusCard(el: dom.Element, t: Preview.Target): Unit =
    val r = el.getBoundingClientRect()
    hovered = Some(t)
    Preview.showAt(t, Preview.ScreenPixels(r.left + r.width / 2), Preview.ScreenPixels(r.top + r.height / 2))

  /** Forget the hovered target: its pending card is cancelled, its showing card hidden. */
  private def dropHover(): Unit =
    hovered.foreach { t => intent.leave(t); Preview.hideAt(t) }
    hovered = None

  /** A point on the map — any kind of [[PointLayer]] marker — answers like the app's links, the same way for every
    * kind: resting the pointer on it (after [[Layout.hoverDelayMs]]) floats its card at the pointer (`target`, re-read
    * on each hover: the point's content may have changed), focus shows it too, leaving drops it; it is a keyboard
    * button named `label` (Enter or Space activates it); a click or Enter clears the hover card out of the way and
    * `activate`s it — what that means is the kind's (a finding's popup, a place chosen and pinned, an event's card
    * pinned…). A point that leaves the map (re-drawn, clustered, filtered away) takes its hover card with it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bindPoint(
      m: DomMarker,
      target: () => Option[Preview.Target],
      label: () => UiLabels.Text,
      activate: Activation => Unit
  ): Unit =
    val el = m.el
    m.handlers = new MarkerHandlers:
      override def over(e: dom.MouseEvent): Unit = hoverIn(e, target())
      override def move(e: dom.MouseEvent): Unit = hoverMove(e)
      override def out(e: dom.MouseEvent): Unit = if !isTouch(lastPointer) then dropHover()
      override def click(e: dom.MouseEvent): Unit =
        e.stopPropagation() // the map's own click would close a card this click pins
        at(e)
        intent.cancelPending()
        hovered = None
        Preview.hide()
        activate(Activation(keyboard = Activation.Keyboard.Pointer, Some(el), latLonOf(e)))
      override def focus(e: dom.FocusEvent): Unit = target().foreach(t => focusCard(el, t))
      override def blur(e: dom.FocusEvent): Unit = dropHover()
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      override def key(e: dom.KeyboardEvent): Unit =
        if PinRules.activates(PinRules.KeyName(e.key)) then
          e.preventDefault()
          intent.cancelPending()
          Preview.hide()
          activate(Activation(keyboard = Activation.Keyboard.Keyboard, Some(el), None))
    m.onRemoved(() => if target().exists(t => hovered.contains(t)) then dropHover())
    button(el, label().value)

  /** A place's point activated (a click or Enter): it is chosen as THE place filter and its card pinned there — beside
    * the point clicked, or on the focused element with the focus moving in; activating the chosen place again lets it
    * go and closes the card.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def activatePlace(
      id: PlaceId,
      label: UiLabels.Text,
      kind: PlaceKind,
      asOf: () => Option[Year.Astronomical],
      how: Activation
  ): Unit =
    val t = Preview.Target.Place(id, asOf())
    val on = PlacesInput.toggleFromMap(id, label, kind)
    val anchor = (how.keyboard.keyboard, how.element, how.latlng) match
      case (true, Some(el), _) => anchorOn(el)
      case (_, _, Some(ll))    => anchorAt(ll)
      case _                   => { val (x, y) = (lastX, lastY); () => Some(Preview.Anchor.At(x, y)) }
    settle(
      t,
      on,
      anchor,
      how.element.filter(_ => how.keyboard.keyboard),
      (if how.keyboard.keyboard then Preview.KeyboardDriven.Keyboard else Preview.KeyboardDriven.Pointer)
    )
    // chosen, its detail replaces the results; unchosen, the results come back
    if on then ResultsPanel.show(t) else if ResultsPanel.selected.now().exists(_ == t) then ResultsPanel.back()
