package com.histmersion
package domain.reviewledger

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.Newtype
import Regression.Rationale

final case class Regression(id: Regression.Id, why: Rationale) derives JsonCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line: String = s"$id (closed): $why"

object Regression:

  /** Why the revision reopens the finding: the guard's account of what it found, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  export Finding.Id
