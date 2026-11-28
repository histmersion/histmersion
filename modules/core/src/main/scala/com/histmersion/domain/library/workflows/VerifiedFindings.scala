package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class VerifiedFindings(
    findings: List[VerifiedFinding],
    proposed: Option[VerifiedFindings.Proposed] = None,
    quoteFailed: Option[VerifiedFindings.QuoteFailed] = None
) derives JsonCodec

object VerifiedFindings:
  type Proposed = Proposed.T
  object Proposed extends Newtype[Int, Pure]
  type QuoteFailed = QuoteFailed.T
  object QuoteFailed extends Newtype[Int, Pure]
