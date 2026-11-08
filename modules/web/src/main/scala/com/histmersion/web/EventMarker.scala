package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventTier}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Year

/** The pure half of the map's EVENT markers (#1449). */
object EventMarker:
  type Color = Color.T
  object Color extends RefinedType[String, Pure]
  type Description = Description.T
  object Description extends RefinedType[String, Pure]
  type Html = Html.T
  object Html extends RefinedType[String, Pure]
  type Ramp = Ramp.T
  object Ramp extends RefinedType[Double, Pure]
  type TierClass = TierClass.T
  object TierClass extends RefinedType[String, Pure]

  /** Pure: the marker's markup ([[PointKinds.Events]]): its kind's icon on a square whose fill is the event's date on
    * the period ramp (`--c`), as every point's. The TIER is the badge's style: `hd-ev--attested` filled;
    * `hd-ev--catalogue` paler and dashed — a research lead, not evidence (docs/visualization-guide.md §1).
    */

  def html(e: EventGeo, color: Color): Html = Html(PointMarker.html(PointKinds.Events, e, color.value))

  /** Pure: an event in words — its name, kind and years, size, place and tier (its accessible name). */
  def describe(e: EventGeo): Description =
    Description(List(
      Some(e.label.value),
      Some(EventPlayback.note(e).value),
      e.dating.map(_.value),
      EventPrecision.label(EventSummary.Precision(e.precision.value)),
      e.size.map(_.value),
      e.placeLabel.map(_.value),
      Some(if e.tier.value == EventTier.Catalogue then "catalogue only" else "attested")
    ).flatten.mkString(" · "))

  /** Pure: the tier's class name — anything but `catalogue` is drawn attested. */
  def tierClass(tier: Api.EventQuery.Tier): TierClass = if tier.value == EventTier.Catalogue then TierClass("catalogue") else TierClass("attested")

  /** Pure: where an event sits on the period ramp of the span shown (0 = its start, 1 = its end): the middle of its
    * outer span.
    */
  def rampAt(e: EventGeo, span: (Year, Year)): Ramp =
    val (lo, hi) = (span._1.value.min(span._2.value).toDouble, span._1.value.max(span._2.value).toDouble)
    Ramp(e.timeSpan.outer.fold(0.5) { (a, b) =>
      if hi == lo then 0.5 else (((a.value + b.value) / 2.0 - lo) / (hi - lo)).max(0).min(1)
    })
