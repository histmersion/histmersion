package com.histmersion
package api

import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
@movesTo(FeaturePlace.Kernel)
type ByteCount = ByteCount.T
object ByteCount extends Newtype[Long, io.github.iltotore.iron.constraint.any.Pure]
