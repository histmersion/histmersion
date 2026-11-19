package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure

/** How the gazetteer resolved a [[Filter]]'s `places` values. Computing it needs the database, so it is done ONCE per
  * query by the handler ([[PlaceRepo.scopeFor]]) and passed into the pure `Filter → SQL` builders; an empty scope means
  * no value named a known place and every one of them falls back to the wave-1 name ILIKE, exactly as before the
  * gazetteer existed.
  */
final case class PlaceScope(
    byValue: Map[PlaceScope.Value, List[Place.Id]],
    labels: Map[PlaceScope.Value, PlaceRepo.Label] = Map.empty
):
  /** The place ids one filter value stands for — itself plus its descendants as of the filter's period. `None` when the
    * value is not a place id.
    */
  def idsFor(value: PlaceScope.Value): Option[List[Place.Id]] = byValue.get(value).filter(_.nonEmpty)

  /** What to CALL a filter value when explaining a match: the gazetteer label for an id, and the value itself for a
    * free-text name. "place: pleiades_589831" names nothing a reader knows (item 15); "place: Kairatos (river)" does.
    */
  def labelFor(value: PlaceScope.Value): PlaceRepo.Label =
    labels.getOrElse(value, PlaceRepo.Label(value.value))
  def isEmpty: Boolean = byValue.isEmpty
  def nonEmpty: Boolean = !isEmpty

object PlaceScope:
  /** A value of a filter's `places`: a place id (an old one included) or a free-text place name. */
  type Value = Value.T
  object Value extends Newtype[String, Pure]

  type IdsForValue = Value
  val IdsForValue: Value.type = Value
  type LabelForValue = Value
  val LabelForValue: Value.type = Value
  type ByValueKey = Value
  val ByValueKey: Value.type = Value
  type LabelsKey = Value
  val LabelsKey: Value.type = Value
  export Place.Id as ByValueValue
  export PlaceRepo.Label as LabelsValue

  val empty: PlaceScope = PlaceScope(Map.empty)
