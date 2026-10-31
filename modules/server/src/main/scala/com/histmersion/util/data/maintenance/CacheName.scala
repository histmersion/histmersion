package com.histmersion.util.data.maintenance

import io.github.iltotore.iron.*

/** What a cache is called: unique among the registered ones. */
type CacheName = CacheName.T
object CacheName extends RefinedType[String, Pure]
