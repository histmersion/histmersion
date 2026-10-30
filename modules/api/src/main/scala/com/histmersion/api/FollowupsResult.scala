package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class FollowupsResult(
    target: String,
    doi: Option[String],
    backend: String,
    count: Int,
    edgesAdded: Int,
    followups: List[FollowupRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FollowupsResult:
  given Schema[FollowupsResult] = summon[KindlingsSchema[FollowupsResult]].schema
