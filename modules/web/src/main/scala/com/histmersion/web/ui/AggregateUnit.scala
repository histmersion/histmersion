package com.histmersion
package web.ui

import io.github.iltotore.iron.constraint.any.Pure

enum AggregateUnit(val param: Option[AggregateUnit.Param]):
  case Sources extends AggregateUnit(None)
  case Findings extends AggregateUnit(Some(AggregateUnit.Param("findings")))
  case Starts extends AggregateUnit(Some(AggregateUnit.Param("starts")))

object AggregateUnit:
  type Param = Param.T
  object Param extends Newtype[String, Pure]
