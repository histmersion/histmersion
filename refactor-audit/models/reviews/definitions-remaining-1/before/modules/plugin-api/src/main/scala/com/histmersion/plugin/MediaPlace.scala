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
  * @param ids
  *   the gazetteer ids it carries, as (scheme, id) — given to a collection that asks for places carrying one
  *   ([[MediaCollection.placesCarrying]]): `("pleiades", "570491")`
  */
final case class MediaPlace(
    id: String,
    label: String,
    wikidata: Option[WikidataId] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    period: Option[Period] = None,
    ids: List[(String, String)] = Nil
):
  /** The place as a host of SPI 2's first release described it: no gazetteer ids. */
  def this(
      id: String,
      label: String,
      wikidata: Option[WikidataId],
      lat: Option[Latitude],
      lon: Option[Longitude],
      period: Option[Period]
  ) = this(id, label, wikidata, lat, lon, period, Nil)

object MediaPlace:
  /** The place as a host of SPI 2's first release described it: no gazetteer ids. */
  def apply(
      id: String,
      label: String,
      wikidata: Option[WikidataId],
      lat: Option[Latitude],
      lon: Option[Longitude],
      period: Option[Period]
  ): MediaPlace = new MediaPlace(id, label, wikidata, lat, lon, period, Nil)
