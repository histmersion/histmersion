package com.histmersion.values

enum PeriodError:
  case EndsBeforeStart(start: Year, end: Year)

  def message: String = this match
    case EndsBeforeStart(s, e) => s"the period ends (${e.value}) before it starts (${s.value})"
