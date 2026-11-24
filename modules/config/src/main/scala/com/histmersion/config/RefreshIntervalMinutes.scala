package com.histmersion
package config

import io.github.iltotore.iron.constraint.numeric.Positive
import com.histmersion.lint.{FeaturePlace, movesTo}

@movesTo(FeaturePlace.Values)
type RefreshIntervalMinutes = RefreshIntervalMinutes.T
object RefreshIntervalMinutes extends Newtype[Int, Positive]
