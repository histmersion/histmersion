package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A picture's stable identity across databases (`assets.uid`). */
type AssetUid = AssetUid.T

object AssetUid extends RefinedType[java.util.UUID, Pure]
