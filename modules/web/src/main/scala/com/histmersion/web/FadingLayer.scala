package com.histmersion.web

import com.histmersion.web.leaflet.*
import com.histmersion.web.ui.{CssClass, Layout}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** A Leaflet layer group whose members are diffed by key on every `sync`: new members fade in,
  * vanished ones fade out and are then removed, and kept ones are restyled in place (their colour
  * change animates through a CSS transition) — so a region or site layer never blinks when the
  * filter or the time span moves.
  *
  * With `into`, the members are not a group of their own: they join a shared [[FadingLayer.Sink]] — the
  * map's one cluster group, where the dots of every layer are clustered together ([[MapView]]). They are
  * added and removed in batches (one re-cluster each); a member leaves at once (a bubble never counts
  * what is gone), and only one drawn on its own fades out where it stood. */
final class FadingLayer(into: Option[() => FadingLayer.Sink] = None):
  private var group: LayerGroup                 = null
  // `var`s, for the clustered form: whether its members are in the sink, and the map a member that
  // stood alone fades out on
  private var joined: Boolean                   = false
  private var shownOn: LeafletMap               = null
  private val live: mutable.LinkedHashMap[String, Layer] = mutable.LinkedHashMap.empty

  def isAttached: Boolean = group != null || joined

  def attach(map: LeafletMap): Unit =
    into match
      case Some(_) =>
        joined = true
        shownOn = map
      case None =>
        if group == null then
          group = L.layerGroup()
          group.addTo(map)

  def detach(map: LeafletMap): Unit =
    into match
      case Some(sink) =>
        if joined then
          sink().remove(js.Array(live.values.toSeq*))
          joined = false
          live.clear()
      case None =>
        if group != null then
          map.removeLayer(group)
          group = null
          live.clear()

  /** Bring the group in line with `items`. `create` builds a member; `update` restyles a kept one —
    * only one that `changed` says differs from what it shows (all, by default): a reload of the same
    * 2 400 events after a pan rebuilt every marker's icon and every bubble for nothing (#1949). */
  def sync[A](items: Iterable[A], key: A => String, create: A => Layer, update: (Layer, A) => Unit,
      changed: (Layer, A) => Boolean = (_: Layer, _: A) => true): Unit =
    if isAttached then
      val next = mutable.LinkedHashMap.empty[String, A]
      items.foreach(a => next.getOrElseUpdate(key(a), a))
      val plan = FadingLayer.plan(live.keySet.toSet, next.keySet.toSet)
      val animate = live.nonEmpty || plan.exiting.nonEmpty
      into match
        case Some(sink) =>
          val gone = js.Array[Layer]()
          val alone = mutable.ListBuffer.empty[Layer]
          for k <- plan.exiting; lyr <- live.remove(k) do
            gone.push(lyr)
            if FadingLayer.elementsOf(lyr).exists(_.isConnected) then alone += lyr
          if gone.nonEmpty then sink().remove(gone)
          if alone.nonEmpty && shownOn != null then FadingLayer.fadeAway(shownOn, alone.toList)
        case None =>
          val g = group
          for k <- plan.exiting do
            live.remove(k).foreach { lyr =>
              FadingLayer.elementsOf(lyr).foreach(_.classList.add(FadingLayer.fadeOut.value))
              dom.window.setTimeout(() => g.removeLayer(lyr), Layout.exitAnimMs)
            }
      // `var`: whether any kept member was restyled (its bubbles are drawn again)
      var restyled = false
      for k <- plan.staying do
        val (lyr, a) = (live(k), next(k))
        if changed(lyr, a) then
          update(lyr, a)
          restyled = true
      val fresh = js.Array[Layer]()
      for k <- next.keys if plan.entering(k) do
        val lyr = create(next(k))
        fresh.push(lyr)
        live(k) = lyr
      into match
        case Some(sink) =>
          if fresh.nonEmpty then sink().add(fresh)
          // a kept member's colour moved with the span: the bubbles it is in are drawn again
          if restyled then sink().refresh()
        case None => fresh.foreach(group.addLayer)
      if animate then fresh.foreach(lyr => FadingLayer.elementsOf(lyr).foreach(_.classList.add(FadingLayer.fadeIn.value)))

object FadingLayer:

  final case class Plan(entering: Set[String], staying: Set[String], exiting: Set[String])

  private[web] def plan(before: Set[String], after: Set[String]): Plan =
    Plan(entering = after -- before, staying = after & before, exiting = before -- after)

  val fadeIn: CssClass  = CssClass("hd-fade-in")
  val fadeOut: CssClass = CssClass("hd-fade-out")

  /** Where a clustered layer's members go: the map's one cluster group. */
  trait Sink:
    def add(layers: js.Array[Layer]): Unit
    def remove(layers: js.Array[Layer]): Unit
    /** Redraw every bubble: their members' colours changed. */
    def refresh(): Unit

  /** Members that left a cluster while drawn on their own fade out where they stood, in a group of
    * their own on the map, which goes when the fade is over. */
  private[web] def fadeAway(map: LeafletMap, layers: List[Layer]): Unit =
    val g = L.layerGroup()
    layers.foreach(g.addLayer)
    g.addTo(map)
    layers.foreach(l => elementsOf(l).foreach(_.classList.add(fadeOut.value)))
    dom.window.setTimeout(() => map.removeLayer(g), Layout.exitAnimMs)

  /** The SVG/DOM nodes behind a layer: a path's own element, or every child's for a group. */
  private[web] def elementsOf(lyr: Layer): List[dom.Element] =
    val d = lyr.asInstanceOf[js.Dynamic]
    if js.typeOf(d.getElement) == "function" then
      val el = d.getElement()
      if js.isUndefined(el) || el == null then Nil else List(el.asInstanceOf[dom.Element])
    else if js.typeOf(d.getLayers) == "function" then
      d.getLayers().asInstanceOf[js.Array[Layer]].toList.flatMap(elementsOf)
    else Nil
