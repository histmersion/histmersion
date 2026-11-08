package com.histmersion
package api

import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class Validity(from: Option[Year], to: Option[Year], startUnknown: Validity.StartUnknown)

object Validity:
  type StartUnknown = StartUnknown.T
  object StartUnknown extends RefinedType[Boolean, Pure]
