package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.values.{IssueKind, MatchMethod, MatchOutcome}

final case class SourceMatch(
    rightUid: UUID,
    rightCitekey: String,
    leftUid: Option[UUID],
    leftCitekey: Option[String],
    method: MatchMethod,
    outcome: MatchOutcome,
    score: Option[Double] = None,
    why: List[String] = Nil,
    issue: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceMatch:
  given Schema[SourceMatch] = summon[KindlingsSchema[SourceMatch]].schema
