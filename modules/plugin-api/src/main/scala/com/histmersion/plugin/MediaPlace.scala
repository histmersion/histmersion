package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

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
    id: MediaPlace.Id,
    label: MediaPlace.Label,
    wikidata: Option[WikidataId] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    period: Option[Period] = None,
    ids: List[(GazetteerRef.Scheme, GazetteerRef.Id)] = Nil
)

object MediaPlace:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
