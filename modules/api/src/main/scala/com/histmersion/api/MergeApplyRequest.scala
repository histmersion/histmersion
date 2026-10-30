package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MergeApplyRequest(bundle: String, planHash: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeApplyRequest:
  given Schema[MergeApplyRequest] = summon[KindlingsSchema[MergeApplyRequest]].schema
