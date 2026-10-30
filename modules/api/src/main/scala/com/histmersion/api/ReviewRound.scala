package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The findings first raised in one pass (`round`) of one review run; `runNumber` counts the runs of this article from
  * 1, `startedAt` is when the run began (UTC, when the run is still recorded).
  */
final case class ReviewRound(
    run: String,
    runNumber: Int,
    round: Int,
    startedAt: Option[String],
    findings: List[ReviewFinding]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ReviewRound:
  given Schema[ReviewRound] = summon[KindlingsSchema[ReviewRound]].schema
