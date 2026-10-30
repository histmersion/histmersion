package com.histmersion.api

/** One record of a source, ready to be linked and stored.
  *
  * `ref` is the record's id IN ITS SOURCE (`geonames:2643743`, `wikidata:Q42`, an LPF feature's `@id`) — with the
  * source key it is the provenance every row this record writes carries. `placeId` is the id the record's place gets if
  * no place already holds one of its identifiers.
  */
final case class GazRecord(
    ref: String,
    own: Option[ExternalId],
    placeId: String,
    label: String,
    kind: String,
    geometry: Option[GazGeometry],
    span: Span,
    names: List[GazName],
    links: List[GazLink],
    source: Option[String],
    properties: Option[String],
    descriptions: List[GazDescription] = Nil,
    /** The class the source gives the item (a Wikidata extract's `properties.class`, e.g. Q3024240 "historical
      * country") — kept on the record's own link (V077), joined or not.
      */
    itemClass: Option[String] = None
):
  /** Every identifier the record answers to: its own first, then its links. */
  def identifiers: List[ExternalId] = (own.toList ++ links.map(_.id)).distinct

  /** The links it writes on its place: its own id (exact) and each link not already its own. */
  def placeLinks: List[GazLink] =
    (own.map(GazLink(_, LinkMatch.Exact)).toList ++ links.filterNot(l => own.contains(l.id))).distinctBy(_.id)
