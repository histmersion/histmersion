package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MergeApplyRequest(bundle: MergeApplyRequest.Bundle, planHash: MergeApplyRequest.PlanHash, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeApplyRequest:
  type Bundle = com.histmersion.api.exchange.Bundle.Name
  val Bundle: _root_.com.histmersion.api.exchange.Bundle.Name.type = _root_.com.histmersion.api.exchange.Bundle.Name
  type PlanHash = com.histmersion.api.exchange.MergePlan.PlanHash
  val PlanHash: _root_.com.histmersion.api.exchange.MergePlan.PlanHash.type = _root_.com.histmersion.api.exchange.MergePlan.PlanHash
