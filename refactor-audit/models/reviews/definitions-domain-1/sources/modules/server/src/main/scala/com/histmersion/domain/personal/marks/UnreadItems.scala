package com.histmersion.domain.personal.marks

/** One page of the records not marked read, and how many there are in all. */
final case class UnreadItems(items: List[UnreadItem], total: UnreadItems.Total)

object UnreadItems:
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
