package com.histmersion.api

/** One place as it arrives from an import file: the row, its names and its parent edges. */
final case class PlaceInput(place: Place, names: List[PlaceName] = Nil, relations: List[PlaceRelation] = Nil)
