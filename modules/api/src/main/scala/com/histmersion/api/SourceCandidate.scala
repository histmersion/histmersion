package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A work found for a goal, deduplicated (#3761): `doi` is its key; `foundBy` every watch that found it. */
final case class SourceCandidate(
    id: Long,
    goal: String,
    doi: String,
    title: String,
    year: Option[Int] = None,
    authors: List[String] = Nil,
    venue: Option[String] = None,
    abstractText: Option[String] = None,
    isOa: Boolean = false,
    arxivId: Option[String] = None,
    foundBy: List[String] = Nil,
    state: String = Candidates.State.New.wire,
    firstSeen: String = "",
    lastSeen: String = ""
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceCandidate:
  given Schema[SourceCandidate] = summon[KindlingsSchema[SourceCandidate]].schema
