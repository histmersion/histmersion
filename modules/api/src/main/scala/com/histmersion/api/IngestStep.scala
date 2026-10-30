package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IngestStep(step: String, ok: Boolean, detail: String) derives KindlingsCodecAsObject, KindlingsSchema
object IngestStep:
  given Schema[IngestStep] = summon[KindlingsSchema[IngestStep]].schema
