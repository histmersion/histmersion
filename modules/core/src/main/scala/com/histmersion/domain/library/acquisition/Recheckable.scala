package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.acquisition.LastCheck
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Recheckable(item: Recheckable.Item, last: Option[LastCheck])

object Recheckable:
  type Item = Item.T
  object Item extends Newtype[Int, Pure]
