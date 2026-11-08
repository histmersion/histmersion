package com.histmersion.api.catalogue

// S2 REWRITE: newtype lines get RefinedType/Pure imports, `_root_.scala.Int` becomes `Int`
type Year = Year.T
object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

final case class Period(start: com.histmersion.api.catalogue.Year, end: _root_.com.histmersion.api.catalogue.Year)
