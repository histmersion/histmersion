package com.histmersion
package domain.catalogue.events

import io.github.iltotore.iron.constraint.any.Pure

/** How coarsely an event's outline is simplified for the map, in degrees. */
type GeoTolerance = GeoTolerance.T

object GeoTolerance extends Newtype[Double, Pure]
