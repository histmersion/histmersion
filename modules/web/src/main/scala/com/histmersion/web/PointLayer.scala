package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.scalajs.js

/** The marker SHAPE — what kind of thing a point is (docs/visualization-guide.md §2): a diamond a finding
  * (a source's claim), a circle a site, a rounded square an event, a landscape photo print a picture. */
enum Shape(val cls: String):
  case Diamond extends Shape("diamond")
  case Circle  extends Shape("circle")
  case Square  extends Shape("square")
  case Photo   extends Shape("photo")

/** How a point was activated: a click (where on the map) or the keyboard (on which element). */
final case class Activation(keyboard: Boolean, element: Option[dom.Element], latlng: Option[LatLng])

/** What one KIND of point is (docs/map-layers.md): everything the map does with a point that differs
  * between findings, sites, events and pictures, as data. [[PointLayer]] does the rest, the same way for
  * all of them. */
trait PointKind[A]:
  /** What a cluster bubble counts it as. */
  def dot: DotKind
  def shape: Shape
  /** The marker's box, in px (the shape is drawn inside it at the same optical size for every kind). */
  def box: (Int, Int)
  /** The Leaflet pane (above the areas); `None` for the marker pane. */
  def pane: Option[String] = None
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
  /** Anything bound to the marker that follows its content (a popup) — on creation and on each update. */
  def decorate(m: Marker, a: A): Unit = ()
  /** A click or Enter on it. */
  def activate(m: Marker, a: A, how: Activation): Unit
  /** Its row in a bubble's list chosen (the row given, to pin a card under). */
  def open(m: Marker, a: A, row: dom.Element): Unit
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

/** ONE layer of points of one kind (docs/map-layers.md): its markers in the map's one cluster group
  * (`sink`), diffed by key on every [[show]] — new ones fade in, gone ones leave their bubble at once
  * and fade out where they were drawn alone, kept ones are recoloured in place (or re-drawn when their
  * content changed); a hover or focus shows the kind's card, a click or Enter activates it; switched off,
  * it keeps the last content and shows it again when switched on. The same code for every kind. */
final class PointLayer[A](val kind: PointKind[A], sink: () => FadingLayer.Sink, asOf: () => Option[Long]):
  private val fading = FadingLayer(Some(sink))
  // `var`: what was shown last, for the span — shown again when the layer is switched back on
  private var last: Option[(Iterable[A], (Long, Long))] = None

  def isAttached: Boolean = fading.isAttached

  def attach(map: LeafletMap): Unit =
    fading.attach(map)
    last.foreach((items, span) => show(items, span))

  def detach(map: LeafletMap): Unit = fading.detach(map)

  /** Show `items` for `span` (remembered while the layer is off). */
  def show(items: Iterable[A], span: (Long, Long)): Unit =
    last = Some((items, span))
    fading.sync[A](items.filter(kind.keep), kind.key, a => build(a, span), (l, a) => refresh(l, a, span))

  private def current(m: Marker): A = m.asInstanceOf[js.Dynamic].hdItem.asInstanceOf[A]

  private def icon(a: A, span: (Long, Long)): DivIcon =
    val (w, h) = kind.box
    L.divIcon(js.Dynamic.literal(className = s"hd-point-wrap hd-point-wrap--${kind.shape.cls}",
      iconSize = js.Array(w, h), iconAnchor = js.Array(w / 2, h / 2),
      html = PointMarker.html(kind, a, kind.fill(a, span))).asInstanceOf[js.Object])

  private def build(a: A, span: (Long, Long)): Layer =
    val opts = js.Dynamic.literal(icon = icon(a, span), keyboard = false, zIndexOffset = kind.zIndex(a))
    kind.pane.foreach(p => opts.updateDynamic("pane")(p))
    kind.title(a).foreach(t => opts.updateDynamic("title")(t))
    val (lat, lon) = kind.at(a)
    val m = L.marker(js.Array(lat, lon), opts.asInstanceOf[js.Object])
    val d = m.asInstanceOf[js.Dynamic]
    d.hdItem = a.asInstanceOf[js.Any]
    d.hdMember = kind.member(a).asInstanceOf[js.Any]
    d.hdSig = PointMarker.signature(kind, a)
    d.hdOpen = ((row: dom.Element) => kind.open(m, current(m), row)): js.Function1[dom.Element, Unit]
    kind.decorate(m, a)
    PlaceHover.bindPoint(m, () => kind.target(current(m), asOf()), () => PointMarker.ariaLabel(kind, current(m)),
      how => kind.activate(m, current(m), how))
    m

  /** A kept point: its new content and colour. Only the colour changed — it is set in place (`--c`, a
    * CSS transition) and in the icon it is drawn from next (it may be inside a bubble now); otherwise the
    * marker is re-drawn. */
  private def refresh(l: Layer, a: A, span: (Long, Long)): Unit =
    val m = l.asInstanceOf[Marker]
    val d = l.asInstanceOf[js.Dynamic]
    d.hdItem = a.asInstanceOf[js.Any]
    d.hdMember = kind.member(a).asInstanceOf[js.Any]
    val fresh = icon(a, span)
    val sig = PointMarker.signature(kind, a)
    val el = d.getElement()
    val drawn = !js.isUndefined(el) && el != null
    if d.hdSig.asInstanceOf[String] == sig then
      d.options.icon = fresh
      if drawn then
        Option(el.asInstanceOf[dom.Element].querySelector(".hd-point")).foreach(
          _.asInstanceOf[dom.html.Element].style.setProperty("--c", kind.fill(a, span)))
    else
      d.hdSig = sig
      d.options.title = kind.title(a).getOrElse("")
      d.setIcon(fresh)
      if drawn then
        val e = el.asInstanceOf[dom.Element]
        kind.title(a).fold(e.removeAttribute("title"))(t => e.setAttribute("title", t))
        e.setAttribute("aria-label", PointMarker.ariaLabel(kind, a))
    kind.decorate(m, a)
