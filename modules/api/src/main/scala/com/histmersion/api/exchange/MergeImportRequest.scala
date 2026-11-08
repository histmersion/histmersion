package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MergeImportRequest(
    bundle: MergeImportRequest.Bundle,
    resolutions: MergeResolutions = MergeResolutions(),
    dryRun: MergeImportRequest.DryRun = _root_.com.histmersion.api.exchange.MergeImportRequest.DryRun(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeImportRequest:
  export com.histmersion.api.exchange.Bundle.{Name as Bundle}
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
