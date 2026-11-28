package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.acquirerecheck.LastCheck
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Recheckable(item: Recheckable.Item, last: Option[LastCheck])

object Recheckable:
  type Item = Item.T
  object Item extends Newtype[Int, Pure]
