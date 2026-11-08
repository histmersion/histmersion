package com.histmersion
package api

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Offset = Offset.T
object Offset extends RefinedType[Int, Pure]
