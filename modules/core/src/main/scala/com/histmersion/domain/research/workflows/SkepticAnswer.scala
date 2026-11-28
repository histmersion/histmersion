package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.research.workflows.CheckAnswer
import com.histmersion.domain.research.workflows.NotMaterialAnswer
import com.histmersion.domain.Proposal
import com.histmersion.domain.research.workflows.FindingAnswer
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle.ChecksDescription
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.workflows.Verdict
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.domain.ReviewArticle.evidenceCheck
import com.histmersion.domain.research.workflows.CritiqueRule
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.ReviewArticle.checksOf
import com.histmersion.domain.research.workflows.Prior
import com.histmersion.domain.ReviewArticle.checkNotMaterial
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.library
import com.histmersion.domain.ReviewArticle.CheckSkepticLast
import com.histmersion.domain.ReviewArticle.settle
import com.histmersion.domain.ReviewArticle.verdictOf
import com.histmersion.domain.research.workflows.Report

final case class SkepticAnswer(
    verdict: SkepticAnswer.Verdict,
    findings: List[FindingAnswer] = Nil,
    proposals: List[Proposal.ProposalAnswer] = Nil,
    claimsChecked: SkepticAnswer.ClaimsChecked,
    checks: List[CheckAnswer] = Nil,
    notMaterial: List[NotMaterialAnswer] = Nil
)

object SkepticAnswer:
  export Verdict.Wire as Verdict
  type ClaimsChecked = ClaimsChecked.T
  object ClaimsChecked extends Newtype[Int, Pure]

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
      val verdict = verdictOf(a.verdict, stated, last).map(_ => domain.research.workflows.Verdict.of(fs))
      settle(Reviewer.Skeptic, verdict, fs, ps ++ cps ++ kps ++ nps, ds ++ cws ++ kws ++ nws, last)(v =>
        Report(Reviewer.Skeptic, v, fs, proposals = verified, checks = cs, notMaterial = nm)
      )
        .map((r, ws) => (r, ws ++ props.collect { case Left(m) => s"skeptic: dropped ${m.sentence}" }))
