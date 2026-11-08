package com.histmersion
package upstreams

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type ArchiveId = ArchiveId.T
object ArchiveId extends RefinedType[String, Pure]
