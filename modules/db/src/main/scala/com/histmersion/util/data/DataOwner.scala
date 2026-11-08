package com.histmersion
package util.data

import io.github.iltotore.iron.*

/** Who owns a part of the data: a domain (`library`, `catalogue`, …) or a supporting area (`util.jobs`, …). */
type DataOwner = DataOwner.T
object DataOwner extends Newtype[String, Pure]
