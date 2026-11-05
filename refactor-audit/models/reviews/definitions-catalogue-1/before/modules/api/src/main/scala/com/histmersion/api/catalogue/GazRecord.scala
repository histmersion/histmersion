package com.histmersion.api.catalogue

import com.histmersion.api.Span
import com.histmersion.api.library.ExternalId
import com.histmersion.api.given
final case class GazRecord(
    ref: String,
    own: Option[ExternalId],
    placeId: com.histmersion.api.catalogue.Place.Id,
    label: String,
    kind: String,
    geometry: Option[GazGeometry],
    span: Span,
    names: List[GazName],
    links: List[GazLink],
    source: Option[String],
    properties: Option[String],
    descriptions: List[GazDescription] = Nil,
    itemClass: Option[String] = None
):
  def identifiers: List[ExternalId] = (own.toList ++ links.map(_.id)).distinct

  def placeLinks: List[GazLink] =
    (own.map(GazLink(_, LinkMatch.Exact)).toList ++ links.filterNot(l => own.contains(l.id))).distinctBy(_.id)
