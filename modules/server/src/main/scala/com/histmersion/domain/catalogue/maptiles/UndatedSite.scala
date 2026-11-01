package com.histmersion.domain.catalogue.maptiles

/** One site of no known date, as the map draws it: its id, point and type. */
final case class UndatedSite(id: String, lat: Double, lon: Double, siteType: Option[String])
