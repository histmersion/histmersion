package com.histmersion
package api.personal

import com.histmersion.api.Paging
import com.histmersion.lint.{LintRule, tolerated}

object PersonalIds:

  export Paging.{Limit as LimitAsked, Limit as LimitResult}

  val MaxLimit = 1000
  val DefaultLimit = 200

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def limit(asked: Option[Paging.Limit]): Paging.Limit = Paging.Limit(
    asked.filter((_).value > 0).fold(DefaultLimit)((refactorValue => math.min(refactorValue.value, MaxLimit)))
  )
