package com.histmersion.api.personal

object PersonalIds:

  val MaxLimit = 1000
  val DefaultLimit = 200

  def limit(asked: Option[Int]): Int = asked.filter(_ > 0).fold(DefaultLimit)(math.min(_, MaxLimit))
