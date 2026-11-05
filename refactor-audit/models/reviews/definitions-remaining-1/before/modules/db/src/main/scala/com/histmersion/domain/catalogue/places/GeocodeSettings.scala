package com.histmersion.domain.catalogue.places

/** `placeholderMin`: a point shared by this many places is a placeholder (`places.geocode-placeholder-min`);
  * `repairKm`: the smallest move the repair makes (`places.geocode-repair-km`); `toleranceKm`: the point tolerance
  * `finding_places` is re-materialised with after a repair.
  */
final case class GeocodeSettings(placeholderMin: Int, repairKm: Double, toleranceKm: Double)
