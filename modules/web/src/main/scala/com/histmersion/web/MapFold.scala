package com.histmersion
package web

import com.histmersion.web.map.{MapEventKind, MapSurface}
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Html
import com.histmersion.api.Html.html

/** The map's overlays that fold away on a phone: its foot (the past-landscape notice and the credits) and its legend
  * (the colour ramp and the key). In the narrow layout each is a small button until the reader opens it; opened, it
  * fits the map less its gutters and scrolls inside itself, the button that closes it staying where it was tapped. In
  * the wide layout the button is not shown and the overlay is always open.
  */
object MapFold:
  type Changed = Changed.T
  object Changed extends Newtype[Boolean, Pure]
  type ClassName = ClassName.T
  object ClassName extends Newtype[String, Pure]
  type ControlsId = ControlsId.T
  object ControlsId extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Open = Open.T
  object Open extends Newtype[Boolean, Pure]

  /** The class an opened overlay carries. */
  val OpenClass = "is-open"

  /** Pure: an overlay's button, which opens and closes the part `controls` (an element id); `label` names the overlay
    * to a screen reader, `inner` is what it shows.
    */
  def toggleHtml(cls: ClassName, controls: ControlsId, label: Label, inner: Html, open: Open): Html =
    html"""<button type="button" class="btn btn--sm $cls" aria-controls="$controls" """ ++
      html"""aria-expanded="$open" aria-label="$label" title="$label">$inner</button>"""

  /** Open or close `box`, its button saying which. */
  def set(box: dom.Element, open: Open): Unit =
    box.classList.toggle(OpenClass, open.value)
    Option(box.querySelector("button[aria-expanded]")).foreach(_.setAttribute("aria-expanded", open.toString))

  /** `box`'s button opens and closes it, Escape inside it closes it; `changed` hears the new state. */
  def wire(box: dom.Element, changed: Changed => Unit = _ => ()): Unit =
    Option(box.querySelector("button[aria-expanded]")).foreach { b =>
      b.addEventListener(
        "click",
        { (e: dom.MouseEvent) =>
          e.stopPropagation()
          val open = !box.classList.contains(OpenClass)
          set(box, Open(open))
          changed(Changed(open))
        }
      )
    }
    box.addEventListener(
      "keydown",
      (e: dom.KeyboardEvent) =>
        if e.key == "Escape" && box.classList.contains(OpenClass) then
          set(box, Open(false))
          changed(Changed(false))
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
