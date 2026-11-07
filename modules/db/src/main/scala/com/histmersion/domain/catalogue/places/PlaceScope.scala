package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

/** How the gazetteer resolved a [[Filter]]'s `places` values. Computing it needs the database, so it is done ONCE per
  * query by the handler ([[PlaceRepo.scopeFor]]) and passed into the pure `Filter → SQL` builders; an empty scope means
  * no value named a known place and every one of them falls back to the wave-1 name ILIKE, exactly as before the
  * gazetteer existed.
  */
final case class PlaceScope(byValue: Map[PlaceScope.ByValueKey, List[PlaceScope.ByValueValue]], labels: Map[PlaceScope.LabelsKey, PlaceScope.LabelsValue] = Map.empty):
  /** The place ids one filter value stands for — itself plus its descendants as of the filter's period. `None` when the
    * value is not a place id.
    */
  def idsFor(value: com.histmersion.domain.catalogue.places.PlaceScope.IdsForValue): Option[List[com.histmersion.domain.catalogue.Place.Id]] = byValue.get(value).filter(_.nonEmpty)

  /** What to CALL a filter value when explaining a match: the gazetteer label for an id, and the value itself for a
    * free-text name. "place: pleiades_589831" names nothing a reader knows (item 15); "place: Kairatos (river)" does.
    */
  def labelFor(value: com.histmersion.domain.catalogue.places.PlaceScope.LabelForValue): com.histmersion.domain.catalogue.places.PlaceRepo.Label = labels.getOrElse(value, value)
  def isEmpty: Boolean = byValue.isEmpty
  def nonEmpty: Boolean = !isEmpty

object PlaceScope:
  type IdsForValue = IdsForValue.T
  object IdsForValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelForValue = LabelForValue.T
  object LabelForValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ByValueKey = ByValueKey.T
  object ByValueKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByValueValue = ByValueValue.T
  object ByValueValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsKey = LabelsKey.T
  object LabelsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelsValue = LabelsValue.T
  object LabelsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: PlaceScope = PlaceScope(Map.empty)
