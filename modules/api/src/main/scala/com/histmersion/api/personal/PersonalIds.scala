package com.histmersion.api.personal

/** The shape of a bookmark's and a note's id. */
object PersonalIds:
  type UuidRule = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  /** The most a list answers, and how many when none is asked for. */
  val MaxLimit = 1000
  val DefaultLimit = 200

  def limit(asked: Option[Int]): Int = asked.filter(_ > 0).fold(DefaultLimit)(math.min(_, MaxLimit))
