package com.histmersion.api.catalogue
import com.histmersion.api.given
enum GeoTime:
  case Always

  case At(year: Year)

  case Within(from: Year, to: Year, latest: GeoTime.Within.Latest)

  def window: Option[(Year, Year)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))

object GeoTime:

  object Within:
    type Latest = Latest.T
    object Latest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

