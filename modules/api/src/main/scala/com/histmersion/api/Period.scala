package com.histmersion.api

final case class Period(start: Long, end: Long):
  def isPoint: Boolean = start == end
