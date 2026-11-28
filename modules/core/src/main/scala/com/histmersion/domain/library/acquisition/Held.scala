package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Held(citekey: Citekey, hasOriginal: Held.HasOriginal)

object Held:
  type HasOriginal = HasOriginal.T
  object HasOriginal extends Newtype[Boolean, Pure]
