package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ReviewFinding(
    id: String,
    reviewer: String,
    severity: String,
    kind: String,
    where: String,
    problem: String,
    fix: String,
    quote: String,
    outcome: String,
    note: String = "",
    closedBy: Option[String] = None,
    closedRound: Option[Int] = None,
    closedRunNumber: Option[Int] = None,
    reopened: Int = 0,
    deferred: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ReviewFinding:
  given Schema[ReviewFinding] = summon[KindlingsSchema[ReviewFinding]].schema
