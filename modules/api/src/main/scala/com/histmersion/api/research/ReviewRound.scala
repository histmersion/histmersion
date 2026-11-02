package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
