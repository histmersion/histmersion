package com.histmersion
package domain.catalogue.places

import io.github.iltotore.iron.constraint.any.Pure

/** `placeholderMin`: a point shared by this many places is a placeholder (`places.geocode-placeholder-min`);
  * `repairKm`: the smallest move the repair makes (`places.geocode-repair-km`); `toleranceKm`: the point tolerance
  * `finding_places` is re-materialised with after a repair.
  */
final case class GeocodeSettings(placeholderMin: GeocodeSettings.PlaceholderMin, repairKm: GeocodeSettings.RepairKm, toleranceKm: GeocodeSettings.ToleranceKm)

object GeocodeSettings:
  type PlaceholderMin = PlaceholderMin.T
  object PlaceholderMin extends Newtype[Int, Pure]
  type RepairKm = RepairKm.T
  object RepairKm extends Newtype[Double, Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends Newtype[Double, Pure]
