package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeApplyRequest(bundle: String, planHash: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
