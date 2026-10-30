package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A goal's watch (#3761): what it reads, from which index, the day it reads from, its last read. */
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
