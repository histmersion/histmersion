package com.histmersion.values

/** Where a candidate link between an event and a source stands. */
object EventLinkStatus:
  val Proposed = "proposed"
  val Confirmed = "confirmed"
  val Rejected = "rejected"
  val all: List[String] = List(Proposed, Confirmed, Rejected)
