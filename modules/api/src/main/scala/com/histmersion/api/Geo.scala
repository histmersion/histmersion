package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Kernel)
object Geo:
  type Latitude = Latitude.T
  object Latitude extends Newtype[Double, Pure]
  type Longitude = Longitude.T
  object Longitude extends Newtype[Double, Pure]
