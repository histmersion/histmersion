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

  // ── what is compared: the recorded runs ──────────────────────────────────────────────────────

  /** Tokens, as a provider reports them; `input` includes the cached ones. */
  final case class Tokens(input: Long = 0, output: Long = 0, cacheRead: Long = 0, cacheWrite: Long = 0):
    def +(o: Tokens): Tokens =
      Tokens(input + o.input, output + o.output, cacheRead + o.cacheRead, cacheWrite + o.cacheWrite)

  object Tokens:
    val Zero: Tokens = Tokens()

  /** A role's model, as a run was told to use it. */
  final case class ModelChoice(role: String, model: String)

  /** A run as compared: what it ran on, its state, and the models it was given. */
  final case class RunHead(
      id: String,
      pipeline: String,
      target: String,
      status: String,
      dryRun: Boolean,
      model: Option[String],
      modelFor: List[ModelChoice]
  )

  /** One attempt of one step, with its full recorded output. `firstCallTokens` and `turns` are None where nothing was
    * recorded (a provider that cannot tell, or a run recorded before they were kept).
    */
  final case class Step(
      name: String,
      status: String,
      model: Option[String],
      actualModel: Option[String],
      usage: Tokens,
      costUsd: Double,
      startedAt: String,
      finishedAt: String,
      firstCallTokens: Option[Long],
      turns: Option[Int],
      output: Option[String],
      error: Option[String]
  )

  final case class Run(run: RunHead, steps: List[Step])

  // ── what a comparison says ───────────────────────────────────────────────────────────────────

  /** One run, in total. `seconds` = the sum of its steps' durations. */
  final case class RunSide(
      id: String,
      status: String,
      dryRun: Boolean,
      model: Option[String],
      modelFor: List[ModelChoice],
      usage: Tokens,
      costUsd: Double,
      seconds: Long,
      attempts: Int,
      failures: Int
  )

  /** One step of one run, every attempt summed. `billing` names how each configured model is paid for. */
  final case class StepSide(
      models: List[String],
      actual: List[String],
      billing: List[String],
      usage: Tokens,
      costUsd: Double,
      seconds: Long,
      attempts: Int,
      failures: Int,
      firstCallTokens: Option[Long] = None,
      turns: Option[Int] = None
  )

  /** One step name, and that step in each run (None = the run has no such step). */
  final case class StepRow(name: String, runs: List[Option[StepSide]])

  /** enrich-source's outputs; the period is the classify step's, in signed years. */
  final case class EnrichSide(
      findings: Int,
      quotesChecked: Int,
      quotesPassed: Int,
      placesAsked: Int,
      placesResolved: Int,
      datedFindings: Int,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      summaryChars: Int,
      notesChars: Int,
      uniqueClaims: List[String]
  )

  final case class ReviewerSide(reviewer: String, verdict: Option[String], majors: Int, minors: Int)

  final case class KindCount(kind: String, count: Int)

  /** review-article's outputs: the last round's verdict, the rounds, round 1 per reviewer and by kind. */
  final case class ReviewSide(
      verdict: Option[String],
      rounds: Int,
      reviewers: List[ReviewerSide],
      kinds: List[KindCount],
      majors: List[String]
  )

  /** Round 1's major findings two runs share; `pairs` shows each match as `<a's id> ↔ <b's id>`. */
  final case class MajorOverlap(a: String, b: String, aMajors: Int, bMajors: Int, shared: Int, pairs: List[String])

  final case class Comparison(
      pipeline: String,
      target: String,
      runs: List[RunSide],
      steps: List[StepRow],
      enrich: List[EnrichSide],
      review: List[ReviewSide],
      overlaps: List[MajorOverlap],
      notes: List[String]
  )

  // ── comparing ────────────────────────────────────────────────────────────────────────────────

  /** `outputs` compares what the runs' workflow produced (its owner's, [[OutputComparison]]). */
  def compare(
      runs: List[Run],
      billing: String => Option[String],
      outputs: OutputComparison
  ): Either[String, Comparison] =
    for
      _ <- Either.cond(runs.size >= 2, (), "give at least two runs to compare")
      _ <- Either.cond(runs.map(_.run.id).distinct.size == runs.size, (), "a run is given twice")
      first = runs.head.run
      _ <- runs
        .find(_.run.pipeline != first.pipeline)
        .map(o =>
          s"run ${o.run.id.take(8)} is ${o.run.pipeline}, run ${first.id.take(8)} ${first.pipeline}: compare runs of one pipeline"
        )
        .toLeft(())
      _ <- runs
        .find(_.run.target != first.target)
        .map(o =>
          s"run ${o.run.id.take(8)} is on ${o.run.target}, run ${first.id.take(8)} on ${first.target}: compare runs on one target"
        )
        .toLeft(())
    yield
      val names = unionInOrder(runs.map(_.steps.map(_.name).distinct))
      val rows = names.map(n =>
        StepRow(n, runs.map(d => Option(d.steps.filter(_.name == n)).filter(_.nonEmpty).map(stepSide(_, billing))))
      )
      val notes = runs.collect {
        case d if d.run.status != com.histmersion.domain.automation.Run.State.Done.wire =>
          s"run ${d.run.id.take(8)} is ${d.run.status}, not done: its outputs are what it had so far"
      }
      val enrich = outputs.enrich(runs)
      val review = outputs.review(runs)
      val overlaps = outputs.overlaps(runs)
      Comparison(first.pipeline, first.target, runs.map(runSide), rows, enrich, review, overlaps, notes)

  // ── steps and totals ───────────────────────────────────────────────────────────────────────────

  /** Every list's names in one order: the first list's, each later name placed after the name it follows in its own
    * list (a run with more rounds or windows slots its extra steps in).
    */
  def unionInOrder(lists: List[List[String]]): List[String] =
    lists
      .foldLeft(Vector.empty[String]) { (acc, names) =>
        @tailrec def place(rest: List[String], a: Vector[String], pos: Int): Vector[String] =
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
  def seconds(s: Step): Long =
    (for
      a <- scala.util.Try(java.time.Instant.parse(s.startedAt)).toOption
      b <- scala.util.Try(java.time.Instant.parse(s.finishedAt)).toOption
    yield math.max(0L, b.getEpochSecond - a.getEpochSecond)).getOrElse(0L)

  private def sum(us: List[Tokens]): Tokens = us.foldLeft(Tokens.Zero)(_ + _)

  def stepSide(attempts: List[Step], billing: String => Option[String]): StepSide =
    val models = attempts.flatMap(_.model).distinct
    StepSide(
      models,
      attempts.flatMap(_.actualModel).distinct,
      models.flatMap(billing).distinct,
      sum(attempts.map(_.usage)),
      attempts.map(_.costUsd).sum,
      attempts.map(seconds).sum,
      attempts.size,
      attempts.count(_.status == com.histmersion.domain.automation.Step.Status.Failed.wire),
      attempts.flatMap(_.firstCallTokens).headOption,
      Option.when(attempts.exists(_.turns.isDefined))(attempts.flatMap(_.turns).sum)
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
      d.steps.map(_.costUsd).sum,
      d.steps.map(seconds).sum,
      d.steps.size,
      d.steps.count(_.status == com.histmersion.domain.automation.Step.Status.Failed.wire)
    )

  /** The checkpoint of step `name`: its last finished attempt's output, decoded. */
  def output[O: Decoder](d: Run, name: String): Option[O] =
    d.steps
      .filter(s => s.name == name && s.status == com.histmersion.domain.automation.Step.Status.Done.wire)
      .lastOption
      .flatMap(_.output)
      .flatMap(decode[O](_).toOption)

  private def words(s: String): Set[String] =
    ComparedText.normalise(Option(s).getOrElse("")).split("[^\\p{L}\\p{N}]+").filter(_.length > 2).toSet

  /** Jaccard similarity of two texts' words (0 when both have none). */
  def similarity(a: String, b: String): Double =
    val (x, y) = (words(a), words(b))
    if x.isEmpty && y.isEmpty then 0.0 else (x intersect y).size.toDouble / (x union y).size
