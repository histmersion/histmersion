package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CandidateWatch(
    goal: String,
    kind: String,
    subject: String,
    index: String,
    watermark: Option[String] = None,
    lastRun: Option[String] = None,
    lastError: Option[String] = None,
    lastFound: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CandidateWatch:
  given Schema[CandidateWatch] = summon[KindlingsSchema[CandidateWatch]].schema
