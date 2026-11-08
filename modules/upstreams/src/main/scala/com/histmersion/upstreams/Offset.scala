package com.histmersion
package upstreams

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type Offset = Offset.T
object Offset extends RefinedType[Int, Pure]
