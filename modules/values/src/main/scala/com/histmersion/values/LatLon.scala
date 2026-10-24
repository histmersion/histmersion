package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval

/** Degrees north (negative: south) on WGS84. */
type Latitude = Latitude.T

object Latitude extends RefinedType[Double, Interval.Closed[-90.0, 90.0]]

/** Degrees east (negative: west) on WGS84. */
type Longitude = Longitude.T

object Longitude extends RefinedType[Double, Interval.Closed[-180.0, 180.0]]

/** A point on WGS84, as PostGIS's `geometry(Point, 4326)` holds it. */
final case class LatLon(lat: Latitude, lon: Longitude)
