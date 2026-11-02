package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class PlaceMatch(placeId: String, importSource: Option[String], native: Boolean = true)
