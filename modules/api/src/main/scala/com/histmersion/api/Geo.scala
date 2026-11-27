package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Kernel)
object Geo:
  type Latitude = Latitude.T
  object Latitude extends Newtype[Double, Pure]
  type Longitude = Longitude.T
  object Longitude extends Newtype[Double, Pure]
