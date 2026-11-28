package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Longitude
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Geocode.Placeholder

final case class Resolution(
    asked: Resolution.Asked,
    placeId: Place.Id,
    label: Place.Label,
    lon: Longitude,
    lat: Latitude,
    approximate: List[Approximation] = Nil
):
  def isApproximate: Boolean = (approximate.nonEmpty)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: String =
    val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
    (s"'$asked' → $label [$placeId]$approx")

object Resolution:

  type Asked = Asked.T
  object Asked extends Newtype[String, Pure]

  def choose(
      asked: Asked,
      attempt: Attempt,
      ranked: List[Candidate],
      placeholderMin: Placeholder.Min
  ): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(((c.isPlaceholder(placeholderMin)) == (true)))(Approximation.Rough(c.shared))
      val grid = Option.when(((c.gridCell) == Candidate.GridCell.GridCell) && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }
