package com.histmersion.web.ui

enum AggregateBy(val param: String):
  case Time extends AggregateBy("time")
  case Region extends AggregateBy("region")
  case TimeRegion extends AggregateBy("time,region")
