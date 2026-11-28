package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class ProbeRequest(citekey: Citekey, model: Option[ProbeRequest.Model] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ProbeRequest:
  type Model = Model.T
  object Model extends Newtype[String, Pure]
