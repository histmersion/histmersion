package com.histmersion
package domain.research.workflows

import com.histmersion.util.problem.Problem
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.domain.library
import com.histmersion.domain.research
import com.histmersion.domain.research.workflows.Fix
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class BodyCorpus(
    isCitekey: library.Citekey => Boolean,
    isTarget: research.Slug => Boolean,
    formatErrors: Fix.Body => List[Problem],
    generated: BodyCorpus.Generated = BodyCorpus.Generated(false)
)

object BodyCorpus:

  type Generated = Generated.T
  object Generated extends Newtype[Boolean, Pure]
