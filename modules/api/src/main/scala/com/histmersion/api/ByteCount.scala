package com.histmersion
package api

import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}
@movesTo(FeaturePlace.Kernel)
type ByteCount = ByteCount.T
object ByteCount extends Newtype[Long, io.github.iltotore.iron.constraint.any.Pure]
