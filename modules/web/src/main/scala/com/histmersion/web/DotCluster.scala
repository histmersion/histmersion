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
  * counts for (a findings' point counts its findings), their dated mids and least confidence, and what
  * its bubble's list shows — the findings themselves, or the dot's row. */
final case class Member(kind: DotKind, n: Int, mids: List[Double], confidence: Confidence,
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
    /** The least certain member's confidence (only findings carry one below `high`). */
    def confidence: Confidence = Fmt.worstConf(members.map(_.confidence) :+ Confidence.High)

  /** Pure: what a bubble holds, in words — "12 findings · 3 sites · 2 events", the kinds in a fixed
    * order, those it has none of left out. */
  def summary(counts: Map[DotKind, Int]): String =
    DotKind.values.toList.flatMap(k => counts.get(k).filter(_ > 0).map(n => Plural.count(n, k.one, k.many))).mkString(" · ")

  def summary(c: Contents): String = summary(c.counts)

  /** Is it a bubble of more than one kind? */
  def mixed(c: Contents): Boolean = c.counts.count(_._2 > 0) > 1

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
