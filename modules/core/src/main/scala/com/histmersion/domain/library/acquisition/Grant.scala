package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.acquisition.State
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Grant(state: State, waitMs: Grant.WaitMs)

object Grant:
  type WaitMs = WaitMs.T
  object WaitMs extends Newtype[Long, Pure]
