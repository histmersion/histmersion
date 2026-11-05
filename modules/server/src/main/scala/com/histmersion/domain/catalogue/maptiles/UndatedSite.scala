package com.histmersion.domain.catalogue.maptiles

/** One site of no known date, as the map draws it: its id, point and type. */
final case class UndatedSite(id: UndatedSite.Id, lat: com.histmersion.Latitude, lon: com.histmersion.Longitude, siteType: Option[UndatedSite.SiteType])

object UndatedSite:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteType = SiteType.T
  object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
