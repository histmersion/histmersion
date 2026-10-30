package com.histmersion.api

/** Deep time as signed integer years (BCE negative). A point in time is `start == end`; a range is `start < end`.
  * Stored in Postgres as `int8range`.
  */
final case class Period(start: Long, end: Long):
  def isPoint: Boolean = start == end
