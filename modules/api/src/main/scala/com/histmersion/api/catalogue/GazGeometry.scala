package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
enum GazGeometry:
  case GeoJson(text: GeoJson.Text)
  case Wkt(text: Wkt.Text)

object GazGeometry:
  @tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
  object GeoJson:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  object Wkt:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
