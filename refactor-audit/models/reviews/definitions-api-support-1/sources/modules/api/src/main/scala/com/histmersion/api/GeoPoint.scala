package com.histmersion.api

/** WGS84 lon/lat, matching PostGIS `geometry(Point, 4326)`. */
final case class GeoPoint(lon: com.histmersion.api.catalogue.Longitude, lat: com.histmersion.api.catalogue.Latitude)
