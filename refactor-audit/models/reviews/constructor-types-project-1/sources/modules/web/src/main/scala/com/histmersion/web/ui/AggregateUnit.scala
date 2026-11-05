package com.histmersion.web.ui

enum AggregateUnit(val param: Option[AggregateUnit.Param]):
  case Sources extends AggregateUnit(None)
  case Findings extends AggregateUnit(Some("findings"))
  case Starts extends AggregateUnit(Some("starts"))

object AggregateUnit:
  type Param = Param.T
  object Param extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
