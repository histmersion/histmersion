package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CandidateSweepRequest(goal: String, dryRun: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object CandidateSweepRequest:
  given Schema[CandidateSweepRequest] = summon[KindlingsSchema[CandidateSweepRequest]].schema
