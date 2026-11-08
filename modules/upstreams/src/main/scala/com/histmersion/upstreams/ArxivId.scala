package com.histmersion
package upstreams

import io.github.iltotore.iron.constraint.any.Pure

type ArxivId = ArxivId.T
object ArxivId extends Newtype[String, Pure]
