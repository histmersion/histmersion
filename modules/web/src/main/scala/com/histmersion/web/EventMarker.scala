package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventTier}

/** The pure half of the map's EVENT markers (#1449). */
object EventMarker:
  type Color = Color.T
  object Color extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Html = Html.T
  object Html extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ramp = Ramp.T
  object Ramp extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierClass = TierClass.T
  object TierClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: the marker's markup ([[PointKinds.Events]]): its kind's icon on a square whose fill is the event's date on
    * the period ramp (`--c`), as every point's. The TIER is the badge's style: `hd-ev--attested` filled;
    * `hd-ev--catalogue` paler and dashed — a research lead, not evidence (docs/visualization-guide.md §1).
    */

  def html(e: EventGeo, color: com.histmersion.web.EventMarker.Color): com.histmersion.web.EventMarker.Html = PointMarker.html(PointKinds.Events, e, color)

  /** Pure: an event in words — its name, kind and years, size, place and tier (its accessible name). */
  def describe(e: EventGeo): com.histmersion.web.EventMarker.Description =
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
  def tierClass(tier: com.histmersion.api.Api.EventQuery.Tier): com.histmersion.web.EventMarker.TierClass = if tier == EventTier.Catalogue then com.histmersion.web.EventMarker.TierClass("catalogue") else com.histmersion.web.EventMarker.TierClass("attested")

  /** Pure: where an event sits on the period ramp of the span shown (0 = its start, 1 = its end): the middle of its
    * outer span.
    */
  def rampAt(e: EventGeo, span: (com.histmersion.api.Years.Astronomical, com.histmersion.api.Years.Astronomical)): com.histmersion.web.EventMarker.Ramp =
    val (lo, hi) = (span._1.min(span._2).toDouble, span._1.max(span._2).toDouble)
    e.timeSpan.outer.fold(0.5) { (a, b) =>
      if hi == lo then 0.5 else (((a + b) / 2.0 - lo) / (hi - lo)).max(0).min(1)
    }
