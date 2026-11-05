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
final case class PlaceScope(byValue: Map[String, List[String]], labels: Map[String, String] = Map.empty):
  /** The place ids one filter value stands for — itself plus its descendants as of the filter's period. `None` when the
    * value is not a place id.
    */
  def idsFor(value: String): Option[List[String]] = byValue.get(value).filter(_.nonEmpty)

  /** What to CALL a filter value when explaining a match: the gazetteer label for an id, and the value itself for a
    * free-text name. "place: pleiades_589831" names nothing a reader knows (item 15); "place: Kairatos (river)" does.
    */
  def labelFor(value: String): String = labels.getOrElse(value, value)
  def isEmpty: Boolean = byValue.isEmpty
  def nonEmpty: Boolean = !isEmpty

object PlaceScope:
  val empty: PlaceScope = PlaceScope(Map.empty)
