package com.histmersion
package api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year

object RunCompare:
  import Agents.{ModelOverride, Usage}

  final case class RunSide(
      id: Agents.RunInfo.Id,
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
    object Status extends RefinedType[String, Pure]
    type DryRun = DryRun.T
    object DryRun extends RefinedType[Boolean, Pure]
    type Model = Model.T
    object Model extends RefinedType[String, Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends RefinedType[Double, Pure]
    type Seconds = Seconds.T
    object Seconds extends RefinedType[Long, Pure]
    type Attempts = Attempts.T
    object Attempts extends RefinedType[Int, Pure]
    type Failures = Failures.T
    object Failures extends RefinedType[Int, Pure]


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
    object Models extends RefinedType[String, Pure]
    type Actual = Actual.T
    object Actual extends RefinedType[String, Pure]
    type Billing = Billing.T
    object Billing extends RefinedType[String, Pure]
    export RunSide.{CostUsd, Seconds, Attempts, Failures}
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends RefinedType[Long, Pure]
    type Turns = Turns.T
    object Turns extends RefinedType[Int, Pure]


  final case class StepRow(name: StepRow.Name, runs: List[Option[StepSide]]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object StepRow:
    type Name = Name.T
    object Name extends RefinedType[String, Pure]


  final case class EnrichSide(
      findings: EnrichSide.Findings,
      quotesChecked: EnrichSide.QuotesChecked,
      quotesPassed: EnrichSide.QuotesPassed,
      placesAsked: EnrichSide.PlacesAsked,
      placesResolved: EnrichSide.PlacesResolved,
      datedFindings: EnrichSide.DatedFindings,
      periodStart: Option[Year],
      periodEnd: Option[Year],
      summaryChars: EnrichSide.SummaryChars,
      notesChars: EnrichSide.NotesChars,
      uniqueClaims: List[EnrichSide.UniqueClaims]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object EnrichSide:
    type Findings = Findings.T
    object Findings extends RefinedType[Int, Pure]
    type QuotesChecked = QuotesChecked.T
    object QuotesChecked extends RefinedType[Int, Pure]
    type QuotesPassed = QuotesPassed.T
    object QuotesPassed extends RefinedType[Int, Pure]
    type PlacesAsked = PlacesAsked.T
    object PlacesAsked extends RefinedType[Int, Pure]
    type PlacesResolved = PlacesResolved.T
    object PlacesResolved extends RefinedType[Int, Pure]
    type DatedFindings = DatedFindings.T
    object DatedFindings extends RefinedType[Int, Pure]
    type SummaryChars = SummaryChars.T
    object SummaryChars extends RefinedType[Int, Pure]
    type NotesChars = NotesChars.T
    object NotesChars extends RefinedType[Int, Pure]
    type UniqueClaims = UniqueClaims.T
    object UniqueClaims extends RefinedType[String, Pure]


  final case class ReviewerSide(reviewer: ReviewerSide.Reviewer, verdict: Option[ReviewerSide.Verdict], majors: ReviewerSide.Majors, minors: ReviewerSide.Minors)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ReviewerSide:
    type Reviewer = Reviewer.T
    object Reviewer extends RefinedType[String, Pure]
    type Verdict = Verdict.T
    object Verdict extends RefinedType[String, Pure]
    type Majors = Majors.T
    object Majors extends RefinedType[Int, Pure]
    type Minors = Minors.T
    object Minors extends RefinedType[Int, Pure]


  final case class KindCount(kind: KindCount.Kind, count: KindCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object KindCount:
    type Kind = Kind.T
    object Kind extends RefinedType[String, Pure]
    type Count = Count.T
    object Count extends RefinedType[Int, Pure]


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
    object Verdict extends RefinedType[String, Pure]
    type Rounds = Rounds.T
    object Rounds extends RefinedType[Int, Pure]
    type Majors = Majors.T
    object Majors extends RefinedType[String, Pure]


  final case class MajorOverlap(a: MajorOverlap.A, b: MajorOverlap.B, aMajors: MajorOverlap.AMajors, bMajors: MajorOverlap.BMajors, shared: MajorOverlap.Shared, pairs: List[MajorOverlap.Pairs])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object MajorOverlap:
    type A = A.T
    object A extends RefinedType[String, Pure]
    type B = B.T
    object B extends RefinedType[String, Pure]
    type AMajors = AMajors.T
    object AMajors extends RefinedType[Int, Pure]
    type BMajors = BMajors.T
    object BMajors extends RefinedType[Int, Pure]
    type Shared = Shared.T
    object Shared extends RefinedType[Int, Pure]
    type Pairs = Pairs.T
    object Pairs extends RefinedType[String, Pure]


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
    object Pipeline extends RefinedType[String, Pure]
    type Target = Target.T
    object Target extends RefinedType[String, Pure]
    type Notes = Notes.T
    object Notes extends RefinedType[String, Pure]
