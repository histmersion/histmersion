package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MergeApplyRequest(
    bundle: MergeApplyRequest.Bundle,
    planHash: MergeApplyRequest.PlanHash,
    resolutions: MergeResolutions = MergeResolutions()
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MergeApplyRequest:
  export api.exchange.Bundle.Name as Bundle
  export MergePlan.PlanHash
