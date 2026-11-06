package com.histmersion.api.personal

object PersonalIds:


  val MaxLimit = 1000
  val DefaultLimit = 200

  def limit(asked: Option[com.histmersion.api.Paging.Limit]): com.histmersion.api.Paging.Limit = _root_.com.histmersion.api.Paging.Limit(asked.filter((_).value > 0).fold(DefaultLimit)((refactorValue => math.min(refactorValue.value, MaxLimit))))
