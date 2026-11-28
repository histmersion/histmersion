package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.research.workflows.CheckAnswer
import com.histmersion.domain.research.workflows.SpotCheckAnswer
import com.histmersion.domain.research.workflows.FindingAnswer
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle.ChecksDescription
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.workflows.Verdict
import com.histmersion.util.problem.Problem
import com.histmersion.domain.research.workflows.CritiqueRule
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.ReviewArticle.checksOf
import com.histmersion.domain.research.workflows.Prior
import com.histmersion.domain.ReviewArticle.settle
import com.histmersion.domain.ReviewArticle.verdictOf
import com.histmersion.domain.research.workflows.Report
import com.histmersion.domain.research.workflows.SpotRule
import com.histmersion.domain.ReviewArticle.checkSpotChecks
import com.histmersion.domain.ReviewArticle.CheckRulesLast
import com.histmersion.domain.ReviewArticle.EvidenceCheck
import com.histmersion.domain.ReviewArticle.NoEvidence

final case class RulesAnswer(
    verdict: RulesAnswer.Verdict,
    findings: List[FindingAnswer] = Nil,
    filter: RulesAnswer.Filter,
    uncitedRelevant: List[RulesAnswer.UncitedRelevant] = Nil,
    statusesSilent: List[RulesAnswer.StatusesSilent] = Nil,
    spotChecks: List[SpotCheckAnswer] = Nil,
    checks: List[CheckAnswer] = Nil
) derives JsonCodec

object RulesAnswer:
  export Verdict.Wire as Verdict
  type Filter = Filter.T
  object Filter extends Newtype[String, Pure]
  type UncitedRelevant = UncitedRelevant.T
  object UncitedRelevant extends Newtype[String, Pure]
  type StatusesSilent = StatusesSilent.T
  object StatusesSilent extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[RulesAnswer] = KindlingsSchema
    .derived[RulesAnswer]
    .schema
    .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
    .modify(_.filter)(_.description("The filter you reconstructed and how many sources it returned"))
    .modify(_.uncitedRelevant)(
      _.description("Citekeys the filter returns that clearly belong and are neither cited nor under Not used")
    )
    .modify(_.statusesSilent)(
      _.description("Cited non-current, non-foundational citekeys whose status the article does not state")
    )
    .modify(_.spotChecks)(
      _.description(
        "Checks 5–6: cited claims whose records you read in this step — at least three, on different sources (all when fewer are cited)"
      )
    )
    .modify(_.checks)(_.description(ChecksDescription))

  extension (a: RulesAnswer)
    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    def checkRules(
        last: CheckRulesLast,
        ev: EvidenceCheck = NoEvidence,
        prior: Option[Prior] = None,
        spots: Option[SpotRule] = None,
        critique: Option[CritiqueRule] = None
    ): Either[List[Problem], (Report, List[String])] =
      val (stated, ps, ds) = Reviewer.Rules.checkFindings(a.findings, ev)
      val (fs, _, kws) = critique.fold((stated, Nil, Nil))(c =>
        Reviewer.Rules.checkCritiques(stated, c.copy(shown = Nil, full = CritiqueRule.Full(false)), Nil, last)
      )
      val (cs, cps, cws) = checksOf(Reviewer.Rules, prior, a.checks, last)
      val (ss, sps, sws) =
        spots.fold((Nil, Nil, Nil))(checkSpotChecks(Option(a.spotChecks).getOrElse(Nil), _, ev, last))
      val verdict = verdictOf(a.verdict, stated, last).map(_ => domain.research.workflows.Verdict.of(fs))
      settle(Reviewer.Rules, verdict, fs, ps ++ cps ++ sps, ds ++ cws ++ sws ++ kws, last)(v =>
        Report(Reviewer.Rules, v, fs, checks = cs, spotChecks = ss)
      )
