package com.histmersion
package domain.catalogue.places

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.config.PlacesConfig

/** `placeholderMin`: a point shared by this many places is a placeholder (`places.geocode-placeholder-min`);
  * `repairKm`: the smallest move the repair makes (`places.geocode-repair-km`); `toleranceKm`: the point tolerance
  * `finding_places` is re-materialised with after a repair.
  */
final case class GeocodeSettings(
    placeholderMin: GeocodeSettings.PlaceholderMin,
    repairKm: GeocodeSettings.RepairKm,
    toleranceKm: GeocodeSettings.ToleranceKm
)

object GeocodeSettings:
  export PlacesConfig.GeocodePlaceholderMin as PlaceholderMin
  export PlacesConfig.GeocodeRepairKm as RepairKm
  export PlacesConfig.PointToleranceKm as ToleranceKm
