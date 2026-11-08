package com.histmersion
package web.ui

import io.github.iltotore.iron.constraint.any.Pure

enum AggregateBy(val param: AggregateBy.Param):
  case Time extends AggregateBy(AggregateBy.Param("time"))
  case Region extends AggregateBy(AggregateBy.Param("region"))
  case TimeRegion extends AggregateBy(AggregateBy.Param("time,region"))

object AggregateBy:
  type Param = Param.T
  object Param extends Newtype[String, Pure]
