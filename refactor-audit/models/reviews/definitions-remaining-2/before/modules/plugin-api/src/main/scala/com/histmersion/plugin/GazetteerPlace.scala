package com.histmersion.plugin

/** One place of a gazetteer dataset, in the shape the host stores it.
  *
  * @param id
  *   its id on the map — stable and scoped by the dataset (`cliopatria_hittite_empire_-1650_-1401`), so a re-import
  *   replaces it rather than adding another
  * @param label
  *   its display name
  * @param kind
  *   `region`, `polity`, `site`, `feature` or `people` (an open vocabulary: the map offers these five)
  * @param geometry
  *   its shape as a GeoJSON geometry (`{"type":"Polygon",…}`), WGS84; `None`: a place of the hierarchy only
  * @param period
  *   when this shape or entity is valid; `None`: always
  * @param open
  *   the end of [[period]] the dataset does not know, if one
  * @param source
  *   the provenance line of this place; `None`: the dataset's credit ([[GazetteerRights.credit]])
  * @param properties
  *   what else the dataset says of it, as a JSON object (`{"wikidata":"Q170494"}`)
  * @param names
  *   its names, the display name included
  * @param relations
  *   the places it is part of
  */
final case class GazetteerPlace(
    id: String,
    label: String,
    kind: String,
    geometry: Option[String] = None,
    period: Option[Period] = None,
    open: Option[GazetteerOpenSide] = None,
    source: Option[String] = None,
    properties: Option[String] = None,
    names: List[GazetteerName] = Nil,
    relations: List[GazetteerRelation] = Nil
)
