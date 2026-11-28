package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class RejectedFix(
    id: RejectedFix.Id,
    reason: RejectedFix.Rationale,
    citekey: Citekey,
    quote: RejectedFix.Quote,
    ratio: RejectedFix.Ratio
) derives JsonCodec

object RejectedFix:
  export ReviewFinding.Id
  export Quotation.Quote
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
