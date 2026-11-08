package com.histmersion
package api.catalogue

import io.github.iltotore.iron.constraint.any.Pure

object EventCorrection:
  type Status = Status.T
  object Status extends Newtype[String, Pure]
