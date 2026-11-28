package com.histmersion
package api

import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@movesTo(FeaturePlace.Kernel)
final case class Validity(from: Option[Year], to: Option[Year], startUnknown: Validity.StartUnknown)

object Validity:
  enum StartUnknown derives CanEqual:
    case StartUnknown, StartKnown
  object StartUnknown extends TwoStates[StartUnknown]:
    extension (startUnknown: StartUnknown) def startUnknown: Boolean = startUnknown == StartUnknown
