package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CandidateSweep(
    goal: String,
    watches: List[CandidateWatch],
    found: Int,
    added: Int,
    again: Int,
    skipped: Map[String, Int],
    unseen: Int,
    dryRun: Boolean = false,
    notes: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CandidateSweep:
  given Schema[CandidateSweep] = summon[KindlingsSchema[CandidateSweep]].schema
