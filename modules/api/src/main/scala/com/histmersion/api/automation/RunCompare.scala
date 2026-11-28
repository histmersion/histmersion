package com.histmersion
package api.automation
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object RunCompare:
  import Agents.{ModelOverride, Usage}

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object RunSide:
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    enum DryRun derives CanEqual:
      case DryRun, Apply
    object DryRun extends TwoStates[DryRun]:
      extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends Newtype[Double, Pure]
    type Seconds = Seconds.T
    object Seconds extends Newtype[Long, Pure]
    type Attempts = Attempts.T
    object Attempts extends Newtype[Int, Pure]
    type Failures = Failures.T
    object Failures extends Newtype[Int, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object StepSide:
    type Models = Models.T
    object Models extends Newtype[String, Pure]
    type Actual = Actual.T
    object Actual extends Newtype[String, Pure]
    type Billing = Billing.T
    object Billing extends Newtype[String, Pure]
    export RunSide.{Attempts, CostUsd, Failures, Seconds}
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends Newtype[Long, Pure]
    type Turns = Turns.T
    object Turns extends Newtype[Int, Pure]

  @tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
  final case class StepRow(name: StepRow.Name, runs: List[Option[StepSide]])
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object StepRow:
    type Name = Name.T
    object Name extends Newtype[String, Pure]

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
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object EnrichSide:
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    type QuotesChecked = QuotesChecked.T
    object QuotesChecked extends Newtype[Int, Pure]
    type QuotesPassed = QuotesPassed.T
    object QuotesPassed extends Newtype[Int, Pure]
    type PlacesAsked = PlacesAsked.T
    object PlacesAsked extends Newtype[Int, Pure]
    type PlacesResolved = PlacesResolved.T
    object PlacesResolved extends Newtype[Int, Pure]
    type DatedFindings = DatedFindings.T
    object DatedFindings extends Newtype[Int, Pure]
    type SummaryChars = SummaryChars.T
    object SummaryChars extends Newtype[Int, Pure]
    type NotesChars = NotesChars.T
    object NotesChars extends Newtype[Int, Pure]
    type UniqueClaims = UniqueClaims.T
    object UniqueClaims extends Newtype[String, Pure]

  final case class ReviewerSide(
      reviewer: ReviewerSide.Reviewer,
      verdict: Option[ReviewerSide.Verdict],
      majors: ReviewerSide.Majors,
      minors: ReviewerSide.Minors
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object ReviewerSide:
    type Reviewer = Reviewer.T
    object Reviewer extends Newtype[String, Pure]
    type Verdict = Verdict.T
    object Verdict extends Newtype[String, Pure]
    type Majors = Majors.T
    object Majors extends Newtype[Int, Pure]
    type Minors = Minors.T
    object Minors extends Newtype[Int, Pure]

  final case class KindCount(kind: KindCount.Kind, count: KindCount.Count)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object KindCount:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

  final case class ReviewSide(
      verdict: Option[ReviewSide.Verdict],
      rounds: ReviewSide.Rounds,
      reviewers: List[ReviewerSide],
      kinds: List[KindCount],
      majors: List[ReviewSide.Majors]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object ReviewSide:
    type Verdict = Verdict.T
    object Verdict extends Newtype[String, Pure]
    type Rounds = Rounds.T
    object Rounds extends Newtype[Int, Pure]
    type Majors = Majors.T
    object Majors extends Newtype[String, Pure]

  final case class MajorOverlap(
      a: MajorOverlap.A,
      b: MajorOverlap.B,
      aMajors: MajorOverlap.AMajors,
      bMajors: MajorOverlap.BMajors,
      shared: MajorOverlap.Shared,
      pairs: List[MajorOverlap.Pairs]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object MajorOverlap:
    type A = A.T
    object A extends Newtype[String, Pure]
    type B = B.T
    object B extends Newtype[String, Pure]
    type AMajors = AMajors.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object AMajors extends Newtype[Int, Pure]
    type BMajors = BMajors.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object BMajors extends Newtype[Int, Pure]
    type Shared = Shared.T
    object Shared extends Newtype[Int, Pure]
    type Pairs = Pairs.T
    object Pairs extends Newtype[String, Pure]

  final case class Comparison(
      pipeline: Comparison.Pipeline,
      target: Comparison.Target,
      runs: List[RunSide],
      steps: List[StepRow],
      enrich: List[EnrichSide] = Nil,
      review: List[ReviewSide] = Nil,
      overlaps: List[MajorOverlap] = Nil,
      notes: List[Comparison.Notes] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Comparison:
    type Pipeline = Pipeline.T
    object Pipeline extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
