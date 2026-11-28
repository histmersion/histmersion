package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class RecheckedFindings(
    reviewer: Reviewer,
    checks: List[CheckedFinding],
    findings: List[ReviewFinding],
    lessons: List[RecheckedFindings.Lessons] = Nil
) derives JsonCodec

object RecheckedFindings:
  type Lessons = Lessons.T
  object Lessons extends Newtype[String, Pure]
