package com.histmersion.plugin

/** A place on the map the host asks a collection about.
  *
  * @param id
  *   the place's id on the map (`pleiades_570491`)
  * @param label
  *   its name, as the map shows it — what a collection searches for
  * @param wikidata
  *   its Wikidata item (`Q5788`), when it has one
  * @param lat
  *   its latitude, when it has a point
  * @param lon
  *   its longitude, when it has a point
  * @param period
  *   its period — the place's own, else the span of the findings it holds
  */
final case class MediaPlace(
    id: String,
    label: String,
    wikidata: Option[WikidataId] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    period: Option[Period] = None
)
