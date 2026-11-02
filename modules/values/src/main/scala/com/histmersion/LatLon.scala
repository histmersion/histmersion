package com.histmersion
import io.github.iltotore.iron.*

/** A point on WGS84, as PostGIS's `geometry(Point, 4326)` holds it. */
final case class LatLon(lat: Latitude, lon: Longitude)
