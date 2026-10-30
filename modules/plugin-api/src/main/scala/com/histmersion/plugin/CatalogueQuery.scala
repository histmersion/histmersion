package com.histmersion.plugin

/** What one import asks for.
  *
  * @param from
  *   the earliest year asked for, when the import is bounded
  * @param to
  *   the latest year asked for
  * @param area
  *   `(minLon, minLat, maxLon, maxLat)`, when the import is limited to an area — a filter the host applies anyway; a
  *   catalogue whose API can narrow by area may use it
  */
final case class CatalogueQuery(
    from: Option[Year],
    to: Year,
    area: Option[(Longitude, Latitude, Longitude, Latitude)] = None
)
