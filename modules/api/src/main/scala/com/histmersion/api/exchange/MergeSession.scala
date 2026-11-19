package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeSession(
    bundle: MergeSession.Bundle,
    resolutions: MergeResolutions = MergeResolutions(),
    updatedAt: Option[MergeSession.UpdatedAt] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MergeSession:
  export api.exchange.Bundle.Name as Bundle
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
