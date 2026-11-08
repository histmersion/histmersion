package com.histmersion
package api.catalogue

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

// S2 REWRITE: newtype lines get RefinedType/Pure imports, `_root_.scala.Int` becomes `Int`
type Year = Year.T
object Year extends RefinedType[Int, Pure]

final case class Period(start: Year, end: Year)
