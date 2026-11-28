package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Check
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Recheck(
    reviewer: Reviewer,
    checks: List[Check],
    findings: List[Finding],
    lessons: List[Recheck.Lessons] = Nil
) derives JsonCodec

object Recheck:
  type Lessons = Lessons.T
  object Lessons extends Newtype[String, Pure]
