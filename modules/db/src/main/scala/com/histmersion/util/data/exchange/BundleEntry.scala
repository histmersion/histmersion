package com.histmersion.util.data.exchange

import io.github.iltotore.iron.*

/** A file of a bundle, by its path in the archive (`entities/sources.ndjson`). */
type BundleEntry = BundleEntry.T
object BundleEntry extends RefinedType[String, Pure]
