package com.histmersion.domain.personal.marks

/** One page of the records not marked read, and how many there are in all. */
final case class UnreadItems(items: List[UnreadItem], total: Int)
