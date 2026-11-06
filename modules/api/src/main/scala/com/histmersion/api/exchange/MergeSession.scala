package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MergeSession(
    bundle: MergeSession.Bundle,
    resolutions: MergeResolutions = MergeResolutions(),
    updatedAt: Option[MergeSession.UpdatedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeSession:
  type Bundle = com.histmersion.api.exchange.Bundle.Name
  val Bundle: _root_.com.histmersion.api.exchange.Bundle.Name.type = _root_.com.histmersion.api.exchange.Bundle.Name
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
