package com.histmersion.api

final case class PlaceMatch(placeId: String, importSource: Option[String], native: Boolean = true)
