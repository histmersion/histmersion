package com.histmersion
package domain.personal.marks

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One page of the records not marked read, and how many there are in all. */
final case class UnreadItems(items: List[UnreadItem], total: UnreadItems.Total)

object UnreadItems:
  type Total = Total.T
  object Total extends RefinedType[Int, Pure]
