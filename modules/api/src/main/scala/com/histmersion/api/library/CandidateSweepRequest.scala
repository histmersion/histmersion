package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CandidateSweepRequest(goal: String, dryRun: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CandidateSweepRequest:
  given Schema[CandidateSweepRequest] = summon[KindlingsSchema[CandidateSweepRequest]].schema
