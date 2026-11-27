package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
enum LinkScheme:
  case Pleiades, Wikidata, Geonames, Tgn, Whg

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: LinkScheme.Wire = LinkScheme.Wire(toString.toLowerCase(java.util.Locale.ROOT))

object LinkScheme:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[LinkScheme] =
    values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
