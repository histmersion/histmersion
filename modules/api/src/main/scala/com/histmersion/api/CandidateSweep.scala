package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What one sweep of a goal did: its watches as read, the works found, the candidates added (new) and those found
  * again, what was left out and why, and the goal's unseen candidates after it.
  */
/** One sweep's result. `notes`: what the sweep left out and why — an index it did not ask (CORE's budget spent, CORE
  * asked to wait).
  */
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
