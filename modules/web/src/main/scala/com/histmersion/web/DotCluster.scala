package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.web.leaflet.*
import org.scalajs.dom

import scala.scalajs.js

/** What a dot on the map stands for. Every kind but the areas (regions, polities: shapes, never dots)
  * joins the one cluster group of the map ([[MapView]]), so dots near each other are drawn as one
  * bubble whatever layer they come from. */
enum DotKind(val one: String, val many: String):
  case Finding extends DotKind("finding", "findings")
  case Site    extends DotKind("site", "sites")
  case Event   extends DotKind("event", "events")
  case Media   extends DotKind("picture", "pictures")

/** A clustered dot that is not a finding — a site, an event, a picture: what the bubble counts and
  * colours, and the row its list shows (its kind's icon, its name, a note, `mid` the middle of its
  * dated span on the period ramp). A finding's dot carries its [[GeoHit]]s instead. */
final case class DotEntry(kind: DotKind, id: String, label: String, icon: String, mid: Option[Double], note: String = "")

/** What one clustered marker holds, for the bubble it may be drawn in: its kind, how many things it
  * counts for (a findings' point counts its findings), their dated mids and least certainty
  * ([[Certainty.NoSay]] for a dot that is not a finding), and what its bubble's list shows — the findings
  * themselves, or the dot's row. */
final case class Member(kind: DotKind, n: Int, mids: List[Double], certainty: Certainty,
    findings: List[GeoHit] = Nil, entry: Option[DotEntry] = None)

/** The pure half of the mixed clusters: what a bubble holds, says and looks like. */
object DotCluster:

  /** A bubble's content: what its markers hold. */
  final case class Contents(members: List[Member]):
    def total: Int = members.map(_.n).sum
    def counts: Map[DotKind, Int] = members.groupMapReduce(_.kind)(_.n)(_ + _)
    def findings: List[GeoHit] = members.flatMap(_.findings)
    def entries: List[DotEntry] = members.flatMap(_.entry)
    /** The middle of every dated member's span: the bubble's colour is their range. */
    def mids: List[Double] = members.flatMap(_.mids)
    /** The least certain member's certainty (only findings carry one other than [[Certainty.NoSay]]). */
    def certainty: Certainty = Certainty.worst(members.map(_.certainty))

  /** Pure: what a bubble holds, in words — "12 findings · 3 sites · 2 events", the kinds in a fixed
    * order, those it has none of left out. */
  def summary(counts: Map[DotKind, Int]): String =
    DotKind.values.toList.flatMap(k => counts.get(k).filter(_ > 0).map(n => Plural.count(n, k.one, k.many))).mkString(" · ")

  def summary(c: Contents): String = summary(c.counts)

  /** Is it a bubble of more than one kind? */
  def mixed(c: Contents): Boolean = c.counts.count(_._2 > 0) > 1

  /** How far a bubble's rings reach outside its disc, px: a dark line, the white coin ring, a dark line. */
  val Ring: Int = 5

  /** Pure: a bubble's markup — its count on a disc of diameter `d` in its colour (`ends`: the colours of
    * the oldest and the newest of its dates, the same when they agree), ringed by a dark line, a white coin
    * ring and a dark line, and (`says`) what it holds, to the pointer and to assistive tech. The disc and
    * the rings are ONE inline SVG, every circle about the same centre: drawn as box-shadows round a CSS
    * background they were rasterised apart — the background snapped to the device pixel grid, the shadows
    * not — so wherever a marker landed off that grid (a fractional device-pixel ratio or page zoom, a map
    * under a toolbar of fractional height) the disc sat up to a pixel off-centre in its rings. `id` names
    * the disc's gradient; it must be unique on the page. */
  def bubbleHtml(n: Int, d: Int, ends: (String, String), id: String, extraClass: String, says: Option[String]): String =
    val label = if n > 1 then n.toString else ""
    val words = says.fold("")(w => s""" role="img" title="${Fmt.esc(w)}" aria-label="${Fmt.esc(w)}"""")
    val s = d + 2 * Ring
    val c = s / 2.0
    val r = d / 2.0
    def circle(cls: String, radius: Double, extra: String = "") = s"""<circle class="$cls" cx="$c" cy="$c" r="$radius"$extra/>"""
    val svg =
      s"""<svg class="hd-bubble" viewBox="0 0 $s $s" aria-hidden="true" focusable="false">""" +
        s"""<defs><linearGradient id="$id" x1="0" y1="0" x2="1" y2="1">""" +
        """<stop offset="0" class="hd-bubble-g0"/><stop offset="1" class="hd-bubble-g1"/></linearGradient></defs>""" +
        circle("hd-bubble-fill", r, s""" fill="url(#$id)"""") +
        circle("hd-bubble-line", r + 0.5) + circle("hd-bubble-coin", r + 2.5) + circle("hd-bubble-line", r + 4.5) +
        "</svg>"
    s"""<span class="${s"hd-dot hd-cluster $extraClass".trim}" style="--d:${d}px;--fs:${math.min(12, d / 2)}px;--ring:${Ring}px;""" +
      s"""--g0:${ends._1};--g1:${ends._2}"$words>$svg<span class="hd-dot-count">$label</span></span>"""

  /** Pure: the middle of a dated span, for the ramp. */
  def mid(start: Option[Long], end: Option[Long]): Option[Double] =
    for a <- start; b <- end yield (a + b).toDouble / 2

  // ── the members, as the map holds them (set by PointLayer) ────────────────────────────────────

  def memberOf(m: js.Dynamic): Option[Member] =
    if js.isUndefined(m.hdMember) then None else Some(m.hdMember.asInstanceOf[Member])

  /** What the markers of a bubble hold. */
  def contents(markers: js.Array[js.Dynamic]): Contents = Contents(markers.toList.flatMap(memberOf))

  /** The rows of a bubble's list that are not findings, each with how it opens. */
  def others(markers: js.Array[js.Dynamic]): List[(DotEntry, dom.Element => Unit)] =
    markers.toList.flatMap(m => memberOf(m).flatMap(_.entry).map(e =>
      e -> m.hdOpen.asInstanceOf[js.Function1[dom.Element, Unit]].apply))

  /** Pure: the order of a bubble's list — by kind, then by name. */
  def ordered(others: List[(DotEntry, dom.Element => Unit)]): List[(DotEntry, dom.Element => Unit)] =
    others.sortBy((e, _) => (e.kind.ordinal, e.label.toLowerCase, e.id))

  /** A card pinned under a list row (`row`): the row is its owner, so the click that pinned it does not
    * count as a click outside it. */
  def pinUnder(t: Preview.Target, row: dom.Element, keyboard: Boolean = false): Unit =
    Preview.pin(t, () => Option.when(row.isConnected) {
      val r = row.getBoundingClientRect()
      Preview.Anchor.Under(r.left, r.top, r.bottom, r.width)
    }, owner = Some(row), keyboard = keyboard)
