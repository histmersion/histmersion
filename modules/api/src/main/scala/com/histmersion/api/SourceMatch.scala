package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.values.{IssueKind, MatchMethod, MatchOutcome}

/** One source of the bundle and what it was paired with. Pairs `Same` by uid are counted, not listed. */
final case class SourceMatch(
    rightUid: UUID,
    rightCitekey: String,
    leftUid: Option[UUID],
    leftCitekey: Option[String],
    method: MatchMethod,
    outcome: MatchOutcome,
    score: Option[Double] = None,
    why: List[String] = Nil,
    /** The [[IssueKind.SameWork]] issue that decides it, when one does. */
    issue: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceMatch:
  given Schema[SourceMatch] = summon[KindlingsSchema[SourceMatch]].schema
