package com.histmersion.domain

import scala.annotation.tailrec
import io.circe.Decoder
import io.circe.parser.decode
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.util.text.ComparedText

/** `agent compare` (#992): the pure half. Reads two or more runs' recorded steps (their FULL outputs) and lays them
  * side by side; `billing` names how a configured model is paid for (a plan pool or a price — the server knows, from
  * its config). Nothing is judged: counts, tokens, times, and what one run produced that the others did not.
  */
object CompareRuns:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Similarity = Similarity.T
  object Similarity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── what is compared: the recorded runs ──────────────────────────────────────────────────────

  /** Tokens, as a provider reports them; `input` includes the cached ones. */

  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export com.histmersion.domain.CompareRuns.Step.{Name as OutputName}

  final case class Tokens(input: Tokens.Input = com.histmersion.domain.CompareRuns.Tokens.Input(0), output: Tokens.Output = com.histmersion.domain.CompareRuns.Tokens.Output(0), cacheRead: Tokens.CacheRead = com.histmersion.domain.CompareRuns.Tokens.CacheRead(0), cacheWrite: Tokens.CacheWrite = com.histmersion.domain.CompareRuns.Tokens.CacheWrite(0)):
    def +(o: Tokens): Tokens =
      Tokens(
        Tokens.Input(input.value + o.input.value),
        Tokens.Output(output.value + o.output.value),
        Tokens.CacheRead(cacheRead.value + o.cacheRead.value),
        Tokens.CacheWrite(cacheWrite.value + o.cacheWrite.value)
      )

  object Tokens:
    type Input = Input.T
    object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CacheRead = CacheRead.T
    object CacheRead extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CacheWrite = CacheWrite.T
    object CacheWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Zero: Tokens = Tokens()

  /** A role's model, as a run was told to use it. */
  final case class ModelChoice(role: ModelChoice.Role, model: ModelChoice.Model)

  object ModelChoice:
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A run as compared: what it ran on, its state, and the models it was given. */
  final case class RunHead(
      id: RunHead.Id,
      pipeline: RunHead.Pipeline,
      target: RunHead.Target,
      status: RunHead.Status,
      dryRun: RunHead.DryRun,
      model: Option[RunHead.Model],
      modelFor: List[ModelChoice]
  )

  object RunHead:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      extension (id: Id) def short: String = id.value.take(8)
    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = com.histmersion.domain.automation.Run.State.Wire
    val Status: _root_.com.histmersion.domain.automation.Run.State.Wire.type = _root_.com.histmersion.domain.automation.Run.State.Wire
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One attempt of one step, with its full recorded output. `firstCallTokens` and `turns` are None where nothing was
    * recorded (a provider that cannot tell, or a run recorded before they were kept).
    */
  final case class Step(
      name: Step.Name,
      status: Step.Status,
      model: Option[Step.Model],
      actualModel: Option[Step.ActualModel],
      usage: Tokens,
      costUsd: Step.CostUsd,
      startedAt: Step.StartedAt,
      finishedAt: Step.FinishedAt,
      firstCallTokens: Option[Step.FirstCallTokens],
      turns: Option[Step.Turns],
      output: Option[Step.Output],
      error: Option[Step.Error]
  )

  object Step:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = com.histmersion.domain.automation.Step.Status.Wire
    val Status: _root_.com.histmersion.domain.automation.Step.Status.Wire.type = _root_.com.histmersion.domain.automation.Step.Status.Wire
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartedAt = StartedAt.T
    object StartedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishedAt = FinishedAt.T
    object FinishedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Run(run: RunHead, steps: List[Step])

  // ── what a comparison says ───────────────────────────────────────────────────────────────────

  /** One run, in total. `seconds` = the sum of its steps' durations. */
  final case class RunSide(
      id: RunSide.Id,
      status: RunSide.Status,
      dryRun: RunSide.DryRun,
      model: Option[RunSide.Model],
      modelFor: List[ModelChoice],
      usage: Tokens,
      costUsd: RunSide.CostUsd,
      seconds: RunSide.Seconds,
      attempts: RunSide.Attempts,
      failures: RunSide.Failures
  )

  object RunSide:
    type Id = _root_.com.histmersion.domain.CompareRuns.RunHead.Id
    val Id: _root_.com.histmersion.domain.CompareRuns.RunHead.Id.type = _root_.com.histmersion.domain.CompareRuns.RunHead.Id
    type Status = _root_.com.histmersion.domain.CompareRuns.RunHead.Status
    val Status: _root_.com.histmersion.domain.CompareRuns.RunHead.Status.type = _root_.com.histmersion.domain.CompareRuns.RunHead.Status
    type DryRun = _root_.com.histmersion.domain.CompareRuns.RunHead.DryRun
    val DryRun: _root_.com.histmersion.domain.CompareRuns.RunHead.DryRun.type = _root_.com.histmersion.domain.CompareRuns.RunHead.DryRun
    type Model = _root_.com.histmersion.domain.CompareRuns.RunHead.Model
    val Model: _root_.com.histmersion.domain.CompareRuns.RunHead.Model.type = _root_.com.histmersion.domain.CompareRuns.RunHead.Model
    type CostUsd = _root_.com.histmersion.domain.CompareRuns.Step.CostUsd
    val CostUsd: _root_.com.histmersion.domain.CompareRuns.Step.CostUsd.type = _root_.com.histmersion.domain.CompareRuns.Step.CostUsd
    type Seconds = _root_.com.histmersion.domain.CompareRuns.Seconds
    val Seconds: _root_.com.histmersion.domain.CompareRuns.Seconds.type = _root_.com.histmersion.domain.CompareRuns.Seconds
    type Attempts = Attempts.T
    object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failures = Failures.T
    object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One step of one run, every attempt summed. `billing` names how each configured model is paid for. */
  final case class StepSide(
      models: List[StepSide.Models],
      actual: List[StepSide.Actual],
      billing: List[StepSide.Billing],
      usage: Tokens,
      costUsd: StepSide.CostUsd,
      seconds: StepSide.Seconds,
      attempts: StepSide.Attempts,
      failures: StepSide.Failures,
      firstCallTokens: Option[StepSide.FirstCallTokens] = None,
      turns: Option[StepSide.Turns] = None
  )

  object StepSide:
    type Models = _root_.com.histmersion.domain.CompareRuns.Step.Model
    val Models: _root_.com.histmersion.domain.CompareRuns.Step.Model.type = _root_.com.histmersion.domain.CompareRuns.Step.Model
    type Actual = _root_.com.histmersion.domain.CompareRuns.Step.ActualModel
    val Actual: _root_.com.histmersion.domain.CompareRuns.Step.ActualModel.type = _root_.com.histmersion.domain.CompareRuns.Step.ActualModel
    type Billing = Billing.T
    object Billing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = _root_.com.histmersion.domain.CompareRuns.Step.CostUsd
    val CostUsd: _root_.com.histmersion.domain.CompareRuns.Step.CostUsd.type = _root_.com.histmersion.domain.CompareRuns.Step.CostUsd
    type Seconds = _root_.com.histmersion.domain.CompareRuns.Seconds
    val Seconds: _root_.com.histmersion.domain.CompareRuns.Seconds.type = _root_.com.histmersion.domain.CompareRuns.Seconds
    type Attempts = _root_.com.histmersion.domain.CompareRuns.RunSide.Attempts
    val Attempts: _root_.com.histmersion.domain.CompareRuns.RunSide.Attempts.type = _root_.com.histmersion.domain.CompareRuns.RunSide.Attempts
    type Failures = _root_.com.histmersion.domain.CompareRuns.RunSide.Failures
    val Failures: _root_.com.histmersion.domain.CompareRuns.RunSide.Failures.type = _root_.com.histmersion.domain.CompareRuns.RunSide.Failures
    type FirstCallTokens = _root_.com.histmersion.domain.CompareRuns.Step.FirstCallTokens
    val FirstCallTokens: _root_.com.histmersion.domain.CompareRuns.Step.FirstCallTokens.type = _root_.com.histmersion.domain.CompareRuns.Step.FirstCallTokens
    type Turns = _root_.com.histmersion.domain.CompareRuns.Step.Turns
    val Turns: _root_.com.histmersion.domain.CompareRuns.Step.Turns.type = _root_.com.histmersion.domain.CompareRuns.Step.Turns

  /** One step name, and that step in each run (None = the run has no such step). */
  final case class StepRow(name: StepRow.Name, runs: List[Option[StepSide]])

  object StepRow:
    type Name = _root_.com.histmersion.domain.CompareRuns.Step.Name
    val Name: _root_.com.histmersion.domain.CompareRuns.Step.Name.type = _root_.com.histmersion.domain.CompareRuns.Step.Name

  /** enrich-source's outputs; the period is the classify step's, in signed years. */
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
  )

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

  object ReviewerSide:
    type Reviewer = Reviewer.T
    object Reviewer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Verdict = Verdict.T
    object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Majors = Majors.T
    object Majors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Minors = Minors.T
    object Minors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class KindCount(kind: KindCount.Kind, count: KindCount.Count)

  object KindCount:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** review-article's outputs: the last round's verdict, the rounds, round 1 per reviewer and by kind. */
  final case class ReviewSide(
      verdict: Option[ReviewSide.Verdict],
      rounds: ReviewSide.Rounds,
      reviewers: List[ReviewerSide],
      kinds: List[KindCount],
      majors: List[ReviewSide.Majors]
  )

  object ReviewSide:
    type Verdict = Verdict.T
    object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rounds = Rounds.T
    object Rounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Majors = Majors.T
    object Majors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Round 1's major findings two runs share; `pairs` shows each match as `<a's id> ↔ <b's id>`. */
  final case class MajorOverlap(a: MajorOverlap.A, b: MajorOverlap.B, aMajors: MajorOverlap.AMajors, bMajors: MajorOverlap.BMajors, shared: MajorOverlap.Shared, pairs: List[MajorOverlap.Pairs])

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
      enrich: List[EnrichSide],
      review: List[ReviewSide],
      overlaps: List[MajorOverlap],
      notes: List[Comparison.Notes]
  )

  object Comparison:
    type Pipeline = _root_.com.histmersion.domain.CompareRuns.RunHead.Pipeline
    val Pipeline: _root_.com.histmersion.domain.CompareRuns.RunHead.Pipeline.type = _root_.com.histmersion.domain.CompareRuns.RunHead.Pipeline
    type Target = _root_.com.histmersion.domain.CompareRuns.RunHead.Target
    val Target: _root_.com.histmersion.domain.CompareRuns.RunHead.Target.type = _root_.com.histmersion.domain.CompareRuns.RunHead.Target
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── comparing ────────────────────────────────────────────────────────────────────────────────

  /** `outputs` compares what the runs' workflow produced (its owner's, [[OutputComparison]]). */
  def compare(
      runs: List[Run],
      billing: com.histmersion.domain.CompareRuns.Step.Model => Option[com.histmersion.domain.CompareRuns.StepSide.Billing],
      outputs: OutputComparison
  ): Either[com.histmersion.domain.CompareRuns.Problem, Comparison] =
    for
      _ <- Either.cond(runs.size >= 2, (), Problem("give at least two runs to compare"))
      _ <- Either.cond(runs.map(_.run.id).distinct.size == runs.size, (), Problem("a run is given twice"))
      first = runs.head.run
      _ <- runs
        .find(_.run.pipeline != first.pipeline)
        .map(o =>
          Problem(s"run ${o.run.id.short} is ${o.run.pipeline}, run ${first.id.short} ${first.pipeline}: compare runs of one pipeline")
        )
        .toLeft(())
      _ <- runs
        .find(_.run.target != first.target)
        .map(o =>
          Problem(s"run ${o.run.id.short} is on ${o.run.target}, run ${first.id.short} on ${first.target}: compare runs on one target")
        )
        .toLeft(())
    yield
      val names = unionInOrder(runs.map(_.steps.map(_.name).distinct))
      val rows = names.map(n =>
        StepRow(n, runs.map(d => Option(d.steps.filter(_.name == n)).filter(_.nonEmpty).map(stepSide(_, billing))))
      )
      val notes = runs.collect {
        case d if d.run.status != com.histmersion.domain.automation.Run.State.Done.wire =>
          Comparison.Notes(s"run ${d.run.id.short} is ${d.run.status}, not done: its outputs are what it had so far")
      }
      val enrich = outputs.enrich(runs)
      val review = outputs.review(runs)
      val overlaps = outputs.overlaps(runs)
      Comparison(first.pipeline, first.target, runs.map(runSide), rows, enrich, review, overlaps, notes)

  // ── steps and totals ───────────────────────────────────────────────────────────────────────────

  /** Every list's names in one order: the first list's, each later name placed after the name it follows in its own
    * list (a run with more rounds or windows slots its extra steps in).
    */
  def unionInOrder(lists: List[List[com.histmersion.domain.CompareRuns.Step.Name]]): List[com.histmersion.domain.CompareRuns.Step.Name] =
    lists
      .foldLeft(Vector.empty[Step.Name]) { (acc, names) =>
        @tailrec def place(rest: List[com.histmersion.domain.CompareRuns.Step.Name], a: Vector[com.histmersion.domain.CompareRuns.Step.Name], pos: Int): Vector[com.histmersion.domain.CompareRuns.Step.Name] =
          rest match
            case Nil       => a
            case n :: tail =>
              a.indexOf(n) match
                case -1 => place(tail, a.patch(pos + 1, List(n), 0), pos + 1)
                case i  => place(tail, a, i)
        place(names, acc, -1)
      }
      .toList

  /** A step's duration in whole seconds (the store keeps seconds); 0 when a time is unreadable. */
  def seconds(s: Step): com.histmersion.domain.CompareRuns.Seconds =
    Seconds(
      (for
        a <- scala.util.Try(java.time.Instant.parse(s.startedAt.value)).toOption
        b <- scala.util.Try(java.time.Instant.parse(s.finishedAt.value)).toOption
      yield math.max(0L, b.getEpochSecond - a.getEpochSecond)).getOrElse(0L)
    )

  private def costOf(steps: List[Step]): Step.CostUsd = Step.CostUsd(steps.foldLeft(0.0)((acc, s) => acc + s.costUsd.value))

  private def secondsOf(steps: List[Step]): Seconds = Seconds(steps.foldLeft(0L)((acc, s) => acc + seconds(s).value))

  private def failedIn(steps: List[Step]): Int = steps.count(_.status == com.histmersion.domain.automation.Step.Status.Failed.wire)

  private def sum(us: List[Tokens]): Tokens = us.foldLeft(Tokens.Zero)(_ + _)

  def stepSide(attempts: List[Step], billing: com.histmersion.domain.CompareRuns.Step.Model => Option[com.histmersion.domain.CompareRuns.StepSide.Billing]): StepSide =
    val models = attempts.flatMap(_.model).distinct
    StepSide(
      models,
      attempts.flatMap(_.actualModel).distinct,
      models.flatMap(billing).distinct,
      sum(attempts.map(_.usage)),
      costOf(attempts),
      secondsOf(attempts),
      StepSide.Attempts(attempts.size),
      StepSide.Failures(failedIn(attempts)),
      attempts.flatMap(_.firstCallTokens).headOption,
      Option.when(attempts.exists(_.turns.isDefined))(StepSide.Turns(attempts.flatMap(_.turns).foldLeft(0)((acc, t) => acc + t.value)))
    )

  private def runSide(d: Run): RunSide =
    val r = d.run
    RunSide(
      r.id,
      r.status,
      r.dryRun,
      r.model,
      r.modelFor,
      sum(d.steps.map(_.usage)),
      costOf(d.steps),
      secondsOf(d.steps),
      RunSide.Attempts(d.steps.size),
      RunSide.Failures(failedIn(d.steps))
    )

  /** The checkpoint of step `name`: its last finished attempt's output, decoded. */
  def output[O: Decoder](d: Run, name: com.histmersion.domain.CompareRuns.OutputName): Option[O] =
    d.steps
      .filter(s => s.name == name && s.status == com.histmersion.domain.automation.Step.Status.Done.wire)
      .lastOption
      .flatMap(_.output)
      .flatMap(o => decode[O](o.value).toOption)

  private def words(s: String): Set[String] =
    ComparedText.normalise(ComparedText.NormaliseS(Option(s).getOrElse(""))).value.split("[^\\p{L}\\p{N}]+").filter(_.length > 2).toSet

  /** Jaccard similarity of two texts' words (0 when both have none). */
  def similarity(a: com.histmersion.domain.CompareRuns.Text, b: com.histmersion.domain.CompareRuns.Text): com.histmersion.domain.CompareRuns.Similarity =
    val (x, y) = (words(a.value), words(b.value))
    Similarity(if x.isEmpty && y.isEmpty then 0.0 else x.intersect(y).size.toDouble / x.union(y).size)
