package com.histmersion.util.data.mirror

import io.github.iltotore.iron.*

/** The kind of record a mirror contribution writes notes of (`sources`, `articles`, …). */
type MirrorRecords = MirrorRecords.T
object MirrorRecords extends RefinedType[String, Pure]
