package com.histmersion
package api

import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Kernel)
final case class Validity(from: Option[Year], to: Option[Year], startUnknown: Validity.StartUnknown)

object Validity:
  type StartUnknown = StartUnknown.T
  object StartUnknown extends Newtype[Boolean, Pure]
