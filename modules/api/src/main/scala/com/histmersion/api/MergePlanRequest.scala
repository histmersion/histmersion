package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MergePlanRequest(bundle: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergePlanRequest:
  given Schema[MergePlanRequest] = summon[KindlingsSchema[MergePlanRequest]].schema
