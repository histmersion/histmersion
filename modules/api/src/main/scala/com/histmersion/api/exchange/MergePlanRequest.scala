package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergePlanRequest(bundle: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergePlanRequest:
  given Schema[MergePlanRequest] = summon[KindlingsSchema[MergePlanRequest]].schema
