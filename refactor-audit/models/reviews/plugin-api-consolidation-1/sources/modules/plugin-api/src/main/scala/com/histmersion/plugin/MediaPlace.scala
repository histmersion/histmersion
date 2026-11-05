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
    id: MediaPlace.Id,
    label: MediaPlace.Label,
    wikidata: Option[WikidataId] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    period: Option[Period] = None,
    ids: List[(MediaPlace.IdsPart1, MediaPlace.IdsPart2)] = Nil
):
  /** The place as a host of SPI 2's first release described it: no gazetteer ids. */
  def this(
      id: String,
      label: String,
      wikidata: Option[WikidataId],
      lat: Option[Latitude],
      lon: Option[Longitude],
      period: Option[Period]
  ) = this(_root_.com.histmersion.plugin.MediaPlace.Id(id), _root_.com.histmersion.plugin.MediaPlace.Label(label), wikidata, lat, lon, period, Nil)

object MediaPlace:
  /** The place as a host of SPI 2's first release described it: no gazetteer ids. */
  type ApplyId = _root_.com.histmersion.plugin.MediaPlace.Id
  val ApplyId: _root_.com.histmersion.plugin.MediaPlace.Id.type = _root_.com.histmersion.plugin.MediaPlace.Id
  type ApplyLabel = _root_.com.histmersion.plugin.MediaPlace.Label
  val ApplyLabel: _root_.com.histmersion.plugin.MediaPlace.Label.type = _root_.com.histmersion.plugin.MediaPlace.Label

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsPart1 = IdsPart1.T
  object IdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsPart2 = IdsPart2.T
  object IdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      id: com.histmersion.plugin.MediaPlace.ApplyId,
      label: com.histmersion.plugin.MediaPlace.ApplyLabel,
      wikidata: Option[WikidataId],
      lat: Option[Latitude],
      lon: Option[Longitude],
      period: Option[Period]
  ): MediaPlace = new MediaPlace(id, label, wikidata, lat, lon, period, Nil)
