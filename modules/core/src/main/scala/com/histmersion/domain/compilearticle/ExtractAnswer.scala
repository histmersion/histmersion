package com.histmersion
package domain.compilearticle

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.research.Coverage
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.domain.compilearticle.ClaimAnswer
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Count
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Where
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.CompileArticle.MaxClaims
import com.histmersion.domain.compilearticle.Claim
import com.histmersion.domain.compilearticle.Extracted
import com.histmersion.domain.CompileArticle.Last
import com.histmersion.domain.Proposal

final case class ExtractAnswer(
    relevant: ExtractAnswer.Relevant,
    reason: Coverage.Rationale,
    claims: List[ClaimAnswer] = Nil
) derives JsonCodec

object ExtractAnswer:
  type Relevant = Relevant.T
  object Relevant extends Newtype[Boolean, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[ExtractAnswer] = KindlingsSchema
    .derived[ExtractAnswer]
    .schema
    .modify(_.relevant)(_.description("Does the record bear on the article's question at all?"))
    .modify(_.reason)(
      _.description("If not relevant: why (wrong region, period or sub-problem). Otherwise what it contributes")
    )
    .modify(_.claims)(_.description("Every claim of the record that bears on the question; empty if not relevant"))

  extension (a: ExtractAnswer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkExtract(
        citekey: Citekey,
        record: AgentVerify.SourceBody,
        minRatio: AgentVerify.MinRatio,
        maxClaims: MaxClaims,
        last: Last
    ): Either[List[Problem], (Extracted, List[Problem])] =
      val checked = a.claims.zipWithIndex.map { (c, i) =>
        val at = Where(s"claim ${i + 1} (${Option(c.claim).map(_.value).getOrElse("").take(50)})")
        if Option(c.claim).forall(_.value.trim.isEmpty) then Left(Content.Proposal(Content.Requirement.NoClaim, at))
        else
          AgentVerify.confidence(Option(c.confidence).getOrElse(library.Confidence.Wire(""))) match
            case Left(m)     => Left(m.inside(at))
            case Right(conf) =>
              val q = Option(c.quote).map(_.value.trim).getOrElse("")
              val m = AgentVerify.findQuote(Proposal.Quote(q), record, minRatio)
              if q.isEmpty then Left(Content.Proposal(Content.Requirement.QuoteGiven, at))
              else if !m.accepted then Left(Content.Proposal(Content.Requirement.QuoteInRecord(Raw.of(citekey)), at))
              else
                Right(
                  Claim(
                    citekey,
                    ClaimAnswer.Claim(c.claim.value.trim),
                    Proposal.Quote(q),
                    c.date.map(d => ClaimAnswer.Date(d.value.trim)).filter(_.value.nonEmpty),
                    c.place.map(p => ClaimAnswer.Place(p.value.trim)).filter(_.value.nonEmpty),
                    conf,
                    m.ratio
                  )
                )
      }
      val claims =
        checked.collect { case Right(c) => c }.distinctBy(c => AgentVerify.normalise(AgentVerify.Text(c.claim.value)))
      val cap = maxClaims.value
      val tooMany =
        Option.when(claims.size > cap)(
          Content.Answer(Content.Requirement.TooManyClaims(Count(cap.toLong), Count(claims.size.toLong)))
        )
      val reason = Option(a.reason).map(r => s"$r".trim).filter(_.nonEmpty)
      val noReason =
        Option.when((!a.relevant.value || a.claims.isEmpty) && reason.isEmpty)(
          Content.Answer(Content.Requirement.ReasonGiven)
        )
      val problems = checked.collect { case Left(m) => m } ++ tooMany.toList ++ noReason.toList
      if problems.nonEmpty && !last.value then Left(problems)
      else
        val kept = if a.relevant.value then claims.take(cap) else Nil
        val notUsed =
          Option.when(kept.isEmpty)(
            Coverage.Rationale(s"${reason.getOrElse("no claim could be verified in its record")}")
          )
        Right(
          (
            Extracted(citekey, kept, notUsed),
            problems.map(_.inside(Where(s"extract $citekey: dropped")))
          )
        )
