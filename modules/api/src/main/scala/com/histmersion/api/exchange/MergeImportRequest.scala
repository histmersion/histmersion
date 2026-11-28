package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class MergeImportRequest(
    bundle: MergeImportRequest.Bundle,
    resolutions: MergeResolutions = MergeResolutions(),
    dryRun: MergeImportRequest.DryRun = MergeImportRequest.DryRun.Apply
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MergeImportRequest:
  export api.exchange.Bundle.Name as Bundle
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
