package histdb.web

import histdb.web.leaflet.*
import histdb.web.ui.{CssClass, Layout}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** A Leaflet layer group whose members are diffed by key on every `sync`: new members fade in,
  * vanished ones fade out and are then removed, and kept ones are restyled in place (their colour
  * change animates through a CSS transition) — so a region or site layer never blinks when the
  * filter or the time span moves. */
final class FadingLayer:
  private var group: LayerGroup                 = null
  private val live: mutable.LinkedHashMap[String, Layer] = mutable.LinkedHashMap.empty

  def isAttached: Boolean = group != null

  def attach(map: LeafletMap): Unit =
    if group == null then
      group = L.layerGroup()
      group.addTo(map)

  def detach(map: LeafletMap): Unit =
    if group != null then
      map.removeLayer(group)
      group = null
      live.clear()

  /** Bring the group in line with `items`. `create` builds a member; `update` restyles a kept one. */
  def sync[A](items: Iterable[A], key: A => String, create: A => Layer, update: (Layer, A) => Unit): Unit =
    if group != null then
      val next = mutable.LinkedHashMap.empty[String, A]
      items.foreach(a => next.getOrElseUpdate(key(a), a))
      val plan = FadingLayer.plan(live.keySet.toSet, next.keySet.toSet)
      val g = group
      for k <- plan.exiting do
        live.remove(k).foreach { lyr =>
          FadingLayer.elementsOf(lyr).foreach(_.classList.add(FadingLayer.fadeOut.value))
          dom.window.setTimeout(() => g.removeLayer(lyr), Layout.exitAnimMs)
        }
      for k <- plan.staying do update(live(k), next(k))
      val animate = live.nonEmpty || plan.exiting.nonEmpty
      for k <- next.keys if plan.entering(k) do
        val lyr = create(next(k))
        g.addLayer(lyr)
        if animate then FadingLayer.elementsOf(lyr).foreach(_.classList.add(FadingLayer.fadeIn.value))
        live(k) = lyr

object FadingLayer:

  final case class Plan(entering: Set[String], staying: Set[String], exiting: Set[String])

  private[web] def plan(before: Set[String], after: Set[String]): Plan =
    Plan(entering = after -- before, staying = after & before, exiting = before -- after)

  val fadeIn: CssClass  = CssClass("hd-fade-in")
  val fadeOut: CssClass = CssClass("hd-fade-out")

  /** The SVG/DOM nodes behind a layer: a path's own element, or every child's for a group. */
  private def elementsOf(lyr: Layer): List[dom.Element] =
    val d = lyr.asInstanceOf[js.Dynamic]
    if js.typeOf(d.getElement) == "function" then
      val el = d.getElement()
      if js.isUndefined(el) || el == null then Nil else List(el.asInstanceOf[dom.Element])
    else if js.typeOf(d.getLayers) == "function" then
      d.getLayers().asInstanceOf[js.Array[Layer]].toList.flatMap(elementsOf)
    else Nil
