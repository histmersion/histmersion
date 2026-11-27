package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum GeoTime derives CanEqual:
  case Always

  case At(year: Year)

  case Within(from: Year, to: Year, latest: Within.Latest)

  def window: Option[(Year, Year)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))

object GeoTime:

  object Within:
    type Latest = Latest.T
    object Latest extends Newtype[Boolean, Pure]
