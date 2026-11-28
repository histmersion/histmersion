package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.CheckAnswer
import com.histmersion.domain.research.workflows.FindingAnswer
import com.histmersion.domain.research.workflows.Recheck

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RecheckAnswer(
    checks: List[CheckAnswer] = Nil,
    findings: List[FindingAnswer] = Nil,
    lessons: List[RecheckAnswer.Lessons] = Nil
)

object RecheckAnswer:
  export Recheck.Lessons
