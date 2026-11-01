package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A licence as it is stored and shown: an SPDX id (`CC-BY-SA-4.0`, `CC0-1.0`), `public-domain`, or — when it reads as
  * no licence known — the statement itself. Classified into its family by the shared `domain.Licence`.
  */
type LicenceId = LicenceId.T

object LicenceId extends RefinedType[String, Pure]
