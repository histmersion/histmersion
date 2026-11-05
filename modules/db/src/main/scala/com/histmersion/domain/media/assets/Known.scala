package com.histmersion.domain.media.assets

/** A stored picture of a provider, by its key: its uid and the licence it was stored under. */
final case class Known(uid: Known.Uid, license: Option[Known.License])

object Known:
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type License = License.T
  object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
