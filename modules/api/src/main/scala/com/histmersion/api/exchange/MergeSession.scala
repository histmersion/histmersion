package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeSession(
    bundle: String,
    resolutions: MergeResolutions = MergeResolutions(),
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeSession:
  given Schema[MergeSession] = summon[KindlingsSchema[MergeSession]].schema
