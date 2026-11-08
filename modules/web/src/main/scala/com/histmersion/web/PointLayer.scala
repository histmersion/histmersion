package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.Years
import io.github.iltotore.iron.constraint.any.Pure

/** ONE layer of points of one kind (docs/map-layers.md): its points in the map's one cluster layer, diffed by key on
  * every [[show]] — new ones fade in, gone ones leave their bubble at once and fade out where they were drawn alone,
  * kept ones are recoloured in place (or re-drawn when their content changed); a hover or focus shows the kind's card,
  * a click or Enter activates it; switched off, it keeps the last content and shows it again when switched on. The same
  * code for every kind.
  */
final class PointLayer[A](val kind: PointKind[A], set: () => PointSet[Dot], asOf: () => Option[Years.Astronomical]):
  // `var`s: what was shown last (shown again when the layer is switched back on), whether it is on, and
  // its points in the cluster layer, by key
  private var last: Option[(Iterable[A], (Years.Astronomical, Years.Astronomical))] = None
  private var attached = false
  private val live = scala.collection.mutable.LinkedHashMap.empty[PointSet.Key, Dot]
  private val prefix = s"${kind.dot.ordinal}|"

  def isAttached: Boolean = attached

  def attach(): Unit =
    if !attached then
      attached = true
      last.foreach((items, span) => show(items, span))

  def detach(): Unit =
    if attached then
      attached = false
      set().remove(live.keys.toList)
      live.clear()

  private[web] def at(d: Dot): LatLon =
    val (lat, lon) = kind.at(d.item.asInstanceOf[A])
    LatLon(lat, lon)

  /** Show `items` for `span` (remembered while the layer is off). */
  def show(items: Iterable[A], span: (Years.Astronomical, Years.Astronomical)): Unit =
    last = Some((items, span))
    if attached then
      val next = scala.collection.mutable.LinkedHashMap.empty[PointSet.Key, A]
      items.filter(kind.keep).foreach(a => next.getOrElseUpdate(PointSet.Key(prefix + kind.key(a).value), a))
      val gone = live.keys.filterNot(next.contains).toList
      gone.foreach(live.remove)
      set().remove(gone)
      // `var`s: whether any kept point was restyled, and whether one changed more than its colour (the bubbles
      // it is in are then drawn again; a colour alone is set in place)
      var restyled = false
      var reshaped = false
      val fresh = scala.collection.mutable.ListBuffer.empty[Dot]
      next.foreach { (k, a) =>
        live.get(k) match
          case None =>
            val d = new Dot(Dot.Key(k.value), this, a, span, kind.member(a))
            live(k) = d
            fresh += d
          case Some(d) =>
            val was = d.item.asInstanceOf[A]
            if !(d.span == span && was.equals(a)) then
              val sameLook = PointMarker.signature(kind, was) == PointMarker.signature(kind, a)
              val member = kind.member(a)
              if member != d.member then reshaped = true
              d.item = a
              d.span = span
              d.member = member
              restyled = true
              if sameLook then set().drawnAlone(k).foreach(m => kind.recolour(m, a, span))
              else
                reshaped = true
                set().replace(d)
      }
      set().add(fresh)
      if reshaped then set().refresh() else if restyled then set().recolour()

  /** A point's marker, as the cluster layer draws it alone. */
  def render(d: Dot, fadeIn: PointLayer.FadeIn): DomMarker =
    val a = d.item.asInstanceOf[A]
    val (w, h) = kind.box
    val el = DomMarker.element(
      s"hd-point-wrap hd-point-wrap--${kind.shape.cls}",
      PointMarker.html(
        kind,
        a,
        kind.fill(a, d.span)
      )
    )
    kind.title(a).foreach(t => el.setAttribute("title", t.value))
    if fadeIn.value then el.classList.add(FadingLayer.fadeIn.value)
    val m = new DomMarker(at(d).lat, at(d).lon, el, Some((DomMarker.BoxSize(w.value), DomMarker.BoxSize(h.value))))
    val z = kind.zIndex(a)
    if z.value != 0 then m.setZ(DomMarker.ZIndex(z.value))
    def current: A = d.item.asInstanceOf[A]
    PlaceHover.bindPoint(
      m,
      () => kind.target(current, asOf()),
      () => PointMarker.ariaLabel(kind, current),
      how => kind.activate(current, how)
    )
    m

  /** Its row in a bubble's list chosen. */
  def open(d: Dot, row: dom.Element): Unit = kind.open(PointKind.PointKey(d.key.value), d.item.asInstanceOf[A], row)

object PointLayer:
  type FadeIn = FadeIn.T
  object FadeIn extends Newtype[Boolean, Pure]

  /** Does dot `d` already show `a` for `span`? Then a reload that brings it again leaves it. */

  private[web] def same[A](d: Dot, a: A, span: (Years.Astronomical, Years.Astronomical)): Boolean =
    d.span == span && a.equals(d.item)
