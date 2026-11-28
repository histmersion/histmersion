package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ModReport(source: ModReport.Source, rateLimits: List[ModWindow])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ModReport:
  type Source = Source.T
  object Source extends Newtype[String, Pure]
