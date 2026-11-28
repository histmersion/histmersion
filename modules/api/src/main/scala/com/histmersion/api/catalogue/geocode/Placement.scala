package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Citekey
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

final case class Placement(findingId: Finding.Id, citekey: Citekey, region: Placement.Region, to: Resolution):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: String =
    String.format(
      java.util.Locale.ROOT,
      "%s: '%s' → %s at %.2f N %.2f E%s",
      findingId.value,
      region.value,
      to.label.value + " [" + to.placeId.value + "]",
      to.lat.value,
      to.lon.value,
      if (to.isApproximate) == (true) then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
    )

object Placement:

  type Region = Region.T
  object Region extends Newtype[String, Pure]
