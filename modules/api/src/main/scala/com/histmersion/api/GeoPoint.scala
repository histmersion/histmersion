package com.histmersion
package api

import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude

/** WGS84 lon/lat, matching PostGIS `geometry(Point, 4326)`. */
final case class GeoPoint(lon: Longitude, lat: Latitude)
