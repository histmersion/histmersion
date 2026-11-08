package com.histmersion
package upstreams

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type PageSize = PageSize.T
object PageSize extends RefinedType[Int, Pure]
