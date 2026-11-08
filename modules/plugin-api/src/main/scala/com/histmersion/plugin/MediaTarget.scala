package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** What a collection asks about ([[MediaCollection.asks]]): the places on the map, the events with a Wikidata item, or
  * both.
  */
enum MediaTarget derives CanEqual:
  case Places, Events

object MediaTarget:

  /** One place or event a collection was asked about: its kind and its id on the map. */
  final case class Id(kind: MediaTarget, id: Id.Value)

  object Id:
    type Value = Value.T
    object Value extends Newtype[String, Pure]
