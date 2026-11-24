package com.histmersion
package api.personal
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema
import com.histmersion.lint.{LintRule, tolerated}

type MapZoom = MapZoom.T

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object MapZoom extends Newtype[Double, Pure]:
  given JsonValueCodec[MapZoom] = JsonCodec.mapped[MapZoom, Double](_.value, b => Right(MapZoom(b)))
  given Schema[MapZoom] = Schema.schemaForDouble.as[MapZoom]
