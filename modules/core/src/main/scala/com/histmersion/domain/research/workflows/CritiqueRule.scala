package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.CritiquePair
import com.histmersion.api.given
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.AgentVerify
import com.histmersion.Newtype

final case class CritiqueRule(
    pairs: List[CritiquePair],
    shown: List[CritiquePair],
    full: CritiqueRule.Full,
    body: CritiqueRule.Body = CritiqueRule.Body(""),
    minRatio: CritiqueRule.MinRatio = CritiqueRule.MinRatio(0.9)
)

object CritiqueRule:
  type Full = Full.T
  object Full extends Newtype[Boolean, Pure]
  export Article.Body
  export AgentVerify.MinRatio
