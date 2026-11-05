package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{EventPrecision, EventScale, EventSummary}
import com.augustnagro.magnum.DbCodec

/** An `events` row as the reads select it, with what its scale is derived from (its place's kind, its geometry's type)
  * and its tier.
  */
final private[events] case class EventRow(
    id: String,
    label: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    historicity: String,
    placeId: Option[String],
    parentId: Option[String],
    description: Option[String],
    citekey: Option[String],
    scale: Option[String],
    placeKind: Option[String],
    geometryType: Option[String],
    tier: String,
    central: Option[Long],
    plusMinus: Option[Long],
    datingMethod: Option[String],
    uncertainty: Option[Long]
) derives DbCodec:
  def toSummary: EventSummary =
    EventSummary(
      id,
      label,
      kind,
      TimeSpan(notBefore, beginBy, endFrom, notAfter),
      historicity,
      placeId,
      parentId,
      description,
      citekey,
      EventScale.effective(scale, kind, parentId.isDefined, placeKind, geometryType),
      scale.isDefined,
      tier,
      EventPrecision.of(uncertainty),
      central,
      plusMinus,
      datingMethod
    )
