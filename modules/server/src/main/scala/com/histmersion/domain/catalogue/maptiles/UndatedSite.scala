package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One site of no known date, as the map draws it: its id, point and type. */
final case class UndatedSite(id: UndatedSite.Id, lat: Latitude, lon: Longitude, siteType: Option[UndatedSite.SiteType])

object UndatedSite:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type SiteType = SiteType.T
  object SiteType extends RefinedType[String, Pure]
