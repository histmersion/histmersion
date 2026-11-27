package com.histmersion
package domain.catalogue.events

import com.histmersion.api.catalogue.{EventGeo, EventLeads}
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.catalogue.LeadFacts
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One event on the map before its outline is read (the map's events). */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
final private[events] case class EventGeoRow(
    row: EventRow,
    lat: Latitude,
    lon: Longitude,
    anchor: EventGeoRow.Anchor,
    placeLabel: Option[EventGeoRow.PlaceLabel],
    hasArea: Boolean,
    vei: Option[EventGeoRow.Vei],
    magnitude: Option[EventGeoRow.Magnitude]
) derives DbCodec:
  /** The event as the map draws it, without its outline. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def toGeo: EventGeo =
    val e = row.toSummary
    EventGeo(
      e.id,
      EventGeo.Label(e.label.value),
      EventGeo.Kind(e.kind.value),
      EventGeo.Scale(e.scale.value),
      EventGeo.Historicity(e.historicity.value),
      e.timeSpan,
      com.histmersion.api.catalogue.Latitude(lat.value),
      com.histmersion.api.catalogue.Longitude(lon.value),
      EventGeo.Anchor(anchor.value),
      e.placeId,
      placeLabel.map(l => EventGeo.PlaceLabel(l.value)),
      e.parentId.map(p => EventGeo.ParentId(p.value)),
      None,
      EventGeo.Tier(e.tier.value),
      EventLeads
        .size(
          vei.map(v => LeadFacts.Vei(v.value)),
          magnitude.map(m => LeadFacts.Magnitude(m.value))
        )
        .map(s => EventGeo.Size(s)),
      EventGeo.Precision(e.precision.value),
      e.datingNote.map(EventGeo.Dating(_))
    )

object EventGeoRow:
  type Anchor = Anchor.T
  object Anchor extends Newtype[String, Pure]
  type PlaceLabel = PlaceLabel.T
  object PlaceLabel extends Newtype[String, Pure]
  type Vei = Vei.T
  object Vei extends Newtype[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends Newtype[Double, Pure]
