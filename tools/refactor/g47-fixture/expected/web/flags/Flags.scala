package com.histmersion
package web.flags

import io.github.iltotore.iron.RefinedType

object Pure:
  // S20 UNCHANGED import: this file defines its own Pure; `_root_` stays too, `io` alone is scala.io
  type Flag = Flag.T
  object Flag extends RefinedType[Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  // S21 UNCHANGED: already short; plain fully-qualified Java names are left alone
  def now: java.time.Instant = java.time.Instant.EPOCH
