package com.histmersion
package api.library

import com.histmersion.util.problem.Problem

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
enum CalibreEntryCheck derives CanEqual:
  case Present

  case Missing

  case Unchecked(reason: Problem)
