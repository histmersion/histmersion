package com.histmersion.web.marks

import com.histmersion.api.personal.{ItemKind, Note, PersonalMarkdown, Target}
import com.histmersion.api.MapStack
import com.histmersion.web.Icon
import com.histmersion.web.map.{DomMarker, LatLon, MarkerHandlers, TheMap}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.collection.mutable

/** The map's "My notes" layer: each note left at a place on the map, as a marker of the map's own kind — off
  * until switched on under Layers. A click or Enter on one shows the note there, to read, change or delete. */
object NoteMarkers:

  // `var`/map: whether the layer is on, and the markers drawn by note id
  private var on = false
  private val drawn = mutable.LinkedHashMap.empty[String, DomMarker]
  // the note shown in a popup, unmounted when the next one opens
  private var shown: Option[RootNode] = None

  /** The notes the layer draws: those left at a point. Pure. */
  def placed(ns: List[Note]): List[Note] = ns.filter(n => n.target.kind == ItemKind.Point && n.target.lat.isDefined && n.target.lon.isDefined)

  /** Follow the notes as they are written, changed and deleted. */
  def install(): Unit =
    MyItems.notes.signal.foreach(ns => if on then draw(ns))(using unsafeWindowOwner)

  def attach(): Unit =
    on = true
    draw(MyItems.notes.now())

  def detach(): Unit =
    on = false
    clear()

  private def pane = TheMap.pane(MapStack.Pane.Markers)

  private def clear(): Unit =
    if TheMap.isUp then drawn.values.foreach(pane.remove)
    drawn.clear()

  private def draw(ns: List[Note]): Unit =
    if TheMap.isUp then
      val now = placed(ns).map(n => (n.id.value: String) -> n).toMap
      drawn.keys.toList.filterNot(now.contains).foreach(k => drawn.remove(k).foreach(pane.remove))
      now.foreach { (id, n) =>
        drawn.get(id).foreach(pane.remove)
        val m = marker(n)
        drawn(id) = m
        pane.add(m)
      }

  private def marker(n: Note): DomMarker =
    val el = DomMarker.element("hd-note-marker", Icon.html(Icon.Note))
    el.setAttribute("tabindex", "0")
    el.setAttribute("role", "button")
    el.setAttribute("data-note", n.id.value)
    val words = s"Your note: ${PersonalMarkdown.excerpt(n.text.value, 80)}"
    el.setAttribute("aria-label", s"$words — show it")
    el.setAttribute("title", words)
    val m = DomMarker(n.target.lat.get.value, n.target.lon.get.value, el, Some((26, 26)))
    m.handlers = new MarkerHandlers:
      override def click(e: dom.MouseEvent): Unit = { e.stopPropagation(); show(n) }
      override def key(e: dom.KeyboardEvent): Unit =
        if e.key == "Enter" || e.key == " " then { e.preventDefault(); show(n) }
    m

  /** The note at its place, in the map's popup. */
  def show(n: Note): Unit =
    for la <- n.target.lat; lo <- n.target.lon do
      shown.foreach(_.unmount())
      val pop = TheMap.surface.popup(LatLon(la.value, lo.value), "", "note-popup")
      pop.element.foreach { el =>
        val box = dom.document.createElement("div")
        Option(el.querySelector(".maplibregl-popup-content")).getOrElse(el).appendChild(box)
        shown = Some(render(box, div(cls := "note-popup-body",
          p(cls := "note-popup-where muted", Target.where(n.target)),
          NoteList.card(n))))
      }
