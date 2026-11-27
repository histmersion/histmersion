package com.histmersion
package config

import io.github.iltotore.iron.constraint.numeric.Positive
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.Values)
type RefreshIntervalMinutes = RefreshIntervalMinutes.T
object RefreshIntervalMinutes extends Newtype[Int, Positive]
