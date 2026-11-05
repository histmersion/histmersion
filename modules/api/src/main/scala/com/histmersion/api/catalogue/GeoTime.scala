package com.histmersion.api.catalogue
import com.histmersion.api.given
enum GeoTime:
  case Always

  case At(year: Long)

  case Within(from: Long, to: Long, latest: Boolean)

  def window: Option[(com.histmersion.api.catalogue.GeoTime.WindowResultPart1, com.histmersion.api.catalogue.GeoTime.WindowResultPart2)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))

object GeoTime:
  type WindowResultPart1 = WindowResultPart1.T
  object WindowResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowResultPart2 = WindowResultPart2.T
  object WindowResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
