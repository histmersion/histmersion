package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.acquirerecheck.LastCheck
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Candidate(item: Candidate.Item, last: Option[LastCheck])

object Candidate:
  type Item = Item.T
  object Item extends Newtype[Int, Pure]
