package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Amended(
    written: Amended.Written,
    previousVersion: Option[Amended.PreviousVersion],
    conflict: Amended.Conflict
)

object Amended:
  type Written = Written.T
  object Written extends Newtype[Boolean, Pure]
  type PreviousVersion = PreviousVersion.T
  object PreviousVersion extends Newtype[Long, Pure]
  type Conflict = Conflict.T
  object Conflict extends Newtype[Boolean, Pure]
