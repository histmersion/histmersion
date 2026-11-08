package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeSession(
    bundle: MergeSession.Bundle,
    resolutions: MergeResolutions = MergeResolutions(),
    updatedAt: Option[MergeSession.UpdatedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeSession:
  export com.histmersion.api.exchange.Bundle.{Name as Bundle}
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
