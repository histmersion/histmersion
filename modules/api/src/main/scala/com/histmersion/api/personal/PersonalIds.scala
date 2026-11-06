package com.histmersion.api.personal

object PersonalIds:

  type LimitResult = com.histmersion.api.Paging.Limit
  val LimitResult: _root_.com.histmersion.api.Paging.Limit.type = _root_.com.histmersion.api.Paging.Limit
  type LimitAsked = com.histmersion.api.Paging.Limit
  val LimitAsked: _root_.com.histmersion.api.Paging.Limit.type = _root_.com.histmersion.api.Paging.Limit

  val MaxLimit = 1000
  val DefaultLimit = 200

  def limit(asked: Option[com.histmersion.api.Paging.Limit]): com.histmersion.api.Paging.Limit = _root_.com.histmersion.api.Paging.Limit(asked.filter((_).value > 0).fold(DefaultLimit)((refactorValue => math.min(refactorValue.value, MaxLimit))))
