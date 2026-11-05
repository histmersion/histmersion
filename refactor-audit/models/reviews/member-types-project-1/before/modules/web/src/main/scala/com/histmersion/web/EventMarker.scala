package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventTier}

/** The pure half of the map's EVENT markers (#1449). */
object EventMarker:

  /** Pure: the marker's markup ([[PointKinds.Events]]): its kind's icon on a square whose fill is the event's date on
    * the period ramp (`--c`), as every point's. The TIER is the badge's style: `hd-ev--attested` filled;
    * `hd-ev--catalogue` paler and dashed — a research lead, not evidence (docs/visualization-guide.md §1).
    */
  def html(e: EventGeo, color: String): String = PointMarker.html(PointKinds.Events, e, color)

  /** Pure: an event in words — its name, kind and years, size, place and tier (its accessible name). */
  def describe(e: EventGeo): String =
    List(
      Some(e.label),
      Some(EventPlayback.note(e)),
      e.dating,
      EventPrecision.label(e.precision),
      e.size,
      e.placeLabel,
      Some(if e.tier == EventTier.Catalogue then "catalogue only" else "attested")
    ).flatten.mkString(" · ")

  /** Pure: the tier's class name — anything but `catalogue` is drawn attested. */
  def tierClass(tier: String): String = if tier == EventTier.Catalogue then "catalogue" else "attested"

  /** Pure: where an event sits on the period ramp of the span shown (0 = its start, 1 = its end): the middle of its
    * outer span.
    */
  def rampAt(e: EventGeo, span: (Long, Long)): Double =
    val (lo, hi) = (span._1.min(span._2).toDouble, span._1.max(span._2).toDouble)
    e.timeSpan.outer.fold(0.5) { (a, b) =>
      if hi == lo then 0.5 else (((a + b) / 2.0 - lo) / (hi - lo)).max(0).min(1)
    }
