package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.reviewarticle.CheckAnswer
import com.histmersion.domain.reviewarticle.NotMaterialAnswer
import com.histmersion.domain.Proposal
import com.histmersion.domain.reviewarticle.FindingAnswer
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle.ChecksDescription
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.reviewarticle.Verdict
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.domain.ReviewArticle.evidenceCheck
import com.histmersion.domain.reviewarticle.CritiqueRule
import com.histmersion.domain.reviewarticle.Reviewer
import com.histmersion.domain.ReviewArticle.checksOf
import com.histmersion.domain.reviewarticle.Prior
import com.histmersion.domain.ReviewArticle.checkNotMaterial
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.library
import com.histmersion.domain.ReviewArticle.CheckSkepticLast
import com.histmersion.domain.ReviewArticle.settle
import com.histmersion.domain.ReviewArticle.verdictOf
import com.histmersion.domain.reviewarticle.Report

final case class SkepticAnswer(
    verdict: SkepticAnswer.Verdict,
    findings: List[FindingAnswer] = Nil,
    proposals: List[Proposal.ProposalAnswer] = Nil,
    claimsChecked: SkepticAnswer.ClaimsChecked,
    checks: List[CheckAnswer] = Nil,
    notMaterial: List[NotMaterialAnswer] = Nil
) derives JsonCodec

object SkepticAnswer:
  export Verdict.Wire as Verdict
  type ClaimsChecked = ClaimsChecked.T
  object ClaimsChecked extends Newtype[Int, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[SkepticAnswer] = KindlingsSchema
    .derived[SkepticAnswer]
    .schema
    .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
    .modify(_.proposals)(
      _.description(
        "Database errors you noticed, as changes for a person to approve: a status, or a disagreement edge — each with a quote from the record"
      )
    )
    .modify(_.claimsChecked)(_.description("How many load-bearing claims you checked"))
    .modify(_.checks)(_.description(ChecksDescription))
    .modify(_.notMaterial)(
      _.description(
        "Each open pair of the critique checklist that does NOT bear on a claim the article makes, with why " +
          "(every open pair shown is either here or named by a missing-critique MAJOR finding)"
      )
    )

  extension (a: SkepticAnswer)
    def checkSkeptic(
        last: CheckSkepticLast,
        isCitekey: library.Citekey => Boolean,
        text: library.Citekey => Option[AgentVerify.SourceBody],
        minRatio: CritiqueRule.MinRatio,
        prior: Option[Prior] = None,
        critique: Option[CritiqueRule] = None
    ): Either[List[Problem], (Report, List[String])] =
      val (stated, ps, ds) = Reviewer.Skeptic.checkFindings(a.findings, evidenceCheck(isCitekey, text, minRatio))
      val props = a.proposals.map(Proposal.check(_, isCitekey, text, minRatio))
      val verified = props.collect { case Right(p) => p }
      val (fs, kps, kws) = critique.fold((stated, Nil, Nil))(Reviewer.Skeptic.checkCritiques(stated, _, verified, last))
      val (nm, nps, nws) =
        critique.fold((Nil, Nil, Nil))(checkNotMaterial(Option(a.notMaterial).getOrElse(Nil), fs, _, last))
      val (cs, cps, cws) = checksOf(Reviewer.Skeptic, prior, a.checks, last)
      // the stated verdict is held to the findings as stated: one code records minor does not make it a finding left out
      val verdict = verdictOf(a.verdict, stated, last).map(_ => domain.reviewarticle.Verdict.of(fs))
      settle(Reviewer.Skeptic, verdict, fs, ps ++ cps ++ kps ++ nps, ds ++ cws ++ kws ++ nws, last)(v =>
        Report(Reviewer.Skeptic, v, fs, proposals = verified, checks = cs, notMaterial = nm)
      )
        .map((r, ws) => (r, ws ++ props.collect { case Left(m) => s"skeptic: dropped ${m.sentence}" }))
