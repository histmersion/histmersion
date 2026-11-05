package com.histmersion.api

/** WGS84 lon/lat, matching PostGIS `geometry(Point, 4326)`. */
final case class GeoPoint(lon: Double, lat: Double)
