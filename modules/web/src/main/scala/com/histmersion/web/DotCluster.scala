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

/** The pure half of the mixed clusters: what a bubble holds, says and looks like. */
object DotCluster:

  /** A bubble's content: the findings of its finding dots, and its other dots. */
  final case class Contents(findings: List[GeoHit], others: List[DotEntry]):
    def total: Int = findings.size + others.size
    def counts: Map[DotKind, Int] =
      others.groupMapReduce(_.kind)(_ => 1)(_ + _) ++
        (if findings.isEmpty then Map.empty else Map(DotKind.Finding -> findings.size))
    /** The middle of every dated member's span: the bubble's colour is their range. */
    def mids: List[Double] = findings.flatMap(MapView.midOf) ++ others.flatMap(_.mid)
    /** The least certain finding's confidence; the other kinds carry none (they are drawn at full
      * strength, as their own markers are). */
    def confidence: Confidence =
      if findings.isEmpty then Confidence.High else Fmt.worstConf(findings.map(_.confidence.getOrElse(Confidence.High)))

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

  // ── the members, as the map holds them ─────────────────────────────────────────────────────────

  /** Mark `layer` as a clustered dot: what it is and how its row in a bubble's list opens it (given
    * the row, so a card can be pinned under it). */
  def tag(layer: Layer, entry: DotEntry, open: dom.Element => Unit): Unit =
    val d = layer.asInstanceOf[js.Dynamic]
    d.hdDot = entry.asInstanceOf[js.Any]
    d.hdOpen = (open: js.Function1[dom.Element, Unit])

  /** The dot a member was tagged with, if it was. */
  def entryOf(m: js.Dynamic): Option[DotEntry] =
    if js.isUndefined(m.hdDot) then None else Some(m.hdDot.asInstanceOf[DotEntry])

  /** The findings a finding's dot holds, if it is one. */
  def findingsOf(m: js.Dynamic): js.Array[GeoHit] =
    if js.isUndefined(m.hdItems) then js.Array() else m.hdItems.asInstanceOf[js.Array[GeoHit]]

  /** What the members of a bubble hold. */
  def contents(members: js.Array[js.Dynamic]): Contents =
    val f = List.newBuilder[GeoHit]
    val o = List.newBuilder[DotEntry]
    members.foreach { m =>
      findingsOf(m).foreach(f += _)
      entryOf(m).foreach(o += _)
    }
    Contents(f.result(), o.result())

  /** The rows of a bubble's list that are not findings, each with how it opens. */
  def others(members: js.Array[js.Dynamic]): List[(DotEntry, dom.Element => Unit)] =
    members.toList.flatMap(m => entryOf(m).map(e => e -> m.hdOpen.asInstanceOf[js.Function1[dom.Element, Unit]].apply))

  /** Pure: the order of a bubble's list — by kind (findings first), then by name. */
  def ordered(others: List[(DotEntry, dom.Element => Unit)]): List[(DotEntry, dom.Element => Unit)] =
    others.sortBy((e, _) => (e.kind.ordinal, e.label.toLowerCase, e.id))

  /** A card pinned under a list row (`row`): the row is its owner, so the click that pinned it does not
    * count as a click outside it. */
  def pinUnder(t: Preview.Target, row: dom.Element): Unit =
    Preview.pin(t, () => Option.when(row.isConnected) {
      val r = row.getBoundingClientRect()
      Preview.Anchor.Under(r.left, r.top, r.bottom, r.width)
    }, owner = Some(row))
