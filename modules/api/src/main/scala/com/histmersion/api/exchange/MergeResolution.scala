package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeResolution(
    issue: String,
    decision: String,
    text: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeResolution:
  given Schema[MergeResolution] = summon[KindlingsSchema[MergeResolution]].schema
