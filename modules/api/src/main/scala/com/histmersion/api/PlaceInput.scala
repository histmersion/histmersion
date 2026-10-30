package com.histmersion.api

final case class PlaceInput(place: Place, names: List[PlaceName] = Nil, relations: List[PlaceRelation] = Nil)
