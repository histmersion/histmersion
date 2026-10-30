package com.histmersion.api

/** A time span with either side possibly unknown — `from = None` is "since whenever", which is not the same as a known
  * single year. Signed years, historical reckoning (BCE negative, no year 0).
  */
final case class Span(from: Option[Long], to: Option[Long]):
  def isEmpty: Boolean = from.isEmpty && to.isEmpty

object Span:
  val always: Span = Span(None, None)
