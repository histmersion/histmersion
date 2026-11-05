package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeApplyRequest(bundle: MergeApplyRequest.Bundle, planHash: MergeApplyRequest.PlanHash, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeApplyRequest:
  type Bundle = Bundle.T
  object Bundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanHash = PlanHash.T
  object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
