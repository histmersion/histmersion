package com.histmersion.domain.catalogue.places

/** `placeholderMin`: a point shared by this many places is a placeholder (`places.geocode-placeholder-min`);
  * `repairKm`: the smallest move the repair makes (`places.geocode-repair-km`); `toleranceKm`: the point tolerance
  * `finding_places` is re-materialised with after a repair.
  */
final case class GeocodeSettings(placeholderMin: GeocodeSettings.PlaceholderMin, repairKm: GeocodeSettings.RepairKm, toleranceKm: GeocodeSettings.ToleranceKm)

object GeocodeSettings:
  type PlaceholderMin = PlaceholderMin.T
  object PlaceholderMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairKm = RepairKm.T
  object RepairKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToleranceKm = ToleranceKm.T
  object ToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
