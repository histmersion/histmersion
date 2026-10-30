package com.histmersion.api

/** The period a record's place is STORED with, which every time filter reads (`places.period`), and whether its start
  * is a window rather than a date (`places.start_unknown`) — see [[PlaceDating]].
  */
final case class Validity(from: Option[Long], to: Option[Long], startUnknown: Boolean)
