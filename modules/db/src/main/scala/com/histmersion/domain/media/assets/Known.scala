package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.constraint.any.Pure

/** A stored picture of a provider, by its key: its uid and the licence it was stored under. */
final case class Known(uid: Known.Uid, license: Option[Known.License])

object Known:
  export MediaItemRow.Uid
  type License = License.T
  object License extends Newtype[String, Pure]
