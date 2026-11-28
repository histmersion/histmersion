package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Amended(
    written: Amended.Written,
    previousVersion: Option[Amended.PreviousVersion],
    conflict: Amended.Conflict
) derives JsonCodec

object Amended:
  type Written = Written.T
  object Written extends Newtype[Boolean, Pure]
  type PreviousVersion = PreviousVersion.T
  object PreviousVersion extends Newtype[Long, Pure]
  type Conflict = Conflict.T
  object Conflict extends Newtype[Boolean, Pure]
