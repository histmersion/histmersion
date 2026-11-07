package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.{EventGeo, EventLeads}
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

/** One event on the map before its outline is read (the map's events). */
final private[events] case class EventGeoRow(
    row: EventRow,
    lat: com.histmersion.Latitude,
    lon: com.histmersion.Longitude,
    anchor: EventGeoRow.Anchor,
    placeLabel: Option[EventGeoRow.PlaceLabel],
    hasArea: Boolean,
    vei: Option[EventGeoRow.Vei],
    magnitude: Option[EventGeoRow.Magnitude]
) derives DbCodec:
  /** The event as the map draws it, without its outline. */
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
          vei.map(v => com.histmersion.api.catalogue.LeadFacts.Vei(v.value)),
          magnitude.map(m => com.histmersion.api.catalogue.LeadFacts.Magnitude(m.value))
        )
        .map(s => EventGeo.Size(s)),
      EventGeo.Precision(e.precision.value),
      e.datingNote.map(EventGeo.Dating(_))
    )

object EventGeoRow:
  type Anchor = Anchor.T
  object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLabel = PlaceLabel.T
  object PlaceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = Vei.T
  object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
