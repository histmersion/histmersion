package com.histmersion.api.library
import com.histmersion.api.given
enum CalibreEntryCheck:
  case Present

  case Missing

  case Unchecked(reason: CalibreEntryCheck.Unchecked.Reason)

object CalibreEntryCheck:
  object Unchecked:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
