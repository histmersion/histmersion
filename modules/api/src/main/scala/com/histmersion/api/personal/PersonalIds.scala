package com.histmersion
package api.personal

import com.histmersion.api.Paging

object PersonalIds:

  export com.histmersion.api.Paging.{Limit as LimitResult, Limit as LimitAsked}

  val MaxLimit = 1000
  val DefaultLimit = 200

  def limit(asked: Option[Paging.Limit]): Paging.Limit = Paging.Limit(asked.filter((_).value > 0).fold(DefaultLimit)((refactorValue => math.min(refactorValue.value, MaxLimit))))
