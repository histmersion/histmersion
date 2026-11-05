package com.histmersion.domain.library.workflows

import com.histmersion.domain.{AgentVerify, CompareRuns}
import com.histmersion.domain.CompareRuns.{EnrichSide, Run, Step}
import com.histmersion.domain.EnrichSource.{Classified, Findings, Geocoded, VerifiedFindings}
import com.histmersion.domain.automation.agents.OutputComparison

import io.circe.parser.decode
import scala.annotation.tailrec

/** enrich-source's runs compared by their outputs (`agent compare`, #992): the findings each kept, the verifier's quote
  * check, the places asked and resolved, the dated findings, the period, the lengths of the summary and the notes, and
  * the claims only one run made.
  */
object EnrichComparison extends OutputComparison:

  /** A rejected findings answer's quote failures are counted from the verifier's own words. */
  type QuoteCheckLoopResultPart1 = QuoteCheckLoopResultPart1.T
  object QuoteCheckLoopResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteCheckLoopResultPart2 = QuoteCheckLoopResultPart2.T
  object QuoteCheckLoopResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteCheckLoopChecked = QuoteCheckLoopChecked.T
  object QuoteCheckLoopChecked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteCheckLoopPassed = QuoteCheckLoopPassed.T
  object QuoteCheckLoopPassed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsFindingsName = IsFindingsName.T
  object IsFindingsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteCheckResultPart1 = QuoteCheckResultPart1.T
  object QuoteCheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuoteCheckResultPart2 = QuoteCheckResultPart2.T
  object QuoteCheckResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClaimsResult = ClaimsResult.T
  object ClaimsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val QuoteFailure = "the quote is not in the text"

  private def isFindings(name: com.histmersion.domain.library.workflows.EnrichComparison.IsFindingsName) = name.startsWith("findings-")

  /** The quote check over every attempt of every findings step: (checked, passed). A finished attempt says it itself; a
    * rejected one holds the rejected answer, and the verifier's reasons say which quotes failed. An attempt with no
    * readable answer checked nothing.
    */
  def quoteCheck(steps: List[Step]): (com.histmersion.domain.library.workflows.EnrichComparison.QuoteCheckResultPart1, com.histmersion.domain.library.workflows.EnrichComparison.QuoteCheckResultPart2) =
    @tailrec def loop(rest: List[Step], checked: com.histmersion.domain.library.workflows.EnrichComparison.QuoteCheckLoopChecked, passed: com.histmersion.domain.library.workflows.EnrichComparison.QuoteCheckLoopPassed): (com.histmersion.domain.library.workflows.EnrichComparison.QuoteCheckLoopResultPart1, com.histmersion.domain.library.workflows.EnrichComparison.QuoteCheckLoopResultPart2) =
      rest match
        case Nil                                                                              => (checked, passed)
        case s :: tail if s.status == com.histmersion.domain.automation.Step.Status.Done.wire =>
          s.output.flatMap(decode[VerifiedFindings](_).toOption) match
            case Some(v) =>
              val n = v.proposed.getOrElse(v.findings.size)
              loop(tail, checked + n, passed + n - v.quoteFailed.getOrElse(0))
            case None => loop(tail, checked, passed)
        case s :: tail =>
          s.output.flatMap(decode[Findings](_).toOption) match
            case Some(f) =>
              val n = f.findings.size
              val fails =
                math.min(n, s.error.fold(0)(e => e.split(java.util.regex.Pattern.quote(QuoteFailure), -1).length - 1))
              loop(tail, checked + n, passed + n - fails)
            case None => loop(tail, checked, passed)
    loop(steps.filter(s => isFindings(s.name)), 0, 0)

  /** The claims a run's enrichment ended with: the geocoded findings, else what the findings steps kept. */
  private def claims(d: Run): List[com.histmersion.domain.library.workflows.EnrichComparison.ClaimsResult] =
    CompareRuns
      .output[Geocoded](d, "geocode")
      .map(_.findings.map(_.claim))
      .getOrElse(
        d.steps
          .map(_.name)
          .distinct
          .filter(isFindings)
          .flatMap(n => CompareRuns.output[VerifiedFindings](d, n).toList.flatMap(_.findings.map(_.claim)))
      )

  override def enrich(runs: List[Run]): List[EnrichSide] =
    val all = runs.map(d => claims(d).map(c => AgentVerify.normalise(c) -> c))
    runs.zip(all).zipWithIndex.map { case ((d, mine), i) =>
      val others = all.patch(i, Nil, 1).flatten.map(_._1).toSet
      val geo = CompareRuns.output[Geocoded](d, "geocode")
      val cls = CompareRuns.output[Classified](d, "classify")
      val (checked, passed) = quoteCheck(d.steps)
      EnrichSide(
        findings = mine.size,
        quotesChecked = checked,
        quotesPassed = passed,
        placesAsked = geo.fold(0)(_.choices.size),
        placesResolved = geo.fold(0)(_.choices.count(_.placeId.isDefined)),
        datedFindings = geo.fold(0)(_.findings.count(f => f.periodStart.isDefined || f.periodEnd.isDefined)),
        periodStart = cls.flatMap(_.period).map(_.start),
        periodEnd = cls.flatMap(_.period).map(_.end),
        summaryChars = cls.fold(0)(_.summary.trim.length),
        notesChars = CompareRuns.output[String](d, "notes").fold(0)(_.trim.length),
        uniqueClaims = mine.filterNot((n, _) => others.contains(n)).map(_._2).distinct
      )
    }
