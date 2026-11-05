package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object RunCompare:
  import Agents.{ModelOverride, Usage}

  final case class RunSide(
      id: com.histmersion.api.automation.Agents.RunInfo.Id,
      status: RunSide.Status,
      dryRun: RunSide.DryRun,
      model: Option[RunSide.Model],
      modelFor: List[ModelOverride],
      usage: Usage,
      costUsd: RunSide.CostUsd,
      seconds: RunSide.Seconds,
      attempts: RunSide.Attempts,
      failures: RunSide.Failures
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RunSide:
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Seconds = Seconds.T
    object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempts = Attempts.T
    object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failures = Failures.T
    object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class StepSide(
      models: List[StepSide.Models],
      actual: List[StepSide.Actual],
      billing: List[StepSide.Billing],
      usage: Usage,
      costUsd: StepSide.CostUsd,
      seconds: StepSide.Seconds,
      attempts: StepSide.Attempts,
      failures: StepSide.Failures,
      firstCallTokens: Option[StepSide.FirstCallTokens] = None,
      turns: Option[StepSide.Turns] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object StepSide:
    type Models = Models.T
    object Models extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Actual = Actual.T
    object Actual extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Billing = Billing.T
    object Billing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = _root_.com.histmersion.api.automation.RunCompare.RunSide.CostUsd
    val CostUsd: _root_.com.histmersion.api.automation.RunCompare.RunSide.CostUsd.type = _root_.com.histmersion.api.automation.RunCompare.RunSide.CostUsd
    type Seconds = _root_.com.histmersion.api.automation.RunCompare.RunSide.Seconds
    val Seconds: _root_.com.histmersion.api.automation.RunCompare.RunSide.Seconds.type = _root_.com.histmersion.api.automation.RunCompare.RunSide.Seconds
    type Attempts = _root_.com.histmersion.api.automation.RunCompare.RunSide.Attempts
    val Attempts: _root_.com.histmersion.api.automation.RunCompare.RunSide.Attempts.type = _root_.com.histmersion.api.automation.RunCompare.RunSide.Attempts
    type Failures = _root_.com.histmersion.api.automation.RunCompare.RunSide.Failures
    val Failures: _root_.com.histmersion.api.automation.RunCompare.RunSide.Failures.type = _root_.com.histmersion.api.automation.RunCompare.RunSide.Failures
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class StepRow(name: StepRow.Name, runs: List[Option[StepSide]]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object StepRow:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class EnrichSide(
      findings: EnrichSide.Findings,
      quotesChecked: EnrichSide.QuotesChecked,
      quotesPassed: EnrichSide.QuotesPassed,
      placesAsked: EnrichSide.PlacesAsked,
      placesResolved: EnrichSide.PlacesResolved,
      datedFindings: EnrichSide.DatedFindings,
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      summaryChars: EnrichSide.SummaryChars,
      notesChars: EnrichSide.NotesChars,
      uniqueClaims: List[EnrichSide.UniqueClaims]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object EnrichSide:
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QuotesChecked = QuotesChecked.T
    object QuotesChecked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QuotesPassed = QuotesPassed.T
    object QuotesPassed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesAsked = PlacesAsked.T
    object PlacesAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacesResolved = PlacesResolved.T
    object PlacesResolved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DatedFindings = DatedFindings.T
    object DatedFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SummaryChars = SummaryChars.T
    object SummaryChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotesChars = NotesChars.T
    object NotesChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UniqueClaims = UniqueClaims.T
    object UniqueClaims extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ReviewerSide(reviewer: ReviewerSide.Reviewer, verdict: Option[ReviewerSide.Verdict], majors: ReviewerSide.Majors, minors: ReviewerSide.Minors)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ReviewerSide:
    type Reviewer = Reviewer.T
    object Reviewer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Verdict = Verdict.T
    object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Majors = Majors.T
    object Majors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Minors = Minors.T
    object Minors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class KindCount(kind: KindCount.Kind, count: KindCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object KindCount:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ReviewSide(
      verdict: Option[ReviewSide.Verdict],
      rounds: ReviewSide.Rounds,
      reviewers: List[ReviewerSide],
      kinds: List[KindCount],
      majors: List[ReviewSide.Majors]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ReviewSide:
    type Verdict = Verdict.T
    object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rounds = Rounds.T
    object Rounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Majors = Majors.T
    object Majors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class MajorOverlap(a: MajorOverlap.A, b: MajorOverlap.B, aMajors: MajorOverlap.AMajors, bMajors: MajorOverlap.BMajors, shared: MajorOverlap.Shared, pairs: List[MajorOverlap.Pairs])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object MajorOverlap:
    type A = A.T
    object A extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type B = B.T
    object B extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AMajors = AMajors.T
    object AMajors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BMajors = BMajors.T
    object BMajors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Shared = Shared.T
    object Shared extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pairs = Pairs.T
    object Pairs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Comparison(
      pipeline: Comparison.Pipeline,
      target: Comparison.Target,
      runs: List[RunSide],
      steps: List[StepRow],
      enrich: List[EnrichSide] = Nil,
      review: List[ReviewSide] = Nil,
      overlaps: List[MajorOverlap] = Nil,
      notes: List[Comparison.Notes] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Comparison:
    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
