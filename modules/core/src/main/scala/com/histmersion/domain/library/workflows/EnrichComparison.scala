package com.histmersion
package domain.library.workflows

import com.histmersion.domain.library.workflows.VerifiedFindings
import com.histmersion.domain.library.workflows.Geocoded
import com.histmersion.domain.library.workflows.Classified
import com.histmersion.domain.automation.agents.Stored

import com.histmersion.util.problem.Text.sentence
import com.histmersion.domain.{AgentVerify, CompareRuns}
import com.histmersion.domain.CompareRuns.{EnrichSide, Run, Step}
import com.histmersion.domain.automation.agents.OutputComparison

import scala.annotation.tailrec
import com.histmersion.lint.{tolerated, LintRule}

/** enrich-source's runs compared by their outputs (`agent compare`, #992): the findings each kept, the verifier's quote
  * check, the places asked and resolved, the dated findings, the period, the lengths of the summary and the notes, and
  * the claims only one run made.
  */
final class EnrichComparison(records: EnrichRecords) extends OutputComparison:
  import records.given

  /** A rejected findings answer's quote failures are counted from the verifier's own words. */
  private val QuoteFailure = "the quote is not in the text"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def isFindings(name: Step.Name) = name.value.startsWith("findings-")

  /** The quote check over every attempt of every findings step: (checked, passed). A finished attempt says it itself; a
    * rejected one holds the rejected answer, and the verifier's reasons say which quotes failed. An attempt with no
    * readable answer checked nothing.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G5ProblemText, "pending G60 (a caller branches on the case, never the sentence)")
  def quoteCheck(steps: List[Step]): (EnrichSide.QuotesChecked, EnrichSide.QuotesPassed) =
    @tailrec def loop(rest: List[Step], checked: Int, passed: Int): (Int, Int) =
      rest match
        case Nil                                                              => (checked, passed)
        case s :: tail if s.status == domain.automation.Step.Status.Done.wire =>
          s.output.flatMap(o => verifiedFindings.read(Stored.Json(o.value)).toOption) match
            case Some(v) =>
              val n = v.proposed.fold(v.findings.size)(_.value)
              loop(tail, checked + n, passed + n - v.quoteFailed.fold(0)(_.value))
            case None => loop(tail, checked, passed)
        case s :: tail =>
          s.output.flatMap(o => findings.read(Stored.Json(o.value)).toOption) match
            case Some(f) =>
              val n = f.findings.size
              val fails =
                math.min(
                  n,
                  s.error.fold(0)(e => e.sentence.split(java.util.regex.Pattern.quote(QuoteFailure), -1).length - 1)
                )
              loop(tail, checked + n, passed + n - fails)
            case None => loop(tail, checked, passed)
    val (checked, passed) = loop(steps.filter(s => isFindings(s.name)), 0, 0)
    (EnrichSide.QuotesChecked(checked), EnrichSide.QuotesPassed(passed))

  /** The claims a run's enrichment ended with: the geocoded findings, else what the findings steps kept. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def claims(d: Run): List[String] =
    d.stored[Geocoded](CompareRuns.OutputName("geocode"))
      .map(_.findings.map(_.claim.value))
      .getOrElse(
        d.steps
          .map(_.name)
          .distinct
          .filter(isFindings)
          .flatMap(n => d.stored[VerifiedFindings](n).toList.flatMap(_.findings.map(_.claim.value)))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def enrich(runs: List[Run]): List[EnrichSide] =
    val all = runs.map(d => claims(d).map(c => AgentVerify.normalise(AgentVerify.Text(c)) -> c))
    runs.zip(all).zipWithIndex.map { case ((d, mine), i) =>
      val others = all.patch(i, Nil, 1).flatten.map(_._1).toSet
      val geo = d.stored[Geocoded](CompareRuns.OutputName("geocode"))
      val cls = d.stored[Classified](CompareRuns.OutputName("classify"))
      val (checked, passed) = quoteCheck(d.steps)
      EnrichSide(
        findings = EnrichSide.Findings(mine.size),
        quotesChecked = checked,
        quotesPassed = passed,
        placesAsked = EnrichSide.PlacesAsked(geo.fold(0)(_.choices.size)),
        placesResolved = EnrichSide.PlacesResolved(geo.fold(0)(_.choices.count(_.placeId.isDefined))),
        datedFindings = EnrichSide
          .DatedFindings(geo.fold(0)(_.findings.count(f => f.periodStart.isDefined || f.periodEnd.isDefined))),
        periodStart = cls.flatMap(_.period).map(_.start),
        periodEnd = cls.flatMap(_.period).map(_.end),
        summaryChars = EnrichSide.SummaryChars(cls.fold(0)(_.summary.value.trim.length)),
        notesChars = EnrichSide.NotesChars(d.stored[String](CompareRuns.OutputName("notes")).fold(0)(_.trim.length)),
        uniqueClaims = mine.filterNot((n, _) => others.contains(n)).map(_._2).distinct.map(EnrichSide.UniqueClaims(_))
      )
    }
