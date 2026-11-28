package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Citekey
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.Geocode.distanceKm
import com.histmersion.api.catalogue.Geocode.DistanceKm
import io.github.iltotore.iron.constraint.any.Pure

final case class Move(
    findingId: Finding.Id,
    citekey: Citekey,
    asked: Move.Name,
    region: Option[Move.Name],
    fromLon: Move.FromLon,
    fromLat: Move.FromLat,
    to: Resolution
):
  def km: DistanceKm = distanceKm(fromLon, fromLat, to.lon, to.lat)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: String =
    val from = region.filter(_ != asked).fold("")(r => s" (stored as '${r.value}')")
    String.format(
      java.util.Locale.ROOT,
      "%s: '%s'%s at %.2f N %.2f E → %s at %.2f N %.2f E, %.0f km%s",
      findingId.value,
      asked.value,
      from,
      fromLat.value,
      fromLon.value,
      to.label.value + " [" + to.placeId.value + "]",
      to.lat.value,
      to.lon.value,
      km.value,
      if (to.isApproximate) == (true) then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
    )

object Move:
  type MinKm = MinKm.T
  object MinKm extends Newtype[Double, Pure]

  type Name = Name.T
  object Name extends Newtype[String, Pure]
  export com.histmersion.api.catalogue.{Latitude as FromLat, Longitude as FromLon}

  extension (m: Move)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def worthMoving(minKm: Move.MinKm): Boolean = (m.km).value > minKm.value
