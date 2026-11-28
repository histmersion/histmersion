package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library.workflows.FindingProposal
import com.histmersion.domain.AgentVerify.*
import com.histmersion.domain.AgentVerify
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.Share
import com.histmersion.util.problem.Problem.Where
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.catalogue.Period
import com.histmersion.domain.library.workflows.Frame
import com.histmersion.domain.library.workflows.Loaded
import com.histmersion.domain.EnrichSource.MinRatio
import com.histmersion.domain.library.workflows.FindingGuards
import com.histmersion.domain.EnrichSource.Last
import com.histmersion.domain.EnrichSource.normaliseTag
import com.histmersion.domain.library.workflows.VerifiedFindings
import com.histmersion.domain.EnrichSource.centuryAsYear
import com.histmersion.domain.EnrichSource.placementProblem
import com.histmersion.domain.EnrichSource.Description
import com.histmersion.domain.EnrichSource.Text
import com.histmersion.domain.library.workflows.StatedPeriod
import com.histmersion.domain.library.workflows.VerifiedFinding
import com.histmersion.domain.EnrichSource.CheckFindingsMaxAbsYear
import com.histmersion.api.catalogue.geocode.KindHint

@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Findings(findings: List[FindingProposal] = Nil)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object Findings:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def normalised(s: String): NormalisedText = normalise(AgentVerify.Text(s))

  extension (f: Findings)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkFindings(
        window: Frame,
        minRatio: MinRatio,
        maxAbsYear: CheckFindingsMaxAbsYear,
        within: Option[Period],
        existingClaims: Set[Loaded.ExistingClaims],
        guards: FindingGuards,
        last: Last
    ): Either[List[Problem], (VerifiedFindings, List[Description])] =
      val windowBody = AgentVerify.SourceBody(window.text.value)
      val quotes = f.findings.map(p => findQuote(p.quote, windowBody, minRatio))
      val checked = f.findings.zip(quotes).zipWithIndex.map { case ((p, quote), i) =>
        val at = Where(s"finding ${i + 1} (${p.claim.value.take(60)})")
        val conf = AgentVerify.confidence(p.confidence)
        val per: Either[Problems, Option[Period]] = (p.start, p.end) match
          case (None, None)    => Right(None)
          case (Some(s), e)    => StatedPeriod(s, e.getOrElse(s)).convert(maxAbsYear, None).map(Some(_))
          case (None, Some(e)) => StatedPeriod(e, e).convert(maxAbsYear, None).map(Some(_))
        val issues = List(
          Option.when(p.claim.value.trim.isEmpty)(Content.Proposal(Content.Requirement.ClaimGiven, at)),
          Option.when(!quote.accepted)(
            Content.Proposal(Content.Requirement.QuoteInWindow(Share.of(quote.ratio)), at)
          ),
          conf.left.toOption.map(_.inside(at))
        ).flatten ++ per.left.toOption.toList.flatMap(_.toVector).map(_.inside(at))
        val said = List(Some(p.claim.value), Some(p.quote.value), p.periodName.map(_.value)).flatten.mkString("\n")
        val century =
          per.toOption.flatten
            .flatMap(pd => centuryAsYear(pd.start, pd.end, Text(said), within, guards.centuryGapYears))
        val pointer =
          FindingProposal
            .pointerPhrase(p.claim, guards.pointerPhrases)
            .map(ph => Content.Requirement.PointerPhrase(Raw.of(ph)))
        val soft: List[Problem] = (century.toList ++ pointer.toList).map(Content.Proposal(_, at))
        val result =
          if issues.nonEmpty then Left(issues)
          else
            Right(
              VerifiedFinding(
                VerifiedFinding.Claim(p.claim.value.trim),
                VerifiedFinding.Quote(p.quote.value.trim),
                quote.ratio,
                p.place.map(_.value.trim).filter(_.nonEmpty).map(VerifiedFinding.Place(_)),
                per.toOption.flatten.map(_.start),
                per.toOption.flatten.map(_.end),
                p.periodName.map(_.value.trim).filter(_.nonEmpty).map(VerifiedFinding.PeriodName(_)),
                p.tags.map(normaliseTag).filter(_.value.nonEmpty).distinct,
                VerifiedFinding.Confidence(conf.toOption.get.toString.toLowerCase(java.util.Locale.ROOT)),
                p.placeKind.map(KindHint.parse).filter(_ != KindHint.Unknown)
              )
            )
        (result, soft, per.toOption.flatten)
      }
      val results = checked.map(_._1)
      val rejected = results.collect { case Left(ps) => ps }.flatten
      val named = f.findings.zip(checked).zipWithIndex.flatMap { case ((p, (_, _, per)), i) =>
        p.place.map(pl => FindingProposal.Place(pl.value.trim)).filter(_.value.nonEmpty).map { name =>
          val kind = p.placeKind.map(KindHint.parse).getOrElse(KindHint.Unknown)
          (Problem.Count(i + 1L), name, guards.resolves(name, kind, per))
        }
      }
      val soft = checked.flatMap(_._2) ++ placementProblem(named, guards.minPlacedShare).toList
      if (rejected.nonEmpty || soft.nonEmpty) && !last.value then Left(rejected ++ soft)
      else
        val norm = existingClaims.map(c => normalised(c.value))
        val kept = results
          .collect { case Right(v) => v }
          .filterNot(v => norm.contains(normalised(v.claim.value)))
          .distinctBy(v => normalised(v.quote.value))
        val outside = for
          v <- kept; s <- v.start; e <- v.end; p <- within
          problem <- periodProblems(PeriodStart(s.value), PeriodEnd(e.value), maxAbsYear, Some(p)).lastOption
        yield s"check '${v.claim.value.take(60)}': ${problem.sentence}"
        Right(
          (
            VerifiedFindings(
              kept,
              Some(VerifiedFindings.Proposed(f.findings.size)),
              Some(VerifiedFindings.QuoteFailed(quotes.count(!_.accepted)))
            ),
            (rejected.map(p => s"dropped ${p.sentence}") ++ outside ++ soft.map(p => s"kept ${p.sentence}"))
              .map(Description(_))
          )
        )
