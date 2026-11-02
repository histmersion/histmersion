package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FollowupsRequest(
    id: String,
    since: Option[Int] = None,
    limit: Option[Int] = None,
    apply: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FollowupsRequest:
  given Schema[FollowupsRequest] = summon[KindlingsSchema[FollowupsRequest]].schema
