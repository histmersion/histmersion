package com.histmersion.api.catalogue
import com.histmersion.api.given
enum GeoTime:
  case Always

  case At(year: Long)

  case Within(from: Long, to: Long, latest: Boolean)

  def window: Option[(Long, Long)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))
