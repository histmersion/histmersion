package com.histmersion
package api.library
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum CalibreEntryCheck:
  case Present

  case Missing

  case Unchecked(reason: Unchecked.Reason)

object CalibreEntryCheck:
  object Unchecked:
    type Reason = Reason.T
    object Reason extends RefinedType[String, Pure]
