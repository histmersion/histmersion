package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("RulesAnswer")))
final case class GuideReview(
    @description("PASS | MINOR | MAJOR — the worst of your findings") verdict: GuideReview.Verdict,
    findings: List[ReportedFinding] = Nil,
    @description("The filter you reconstructed and how many sources it returned") filter: GuideReview.Filter,
    @description("Citekeys the filter returns that clearly belong and are neither cited nor under Not used") uncitedRelevant: List[GuideReview.UncitedRelevant] = Nil,
    @description("Cited non-current, non-foundational citekeys whose status the article does not state") statusesSilent: List[GuideReview.StatusesSilent] = Nil,
    @description("Checks 5–6: cited claims whose records you read in this step — at least three, on different sources (all when fewer are cited)") spotChecks: List[QuoteCheckAnswer] = Nil,
    @description("One check per earlier finding of yours that the review ledger lists as open (empty when it lists none)") checks: List[CheckAnswer] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object GuideReview:
  export PanelVerdict.Wire as Verdict
  type Filter = Filter.T
  object Filter extends Newtype[String, Pure]
  type UncitedRelevant = UncitedRelevant.T
  object UncitedRelevant extends Newtype[String, Pure]
  type StatusesSilent = StatusesSilent.T
  object StatusesSilent extends Newtype[String, Pure]
