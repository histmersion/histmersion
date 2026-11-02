package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
