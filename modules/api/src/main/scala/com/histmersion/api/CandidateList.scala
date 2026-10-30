package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A page of candidates, and how many there are in each state (of the goal, or of every goal). */
final case class CandidateList(
    goal: Option[String],
    state: String,
    candidates: List[SourceCandidate],
    counts: List[CandidateCount],
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CandidateList:
  given Schema[CandidateList] = summon[KindlingsSchema[CandidateList]].schema
