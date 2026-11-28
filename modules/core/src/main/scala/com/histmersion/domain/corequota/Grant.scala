package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.corequota.State
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Grant(state: State, waitMs: Grant.WaitMs)

object Grant:
  type WaitMs = WaitMs.T
  object WaitMs extends Newtype[Long, Pure]
