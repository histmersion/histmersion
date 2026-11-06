package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeApplyRequest(bundle: MergeApplyRequest.Bundle, planHash: MergeApplyRequest.PlanHash, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

