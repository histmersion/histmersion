package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.domain.AgentVerify
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.Newtype

final case class SpotRule(
    required: SpotRule.Required,
    cited: Set[SpotRule.Cited],
    body: SpotRule.Body,
    calls: List[AgentVerify.ToolUse],
    minRatio: SpotRule.MinRatio
)

object SpotRule:
  type Required = Required.T
  object Required extends Newtype[Int, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Cited = library.Citekey
  val Cited: library.Citekey.type = library.Citekey
  export Article.Body
  export AgentVerify.MinRatio
