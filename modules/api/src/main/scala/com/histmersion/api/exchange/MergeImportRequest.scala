package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeImportRequest(
    bundle: MergeImportRequest.Bundle,
    resolutions: MergeResolutions = MergeResolutions(),
    dryRun: MergeImportRequest.DryRun = MergeImportRequest.DryRun(false)
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MergeImportRequest:
  export com.histmersion.api.exchange.Bundle.Name as Bundle
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
