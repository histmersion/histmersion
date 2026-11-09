package com.histmersion
package api.library
import com.histmersion.api.Problem
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
enum CalibreEntryCheck derives CanEqual:
  case Present

  case Missing

  case Unchecked(reason: Problem)
