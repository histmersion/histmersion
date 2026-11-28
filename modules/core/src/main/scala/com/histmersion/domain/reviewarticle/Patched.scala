package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Patched(
    written: Patched.Written,
    previousVersion: Option[Patched.PreviousVersion],
    conflict: Patched.Conflict
) derives JsonCodec

object Patched:
  type Written = Written.T
  object Written extends Newtype[Boolean, Pure]
  type PreviousVersion = PreviousVersion.T
  object PreviousVersion extends Newtype[Long, Pure]
  type Conflict = Conflict.T
  object Conflict extends Newtype[Boolean, Pure]
