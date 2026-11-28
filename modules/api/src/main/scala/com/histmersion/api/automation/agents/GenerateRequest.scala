package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class GenerateRequest(
    runtimes: List[GenerateRequest.Runtimes] = Nil,
    out: Option[GenerateRequest.Out] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object GenerateRequest:
  type Runtimes = Runtimes.T
  object Runtimes extends Newtype[String, Pure]
  type Out = Out.T
  object Out extends Newtype[String, Pure]
