package com.histmersion.api

/** A place a record's identifiers already name: its id, who brought it in (`None` = the bundled seed, or a person's own
  * place), and whether the identifier is the place's OWN — a seed id, or one the record that created the place gave it
  * — rather than one a record that JOINED it copied there.
  */
final case class PlaceMatch(placeId: String, importSource: Option[String], native: Boolean = true)
