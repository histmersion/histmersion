package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class PlaceInput(place: Place, names: List[PlaceName] = Nil, relations: List[PlaceRelation] = Nil)
