package com.histmersion.plugin

/** What a collection asks about ([[MediaCollection.asks]]): the places on the map, the events with a Wikidata item, or
  * both.
  */
enum MediaTarget:
  case Places, Events

object MediaTarget:

  /** One place or event a collection was asked about: its kind and its id on the map. */
  final case class Id(kind: MediaTarget, id: String)
