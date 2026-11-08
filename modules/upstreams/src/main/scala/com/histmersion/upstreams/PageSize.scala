package com.histmersion
package upstreams

import io.github.iltotore.iron.constraint.any.Pure

type PageSize = PageSize.T
object PageSize extends Newtype[Int, Pure]
