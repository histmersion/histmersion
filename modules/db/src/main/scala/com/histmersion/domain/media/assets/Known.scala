package com.histmersion
package domain.media.assets

import io.github.iltotore.iron.constraint.any.Pure

/** A stored picture of a provider, by its key: its uid and the licence it was stored under. */
final case class Known(uid: Known.Uid, license: Option[Known.License])

object Known:
  type Uid = Uid.T
  object Uid extends Newtype[String, Pure]
  export AssetDetailRow.License
