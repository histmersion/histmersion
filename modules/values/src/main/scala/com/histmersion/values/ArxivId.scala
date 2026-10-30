package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** An arXiv identifier as given (`2101.00001`, `hep-th/9901001`), without the `arXiv:` prefix. */
type ArxivId = ArxivId.T

object ArxivId extends RefinedType[String, Pure]
