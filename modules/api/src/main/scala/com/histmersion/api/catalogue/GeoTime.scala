package com.histmersion.api.catalogue
import com.histmersion.api.given
enum GeoTime:
  case Always

  case At(year: GeoTime.At.Year)

  case Within(from: GeoTime.Within.From, to: GeoTime.Within.To, latest: GeoTime.Within.Latest)

  def window: Option[(com.histmersion.api.catalogue.GeoTime.Within.From, com.histmersion.api.catalogue.GeoTime.Within.To)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))

object GeoTime:
  object At:
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Within:
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Latest = Latest.T
    object Latest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

