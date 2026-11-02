package com.histmersion.values

import com.histmersion.Year
enum PeriodError:
  case EndsBeforeStart(start: Year, end: Year)

  def message: String = this match
    case EndsBeforeStart(s, e) => s"the period ends (${e.value}) before it starts (${s.value})"
