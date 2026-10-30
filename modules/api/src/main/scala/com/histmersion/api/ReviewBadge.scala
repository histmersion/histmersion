package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ReviewBadge(
    state: ReviewState,
    label: String,
    detail: String,
    openMajors: Int = 0,
    openMinors: Int = 0,
    findings: Int = 0,
    runs: Int = 0,
    round: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ReviewBadge:
  given Schema[ReviewBadge] = summon[KindlingsSchema[ReviewBadge]].schema
