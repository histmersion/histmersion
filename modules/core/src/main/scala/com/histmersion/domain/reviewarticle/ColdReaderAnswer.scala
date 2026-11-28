package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.reviewarticle.CheckAnswer
import com.histmersion.domain.reviewarticle.ReaderFindingAnswer
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.ReviewArticle.ExplainSlots
import com.histmersion.domain.ReviewArticle.ChecksDescription
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.reviewarticle.Verdict
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Content.PanelRule
import com.histmersion.domain.ReviewArticle.TermCountThreshold
import com.histmersion.domain.ReviewArticle.verdictOf
import com.histmersion.domain.ReviewArticle.TermCount
import com.histmersion.domain.reviewarticle.Severity
import com.histmersion.domain.ReviewArticle.CheckColdReaderLast
import com.histmersion.domain.reviewarticle.Reviewer
import com.histmersion.domain.ReviewArticle.settle
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.ReviewArticle.VerdictOfLast
import com.histmersion.domain.ReviewArticle.checksOf
import com.histmersion.domain.reviewarticle.Report
import com.histmersion.domain.reviewarticle.Prior
import com.histmersion.domain.ReviewArticle.emptySlotsOf

final case class ColdReaderAnswer(
    verdict: ColdReaderAnswer.Verdict,
    explainBack: ColdReaderAnswer.ExplainBack,
    emptySlots: List[ColdReaderAnswer.EmptySlots] = Nil,
    findings: List[ReaderFindingAnswer] = Nil,
    termsUndefined: ColdReaderAnswer.TermsUndefined,
    opaqueSentences: ColdReaderAnswer.OpaqueSentences,
    lostAt: Option[ColdReaderAnswer.LostAt] = None,
    checks: List[CheckAnswer] = Nil
) derives JsonCodec

object ColdReaderAnswer:
  export Verdict.Wire as Verdict
  type ExplainBack = ExplainBack.T
  object ExplainBack extends Newtype[String, Pure]
  export ReviewArticle.ExplainSlotsValue as EmptySlots
  type TermsUndefined = TermsUndefined.T
  object TermsUndefined extends Newtype[Int, Pure]
  type OpaqueSentences = OpaqueSentences.T
  object OpaqueSentences extends Newtype[Int, Pure]
  type LostAt = LostAt.T
  object LostAt extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[ColdReaderAnswer] = KindlingsSchema
    .derived[ColdReaderAnswer]
    .schema
    .modify(_.verdict)(_.description("PASS | MINOR | MAJOR — the worst of your findings"))
    .modify(_.explainBack)(
      _.description("Five plain sentences: the question, the answer, who disagrees with whom, what is open")
    )
    .modify(_.emptySlots)(
      _.description("The slot names you could not fill from the text; [] when all four are filled")
    )
    .modify(_.emptySlots)(
      _.modifyUnsafe[String](Schema.ModifyCollectionElements)(
        _.validate(sttp.tapir.Validator.enumeration(ExplainSlots.map(_.value)))
      )
    )
    .modify(_.lostAt)(_.description("The section where you stopped following; null if you never did"))
    .modify(_.checks)(_.description(ChecksDescription))

  extension (a: ColdReaderAnswer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkColdReader(
        last: CheckColdReaderLast,
        prior: Option[Prior] = None
    ): Either[List[Problem], (Report, List[String])] =
      val (stated, ps, _) = Reviewer.ColdReader.checkFindings(a.findings.map(_.asFinding))
      val (slots, odd) = emptySlotsOf(a.emptySlots)
      val unknown: List[Problem] =
        odd.map(s =>
          Content.Answer(Content.Requirement.Panel(PanelRule.SlotKnown(Raw(s), ExplainSlots.toVector.map(Raw.of(_)))))
        )
      val explain = Option(a.explainBack).map(e => ExplainBack(e.value.trim)).filter(_.value.nonEmpty)
      val extra =
        slots
          .filter(ExplainSlots.contains)
          .map(s =>
            (
              Severity.Major,
              Finding.Kind("empty-slot"),
              Finding.Problem(s"the explain-back has no '$s': the article does not say it"),
              Finding.Fix("state it in the opening section")
            )
          ) ++
          Option.when(a.termsUndefined.value > TermCountThreshold)(
            (Severity.Minor, TermCount.Kind, TermCount.problem(a.termsUndefined), TermCount.Fix)
          )
      val added = extra.zipWithIndex.map { case ((sev, k, p, fx), i) =>
        Finding(
          Finding.Id(s"C${stated.size + i + 1}"),
          Reviewer.ColdReader,
          sev,
          k,
          TermCount.Where,
          Finding.Quote(""),
          p,
          fx
        )
      }
      val fs = stated ++ added
      val (cs, cps, cws) = checksOf(Reviewer.ColdReader, prior, a.checks, last)
      val problems =
        ps ++ unknown ++ explain.fold(List(Content.Answer(Content.Requirement.Panel(PanelRule.ExplainBackGiven))))(_ =>
          Nil
        ) ++ cps
      val countOnly =
        a.termsUndefined.value > TermCountThreshold && domain.reviewarticle.Verdict
          .parse(Option(a.verdict).getOrElse(domain.reviewarticle.Verdict.Wire("")))
          .contains(domain.reviewarticle.Verdict.Major)
      settle(
        Reviewer.ColdReader,
        verdictOf(a.verdict, fs, VerdictOfLast(last.value || countOnly)),
        fs,
        problems,
        cws,
        last
      )(v => Report(Reviewer.ColdReader, v, fs, explain, slots.filter(ExplainSlots.contains), checks = cs))
