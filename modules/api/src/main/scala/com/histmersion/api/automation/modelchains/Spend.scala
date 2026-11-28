package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class Spend(at: Spend.At, tokens: Spend.Tokens, usd: BigDecimal)

object Spend:
  type At = At.T
  object At extends Newtype[Long, Pure]
  type Tokens = Tokens.T
  object Tokens extends Newtype[Long, Pure]
