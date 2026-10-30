package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class GoalList(
    total: Int,
    goals: List[Goal],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def page: OffsetPage = OffsetPage(total, offset, goals.size)

object GoalList:
  given Schema[GoalList] = summon[KindlingsSchema[GoalList]].schema
  def of(total: Int, rows: List[Goal], offset: Int): GoalList =
    GoalList(total, rows, offset, OffsetPage(total, offset, rows.size).next)
