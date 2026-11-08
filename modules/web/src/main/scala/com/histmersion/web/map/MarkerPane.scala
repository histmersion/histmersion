package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** One pane of DOM markers over the map: each at its map point, moved every frame the map moves (one `transform` write
  * per marker in view; one far outside the view is hidden, not moved).
  */
final class MarkerPane(val el: dom.HTMLElement, surface: MapSurface):
  private val markers = js.Set[DomMarker]()

  // the pointer and the keyboard, for every marker of the pane at once (one set of listeners, not one per
  // marker: a few thousand points are drawn and re-drawn as the map zooms)
  private def markerOf(t: dom.EventTarget): Option[DomMarker] =
    Option(t)
      .collect { case e: dom.Element => e }
      .flatMap(e => Option(e.closest(".hd-marker")))
      .flatMap(e =>
        Option(e.asInstanceOf[js.Dynamic].hdMarker).filterNot(js.isUndefined).map(_.asInstanceOf[DomMarker])
      )
  private def related(e: dom.MouseEvent): Option[DomMarker] = markerOf(e.relatedTarget)
  el.addEventListener(
    "mouseover",
    (e: dom.MouseEvent) =>
      markerOf(e.target).filter(m => !related(e).contains(m)).foreach(m => Option(m.handlers).foreach(_.over(e)))
  )
  el.addEventListener(
    "mouseout",
    (e: dom.MouseEvent) =>
      markerOf(e.target).filter(m => !related(e).contains(m)).foreach(m => Option(m.handlers).foreach(_.out(e)))
  )
  el.addEventListener(
    "mousemove",
    (e: dom.MouseEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.move(e)))
  )
  el.addEventListener(
    "click",
    (e: dom.MouseEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.click(e)))
  )
  el.addEventListener(
    "focusin",
    (e: dom.FocusEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.focus(e)))
  )
  el.addEventListener(
    "focusout",
    (e: dom.FocusEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.blur(e)))
  )
  el.addEventListener(
    "keydown",
    (e: dom.KeyboardEvent) => markerOf(e.target).foreach(m => Option(m.handlers).foreach(_.key(e)))
  )

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

  /** Take `m` off the pane now (it no longer follows the map, answers the pointer or counts), but leave its element
    * drawn for `ms` more — a marker merging into a bubble flies into it first.
    */
  def retire(m: DomMarker, ms: MarkerPane.RetireMs): Unit =
    if markers.contains(m) then
      markers.remove(m)
      m.el.classList.add("hd-cluster-ghost")
      m.removed()
      dom.window.setTimeout(() => if m.el.parentNode == el then { val _ = el.removeChild(m.el) }, ms.value.toDouble)

  def has(m: DomMarker): Boolean = markers.contains(m)

  def size: MarkerPane.MarkerCount = MarkerPane.MarkerCount(markers.size)

  def reposition(v: View): Unit = markers.foreach(_.place(v))

object MarkerPane:
  type MarkerCount = MarkerCount.T
  object MarkerCount extends Newtype[Int, Pure]
  type RetireMs = RetireMs.T
  object RetireMs extends Newtype[Int, Pure]
