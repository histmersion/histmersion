package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A stored picture of a provider, by its key: its uid and the licence it was stored under. */
final case class Known(uid: Known.Uid, license: Option[Known.License])

object Known:
  type Uid = Uid.T
  object Uid extends RefinedType[String, Pure]
  type License = License.T
  object License extends RefinedType[String, Pure]
