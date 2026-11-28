package com.histmersion
package domain
import com.histmersion.util.json.RawJson
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw}
import scala.annotation.tailrec
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.util.text.ComparedText
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** `agent compare` (#992): the pure half. Reads two or more runs' recorded steps (their FULL outputs) and lays them
  * side by side; `billing` names how a configured model is paid for (a plan pool or a price — the server knows, from
  * its config). Nothing is judged: counts, tokens, times, and what one run produced that the others did not.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.AutomationAgents)
object CompareRuns:
  type Similarity = Similarity.T
  object Similarity extends Newtype[Double, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  // ── what is compared: the recorded runs ──────────────────────────────────────────────────────

  /** Tokens, as a provider reports them; `input` includes the cached ones. */

  type Seconds = Seconds.T
  object Seconds extends Newtype[Long, Pure]
  export Step.Name as OutputName

  final case class Tokens(
      input: Tokens.Input = Tokens.Input(0),
      output: Tokens.Output = Tokens.Output(0),
      cacheRead: Tokens.CacheRead = Tokens.CacheRead(0),
      cacheWrite: Tokens.CacheWrite = Tokens.CacheWrite(0)
  ):
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def +(o: Tokens): Tokens =
      Tokens(
        Tokens.Input(input.value + o.input.value),
        Tokens.Output(output.value + o.output.value),
        Tokens.CacheRead(cacheRead.value + o.cacheRead.value),
        Tokens.CacheWrite(cacheWrite.value + o.cacheWrite.value)
      )

  object Tokens:
    type Input = Input.T
    object Input extends Newtype[Long, Pure]
    type Output = Output.T
    object Output extends Newtype[Long, Pure]
    type CacheRead = CacheRead.T
    object CacheRead extends Newtype[Long, Pure]
    type CacheWrite = CacheWrite.T
    object CacheWrite extends Newtype[Long, Pure]

    val Zero: Tokens = Tokens()

  /** A role's model, as a run was told to use it. */
  final case class ModelChoice(role: ModelChoice.Role, model: ModelChoice.Model)

  object ModelChoice:
    type Role = Role.T
    object Role extends Newtype[String, Pure]
    type Model = Model.T
    object Model extends Newtype[String, Pure]

  /** A run as compared: what it ran on, its state, and the models it was given. */
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    object Id extends Newtype[String, Pure]:
      extension (id: Id) def short: String = id.value.take(8)
    type Pipeline = Pipeline.T
    object Pipeline extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    export automation.Run.State.Wire as Status
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    type Model = Model.T
    object Model extends Newtype[String, Pure]

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
      error: Option[Problem]
  )

  object Step:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export automation.Step.Status.Wire as Status
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends Newtype[String, Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends Newtype[Double, Pure]
    type StartedAt = StartedAt.T
    object StartedAt extends Newtype[String, Pure]
    type FinishedAt = FinishedAt.T
    object FinishedAt extends Newtype[String, Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends Newtype[Long, Pure]
    type Turns = Turns.T
    object Turns extends Newtype[Int, Pure]
    type Output = Output.T
    object Output extends Newtype[String, Pure]

  final case class Run(run: RunHead, steps: List[Step])

  // ── what a comparison says ───────────────────────────────────────────────────────────────────

  /** One run, in total. `seconds` = the sum of its steps' durations. */
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    export RunHead.Id
    export automation.Run.State.Wire as Status
    export RunHead.{DryRun, Model}
    export Step.CostUsd
    export CompareRuns.Seconds
    type Attempts = Attempts.T
    object Attempts extends Newtype[Int, Pure]
    type Failures = Failures.T
    object Failures extends Newtype[Int, Pure]

  /** One step of one run, every attempt summed. `billing` names how each configured model is paid for. */
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    export Step.{ActualModel as Actual, Model as Models}
    type Billing = Billing.T
    object Billing extends Newtype[String, Pure]
    export Step.CostUsd
    export CompareRuns.Seconds
    export RunSide.{Attempts, Failures}
    export Step.{FirstCallTokens, Turns}

  /** One step name, and that step in each run (None = the run has no such step). */
  @tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
  final case class StepRow(name: StepRow.Name, runs: List[Option[StepSide]])

  object StepRow:
    export Step.Name

  /** enrich-source's outputs; the period is the classify step's, in signed years. */
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
  )

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
  )

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

  object KindCount:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

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
    object Verdict extends Newtype[String, Pure]
    type Rounds = Rounds.T
    object Rounds extends Newtype[Int, Pure]
    type Majors = Majors.T
    object Majors extends Newtype[String, Pure]

  /** Round 1's major findings two runs share; `pairs` shows each match as `<a's id> ↔ <b's id>`. */
  final case class MajorOverlap(
      a: MajorOverlap.A,
      b: MajorOverlap.B,
      aMajors: MajorOverlap.AMajors,
      bMajors: MajorOverlap.BMajors,
      shared: MajorOverlap.Shared,
      pairs: List[MajorOverlap.Pairs]
  )

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
      enrich: List[EnrichSide],
      review: List[ReviewSide],
      overlaps: List[MajorOverlap],
      notes: List[Comparison.Notes]
  )

  object Comparison:
    export RunHead.{Pipeline, Target}
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]

  // ── comparing ────────────────────────────────────────────────────────────────────────────────

  /** `outputs` compares what the runs' workflow produced (its owner's, [[OutputComparison]]). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def compare(
      runs: List[Run],
      billing: Step.Model => Option[StepSide.Billing],
      outputs: OutputComparison
  ): Either[Problem, Comparison] =
    for
      _ <- Either.cond(runs.size >= 2, (), Invalid.Broken.here(Invalid.Rule.RunsCompared))
      _ <- Either.cond(
        runs.map(_.run.id).distinct.size == runs.size,
        (),
        Invalid.Broken.here(Invalid.Rule.RunRepeated)
      )
      first = runs.head.run
      _ <- runs
        .find(_.run.pipeline != first.pipeline)
        .map(o =>
          Invalid.Broken.here(
            Invalid.Rule
              .RunsPipeline(Raw(o.run.id.short), Raw.of(o.run.pipeline), Raw(first.id.short), Raw.of(first.pipeline))
          )
        )
        .toLeft(())
      _ <- runs
        .find(_.run.target != first.target)
        .map(o =>
          Invalid.Broken.here(
            Invalid.Rule
              .RunsTarget(Raw(o.run.id.short), Raw.of(o.run.target), Raw(first.id.short), Raw.of(first.target))
          )
        )
        .toLeft(())
    yield
      val names = unionInOrder(runs.map(_.steps.map(_.name).distinct))
      val rows = names.map(n =>
        StepRow(n, runs.map(d => Option(d.steps.filter(_.name == n)).filter(_.nonEmpty).map(stepSide(_, billing))))
      )
      val notes = runs.collect {
        case d if d.run.status != automation.Run.State.Done.wire =>
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
  def unionInOrder(lists: List[List[Step.Name]]): List[Step.Name] =
    lists
      .foldLeft(Vector.empty[Step.Name]) { (acc, names) =>
        @tailrec def place(rest: List[Step.Name], a: Vector[Step.Name], pos: Int): Vector[Step.Name] =
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def seconds(s: Step): Seconds =
    Seconds(
      (for
        a <- scala.util.Try(java.time.Instant.parse(s.startedAt.value)).toOption
        b <- scala.util.Try(java.time.Instant.parse(s.finishedAt.value)).toOption
      yield math.max(0L, b.getEpochSecond - a.getEpochSecond)).getOrElse(0L)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def costOf(steps: List[Step]): Step.CostUsd =
    Step.CostUsd(steps.foldLeft(0.0)((acc, s) => acc + s.costUsd.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def secondsOf(steps: List[Step]): Seconds = Seconds(steps.foldLeft(0L)((acc, s) => acc + seconds(s).value))

  private def failedIn(steps: List[Step]): Int =
    steps.count(_.status == automation.Step.Status.Failed.wire)

  private def sum(us: List[Tokens]): Tokens = us.foldLeft(Tokens.Zero)(_ + _)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stepSide(attempts: List[Step], billing: Step.Model => Option[StepSide.Billing]): StepSide =
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
      Option.when(attempts.exists(_.turns.isDefined))(
        StepSide.Turns(attempts.flatMap(_.turns).foldLeft(0)((acc, t) => acc + t.value))
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def output[O: JsonValueCodec](d: Run, name: OutputName): Option[O] =
    d.steps
      .filter(s => s.name == name && s.status == automation.Step.Status.Done.wire)
      .lastOption
      .flatMap(_.output)
      .flatMap(o => RawJson.decode[O](o.value).toOption)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def words(s: String): Set[String] =
    ComparedText
      .normalise(ComparedText.NormaliseS(Option(s).getOrElse("")))
      .value
      .split("[^\\p{L}\\p{N}]+")
      .filter(_.length > 2)
      .toSet

  /** Jaccard similarity of two texts' words (0 when both have none). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def similarity(a: Text, b: Text): Similarity =
    val (x, y) = (words(a.value), words(b.value))
    Similarity(if x.isEmpty && y.isEmpty then 0.0 else x.intersect(y).size.toDouble / x.union(y).size)
