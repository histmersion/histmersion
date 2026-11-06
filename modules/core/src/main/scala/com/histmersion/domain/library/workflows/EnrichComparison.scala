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
  private val QuoteFailure = "the quote is not in the text"

  private def isFindings(name: Step.Name) = name.value.startsWith("findings-")

  /** The quote check over every attempt of every findings step: (checked, passed). A finished attempt says it itself; a
    * rejected one holds the rejected answer, and the verifier's reasons say which quotes failed. An attempt with no
    * readable answer checked nothing.
    */
  def quoteCheck(steps: List[Step]): (EnrichSide.QuotesChecked, EnrichSide.QuotesPassed) =
    @tailrec def loop(rest: List[Step], checked: Int, passed: Int): (Int, Int) =
      rest match
        case Nil                                                                              => (checked, passed)
        case s :: tail if s.status == com.histmersion.domain.automation.Step.Status.Done.wire =>
          s.output.flatMap(o => decode[VerifiedFindings](o.value).toOption) match
            case Some(v) =>
              val n = v.proposed.fold(v.findings.size)(_.value)
              loop(tail, checked + n, passed + n - v.quoteFailed.fold(0)(_.value))
            case None => loop(tail, checked, passed)
        case s :: tail =>
          s.output.flatMap(o => decode[Findings](o.value).toOption) match
            case Some(f) =>
              val n = f.findings.size
              val fails =
                math.min(n, s.error.fold(0)(e => e.value.split(java.util.regex.Pattern.quote(QuoteFailure), -1).length - 1))
              loop(tail, checked + n, passed + n - fails)
            case None => loop(tail, checked, passed)
    val (checked, passed) = loop(steps.filter(s => isFindings(s.name)), 0, 0)
    (EnrichSide.QuotesChecked(checked), EnrichSide.QuotesPassed(passed))

  /** The claims a run's enrichment ended with: the geocoded findings, else what the findings steps kept. */
  private def claims(d: Run): List[String] =
    CompareRuns
      .output[Geocoded](d, com.histmersion.domain.CompareRuns.OutputName("geocode"))
      .map(_.findings.map(_.claim.value))
      .getOrElse(
        d.steps
          .map(_.name)
          .distinct
          .filter(isFindings)
          .flatMap(n => CompareRuns.output[VerifiedFindings](d, n).toList.flatMap(_.findings.map(_.claim.value)))
      )

  override def enrich(runs: List[Run]): List[EnrichSide] =
    val all = runs.map(d => claims(d).map(c => AgentVerify.normalise(AgentVerify.Text(c)) -> c))
    runs.zip(all).zipWithIndex.map { case ((d, mine), i) =>
      val others = all.patch(i, Nil, 1).flatten.map(_._1).toSet
      val geo = CompareRuns.output[Geocoded](d, com.histmersion.domain.CompareRuns.OutputName("geocode"))
      val cls = CompareRuns.output[Classified](d, com.histmersion.domain.CompareRuns.OutputName("classify"))
      val (checked, passed) = quoteCheck(d.steps)
      EnrichSide(
        findings = EnrichSide.Findings(mine.size),
        quotesChecked = checked,
        quotesPassed = passed,
        placesAsked = EnrichSide.PlacesAsked(geo.fold(0)(_.choices.size)),
        placesResolved = EnrichSide.PlacesResolved(geo.fold(0)(_.choices.count(_.placeId.isDefined))),
        datedFindings = EnrichSide.DatedFindings(geo.fold(0)(_.findings.count(f => f.periodStart.isDefined || f.periodEnd.isDefined))),
        periodStart = cls.flatMap(_.period).map(_.start),
        periodEnd = cls.flatMap(_.period).map(_.end),
        summaryChars = EnrichSide.SummaryChars(cls.fold(0)(_.summary.value.trim.length)),
        notesChars = EnrichSide.NotesChars(CompareRuns.output[String](d, com.histmersion.domain.CompareRuns.OutputName("notes")).fold(0)(_.trim.length)),
        uniqueClaims = mine.filterNot((n, _) => others.contains(n)).map(_._2).distinct.map(EnrichSide.UniqueClaims(_))
      )
    }
