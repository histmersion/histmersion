package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class DeclinedFix(id: DeclinedFix.Id, reason: DeclinedFix.Rationale) derives JsonCodec

object DeclinedFix:
  export ReviewFinding.Id
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
