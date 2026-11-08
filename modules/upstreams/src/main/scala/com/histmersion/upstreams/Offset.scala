package com.histmersion
package upstreams

import io.github.iltotore.iron.constraint.any.Pure

type Offset = Offset.T
object Offset extends Newtype[Int, Pure]
