package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.domain.research.workflows.Finding

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class WontFixAnswer(id: WontFixAnswer.Id, reason: Rationale)

object WontFixAnswer:
  export Finding.Id
