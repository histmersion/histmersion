package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class Period(start: Long, end: Long):
  def isPoint: Boolean = start == end
