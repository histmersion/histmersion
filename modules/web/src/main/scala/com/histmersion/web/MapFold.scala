package com.histmersion.web

import com.histmersion.web.map.{MapEventKind, MapSurface}
import org.scalajs.dom

/** The map's overlays that fold away on a phone: its foot (the past-landscape notice and the credits) and its legend
  * (the colour ramp and the key). In the narrow layout each is a small button until the reader opens it; opened, it
  * fits the map less its gutters and scrolls inside itself, the button that closes it staying where it was tapped. In
  * the wide layout the button is not shown and the overlay is always open.
  */
object MapFold:

  /** The class an opened overlay carries. */
  type ToggleHtmlResult = ToggleHtmlResult.T
  object ToggleHtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleHtmlCls = ToggleHtmlCls.T
  object ToggleHtmlCls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleHtmlControls = ToggleHtmlControls.T
  object ToggleHtmlControls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleHtmlLabel = ToggleHtmlLabel.T
  object ToggleHtmlLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleHtmlInner = ToggleHtmlInner.T
  object ToggleHtmlInner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToggleHtmlOpen = ToggleHtmlOpen.T
  object ToggleHtmlOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetOpen = SetOpen.T
  object SetOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireChangedArg1 = WireChangedArg1.T
  object WireChangedArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Open = "is-open"

  /** Pure: an overlay's button, which opens and closes the part `controls` (an element id); `label` names the overlay
    * to a screen reader, `inner` is what it shows.
    */
  def toggleHtml(cls: com.histmersion.web.MapFold.ToggleHtmlCls, controls: com.histmersion.web.MapFold.ToggleHtmlControls, label: com.histmersion.web.MapFold.ToggleHtmlLabel, inner: com.histmersion.web.MapFold.ToggleHtmlInner, open: com.histmersion.web.MapFold.ToggleHtmlOpen): com.histmersion.web.MapFold.ToggleHtmlResult =
    s"""<button type="button" class="btn btn--sm $cls" aria-controls="$controls" """ +
      s"""aria-expanded="$open" aria-label="${Fmt.esc(label)}" title="${Fmt.esc(label)}">$inner</button>"""

  /** Open or close `box`, its button saying which. */
  def set(box: dom.Element, open: com.histmersion.web.MapFold.SetOpen): Unit =
    box.classList.toggle(Open, open)
    Option(box.querySelector("button[aria-expanded]")).foreach(_.setAttribute("aria-expanded", open.toString))

  /** `box`'s button opens and closes it, Escape inside it closes it; `changed` hears the new state. */
  def wire(box: dom.Element, changed: com.histmersion.web.MapFold.WireChangedArg1 => Unit = _ => ()): Unit =
    Option(box.querySelector("button[aria-expanded]")).foreach { b =>
      b.addEventListener(
        "click",
        { (e: dom.MouseEvent) =>
          e.stopPropagation()
          val open = !box.classList.contains(Open)
          set(box, open)
          changed(open)
        }
      )
    }
    box.addEventListener(
      "keydown",
      (e: dom.KeyboardEvent) =>
        if e.key == "Escape" && box.classList.contains(Open) then
          set(box, false)
          changed(false)
          Option(box.querySelector("button[aria-expanded]")).foreach(_.asInstanceOf[dom.HTMLElement].focus())
    )

  /** The map's size as the overlays read it (`--map-w`/`--map-h`): an opened one fits inside the map, whatever the
    * layout around it (a short desktop window's map is a small part of the page).
    */
  def followSize(surface: MapSurface): Unit =
    def write(): Unit =
      val (w, h) = surface.size
      surface.container.style.setProperty("--map-w", s"${w}px")
      surface.container.style.setProperty("--map-h", s"${h}px")
    write()
    val _ = surface.on(MapEventKind.Resize)(_ => write())
