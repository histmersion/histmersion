package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.api.research.CritiqueRef
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class NotMaterialAnswer(pair: NotMaterialAnswer.Pair, reason: Rationale)

object NotMaterialAnswer:
  export CritiqueRef.Wire as Pair
