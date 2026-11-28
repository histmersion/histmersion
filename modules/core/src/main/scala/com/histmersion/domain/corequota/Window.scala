package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Window(limit: Option[Window.Limit], remaining: Option[Window.Remaining], at: Instant)

object Window:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Remaining = Remaining.T
  object Remaining extends Newtype[Int, Pure]
