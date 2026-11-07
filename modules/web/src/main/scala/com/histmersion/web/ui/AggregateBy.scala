package com.histmersion.web.ui

enum AggregateBy(val param: AggregateBy.Param):
  case Time extends AggregateBy(com.histmersion.web.ui.AggregateBy.Param("time"))
  case Region extends AggregateBy(com.histmersion.web.ui.AggregateBy.Param("region"))
  case TimeRegion extends AggregateBy(com.histmersion.web.ui.AggregateBy.Param("time,region"))

object AggregateBy:
  type Param = Param.T
  object Param extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
