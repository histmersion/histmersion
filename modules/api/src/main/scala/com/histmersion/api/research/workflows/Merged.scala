package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Merged(
    round: Merged.Round,
    verdict: PanelVerdict,
    findings: List[ReviewFinding],
    explainBack: Option[Merged.ExplainBack],
    emptySlots: List[Merged.EmptySlots],
    proposals: List[Proposal],
    deferred: List[Merged.Deferred] = Nil
) derives JsonCodec

object Merged:
  type Round = Round.T
  object Round extends Newtype[Int, Pure]
  export ColdReaderAnswer.ExplainBack
  export ReviewFinding.Id as Deferred
  type EmptySlots = EmptySlots.ExplainSlotsValue
  object EmptySlots:
    type ExplainSlotsValue = ExplainSlotsValue.T
    object ExplainSlotsValue extends Newtype[String, Pure]
