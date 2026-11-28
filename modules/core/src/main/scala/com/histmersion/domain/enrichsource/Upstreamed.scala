package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Upstreamed(
    ran: Upstreamed.Ran,
    summary: Upstreamed.Summary,
    retracted: Upstreamed.Retracted = Upstreamed.Retracted(false)
) derives JsonCodec

object Upstreamed:
  type Ran = Ran.T
  object Ran extends Newtype[Boolean, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Retracted = Retracted.T
  object Retracted extends Newtype[Boolean, Pure]
