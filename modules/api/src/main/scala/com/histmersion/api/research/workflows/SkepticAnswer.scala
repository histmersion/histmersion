package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class SkepticAnswer(
    @description("PASS | MINOR | MAJOR — the worst of your findings") verdict: SkepticAnswer.Verdict,
    findings: List[ReportedFinding] = Nil,
    @description("Database errors you noticed, as changes for a person to approve: a status, or a disagreement edge — each with a quote from the record") proposals: List[ProposedChange] = Nil,
    @description("How many load-bearing claims you checked") claimsChecked: SkepticAnswer.ClaimsChecked,
    @description("One check per earlier finding of yours that the review ledger lists as open (empty when it lists none)") checks: List[CheckAnswer] = Nil,
    @description("Each open pair of the critique checklist that does NOT bear on a claim the article makes, with why (every open pair shown is either here or named by a missing-critique MAJOR finding)") notMaterial: List[NotMaterialAnswer] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object SkepticAnswer:
  export PanelVerdict.Wire as Verdict
  type ClaimsChecked = ClaimsChecked.T
  object ClaimsChecked extends Newtype[Int, Pure]
