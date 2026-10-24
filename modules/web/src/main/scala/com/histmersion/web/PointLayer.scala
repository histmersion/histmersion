package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** The marker SHAPE — what kind of thing a point is (docs/visualization-guide.md §2): a diamond a finding
  * (a source's claim), a circle a site, a square an event, a landscape photo print (a white mat) a picture. */
enum Shape(val cls: String):
  case Diamond extends Shape("diamond")
  case Circle  extends Shape("circle")
  case Square  extends Shape("square")
  case Photo   extends Shape("photo")

/** How a point was activated: a click (where on the map) or the keyboard (on which element). */
final case class Activation(keyboard: Boolean, element: Option[dom.Element], latlng: Option[LatLon])

/** What one KIND of point is (docs/map-layers.md): everything the map does with a point that differs
  * between findings, sites, events and pictures, as data. [[PointLayer]] does the rest, the same way for
  * all of them. */
trait PointKind[A]:
  /** What a cluster bubble counts it as. */
  def dot: DotKind
  def shape: Shape
  /** Its layer switch's icon: its shape, outlined (the toolbar, the map's key). */
  def icon: Icon
  /** Its name in the toolbar, and what one marker means in the map's key. */
  def layerLabel: String
  def meaning: String
  /** The marker's box, in px (the shape is drawn inside it at the same optical size for every kind). */
  def box: (Int, Int)
  /** The pane its markers are drawn in (above the areas). */
  def pane: MapStack.Pane = MapStack.Pane.Markers
  def key(a: A): String
  def at(a: A): (Double, Double)
  /** The badge's content: its glyph ([[Glyphs.badge]]; the exclamation mark when its type is unknown),
    * or a count. */
  def inner(a: A): String
  /** Classes beyond the shape — its tier, its basis, its period-span ring. */
  def classes(a: A): String = ""
  /** Data attributes (`data-…`), each with a leading space. */
  def attrs(a: A): String = ""
  /** Its fill for the span shown: the period colour of its dates (a gradient over several). */
  def fill(a: A, span: (Long, Long)): String
  /** What it counts for in a bubble, and its row in the bubble's list. */
  def member(a: A): Member
  /** Its accessible name, and what activating it does ("pin its card"). */
  def label(a: A): String
  def action(a: A): String
  /** A native tooltip, only where it says something the card does not. */
  def title(a: A): Option[String] = None
  /** The card its hover shows, read at the map's year. */
  def target(a: A, asOf: Option[Long]): Option[Preview.Target]
  def zIndex(a: A): Int = 0
  /** A click or Enter on it. */
  def activate(a: A, how: Activation): Unit
  /** Its row in a bubble's list chosen (the row given, to pin a card under); `key` its point's key in
    * the map's cluster layer (to bring it out of its bubble). */
  def open(key: String, a: A, row: dom.Element): Unit
  /** The client-side part of its filter (a menu the server is not asked about). */
  def keep(a: A): Boolean = true

/** The pure half: a point's marker markup. */
object PointMarker:
  /** Pure: the badge — shape class, the kind's classes and attributes, the fill as `--c`, the content. */
  def html[A](k: PointKind[A], a: A, fill: String): String =
    s"""<span class="hd-point hd-badge hd-badge--${k.shape.cls} ${k.classes(a)}" style="--c:$fill"${k.attrs(a)}>${k.inner(a)}</span>"""

  /** Pure: what of the markup changes only by re-drawing (everything but the colour, which is set in
    * place, so it animates). */
  def signature[A](k: PointKind[A], a: A): String = s"${k.classes(a)}|${k.attrs(a)}|${k.inner(a)}|${k.title(a).getOrElse("")}"

  /** Pure: a point's accessible name: what it is, then what activating it does. */
  def ariaLabel[A](k: PointKind[A], a: A): String = s"${k.label(a)} — ${k.action(a)}"

/** One point on the map, of any kind: what the map's ONE cluster layer ([[MapView.dots]]) holds. Its
  * item and span change in place when only its colour moved with the span. */
final class Dot(val key: String, val layer: PointLayer[?], var item: Any, var span: (Long, Long), var member: Member):
  def at: LatLon = layer.at(this)

/** ONE layer of points of one kind (docs/map-layers.md): its points in the map's one cluster layer,
  * diffed by key on every [[show]] — new ones fade in, gone ones leave their bubble at once and fade out
  * where they were drawn alone, kept ones are recoloured in place (or re-drawn when their content
  * changed); a hover or focus shows the kind's card, a click or Enter activates it; switched off, it
  * keeps the last content and shows it again when switched on. The same code for every kind. */
final class PointLayer[A](val kind: PointKind[A], set: () => PointSet[Dot], asOf: () => Option[Long]):
  // `var`s: what was shown last (shown again when the layer is switched back on), whether it is on, and
  // its points in the cluster layer, by key
  private var last: Option[(Iterable[A], (Long, Long))] = None
  private var attached = false
  private val live = scala.collection.mutable.LinkedHashMap.empty[String, Dot]
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
  def show(items: Iterable[A], span: (Long, Long)): Unit =
    last = Some((items, span))
    if attached then
      val next = scala.collection.mutable.LinkedHashMap.empty[String, A]
      items.filter(kind.keep).foreach(a => next.getOrElseUpdate(prefix + kind.key(a), a))
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
            val d = Dot(k, this, a, span, kind.member(a))
            live(k) = d
            fresh += d
          case Some(d) =>
            val was = d.item.asInstanceOf[A]
            if !(d.span == span && was == a) then
              val sameLook = PointMarker.signature(kind, was) == PointMarker.signature(kind, a)
              val member = kind.member(a)
              if member != d.member then reshaped = true
              d.item = a
              d.span = span
              d.member = member
              restyled = true
              if sameLook then
                set().drawnAlone(k).foreach(m => Option(m.el.querySelector(".hd-point")).foreach(
                  _.asInstanceOf[dom.html.Element].style.setProperty("--c", kind.fill(a, span))))
              else
                reshaped = true
                set().replace(d)
      }
      set().add(fresh)
      if reshaped then set().refresh() else if restyled then set().recolour()

  /** A point's marker, as the cluster layer draws it alone. */
  def render(d: Dot, fadeIn: Boolean): DomMarker =
    val a = d.item.asInstanceOf[A]
    val (w, h) = kind.box
    val el = DomMarker.element(s"hd-point-wrap hd-point-wrap--${kind.shape.cls}", PointMarker.html(kind, a, kind.fill(a, d.span)))
    kind.title(a).foreach(t => el.setAttribute("title", t))
    if fadeIn then el.classList.add(FadingLayer.fadeIn.value)
    val m = DomMarker(at(d).lat, at(d).lon, el, Some((w, h)))
    val z = kind.zIndex(a)
    if z != 0 then m.setZ(z)
    def current: A = d.item.asInstanceOf[A]
    PlaceHover.bindPoint(m, () => kind.target(current, asOf()), () => PointMarker.ariaLabel(kind, current),
      how => kind.activate(current, how))
    m

  /** Its row in a bubble's list chosen. */
  def open(d: Dot, row: dom.Element): Unit = kind.open(d.key, d.item.asInstanceOf[A], row)

object PointLayer:
  /** Does dot `d` already show `a` for `span`? Then a reload that brings it again leaves it. */
  private[web] def same[A](d: Dot, a: A, span: (Long, Long)): Boolean = d.span == span && d.item == a
