package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `histmersion agent compare <run> <run> …` (#992): runs of ONE pipeline on ONE target, side by side — per step the
  * model (configured → what the provider reported), tokens, cost or plan pool, time and attempts, then the outputs in a
  * compact form per pipeline. Data, not judgement: nothing here says which run is better. The wire DTOs; the server
  * builds them ([[Agents]] runs in, code in core/jvm).
  */
object RunCompare:
  import Agents.{ModelOverride, Usage}

  /** One run, in total. `seconds` = the sum of its steps' durations. */
  final case class RunSide(
      id: String,
      status: String,
      dryRun: Boolean,
      model: Option[String],
      modelFor: List[ModelOverride],
      usage: Usage,
      costUsd: Double,
      seconds: Long,
      attempts: Int,
      failures: Int
  ) derives KindlingsCodecAsObject
  object RunSide:
    given Schema[RunSide] = KindlingsSchema.derived[RunSide].schema

  /** One step of one run, every attempt summed: the configured models it tried, what the providers reported serving,
    * how each was paid for (`plan pool: claude-plan`, a price), tokens, cost, time. `firstCallTokens`: the input of the
    * first model call of its first attempt that recorded one (the fixed per-turn part plus the step's prompt); `turns`:
    * the model turns of every attempt that recorded them, summed. None = not recorded (a provider that cannot tell, or
    * a run before V072).
    */
  final case class StepSide(
      models: List[String],
      actual: List[String],
      billing: List[String],
      usage: Usage,
      costUsd: Double,
      seconds: Long,
      attempts: Int,
      failures: Int,
      firstCallTokens: Option[Long] = None,
      turns: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object StepSide:
    given Schema[StepSide] = KindlingsSchema.derived[StepSide].schema

  /** One step name, and that step in each run (in the order the runs were given; None = the run has no such step — it
    * stopped earlier, or took fewer rounds).
    */
  final case class StepRow(name: String, runs: List[Option[StepSide]]) derives KindlingsCodecAsObject
  object StepRow:
    given Schema[StepRow] = KindlingsSchema.derived[StepRow].schema

  /** enrich-source's outputs. `quotesChecked`/`quotesPassed`: the verifier's quote check over every answer of every
    * findings step (a rejected answer's findings included); `uniqueClaims`: the claims no other compared run made
    * (normalised text). The period is the classify step's, in signed years.
    */
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
  ) derives KindlingsCodecAsObject
  object EnrichSide:
    given Schema[EnrichSide] = KindlingsSchema.derived[EnrichSide].schema

  /** One reviewer of round 1: its verdict and its findings by severity. */
  final case class ReviewerSide(reviewer: String, verdict: Option[String], majors: Int, minors: Int)
      derives KindlingsCodecAsObject
  object ReviewerSide:
    given Schema[ReviewerSide] = KindlingsSchema.derived[ReviewerSide].schema

  final case class KindCount(kind: String, count: Int) derives KindlingsCodecAsObject
  object KindCount:
    given Schema[KindCount] = KindlingsSchema.derived[KindCount].schema

  /** review-article's outputs: the last round's verdict and the number of rounds; round 1 (the one every run reviews on
    * the same text) per reviewer, by kind, and its major findings as lines.
    */
  final case class ReviewSide(
      verdict: Option[String],
      rounds: Int,
      reviewers: List[ReviewerSide],
      kinds: List[KindCount],
      majors: List[String]
  ) derives KindlingsCodecAsObject
  object ReviewSide:
    given Schema[ReviewSide] = KindlingsSchema.derived[ReviewSide].schema

  /** Round 1's major findings two runs share: matched on `where` and a similar problem. `pairs` shows each match as
    * `<a's id> ↔ <b's id>`.
    */
  final case class MajorOverlap(a: String, b: String, aMajors: Int, bMajors: Int, shared: Int, pairs: List[String])
      derives KindlingsCodecAsObject
  object MajorOverlap:
    given Schema[MajorOverlap] = KindlingsSchema.derived[MajorOverlap].schema

  /** The comparison. `enrich`/`review` hold one side per run (same order as `runs`) for those two pipelines, empty for
    * the others; `notes` say what could not be read.
    */
  final case class Comparison(
      pipeline: String,
      target: String,
      runs: List[RunSide],
      steps: List[StepRow],
      enrich: List[EnrichSide] = Nil,
      review: List[ReviewSide] = Nil,
      overlaps: List[MajorOverlap] = Nil,
      notes: List[String] = Nil
  ) derives KindlingsCodecAsObject
  object Comparison:
    given Schema[Comparison] = KindlingsSchema.derived[Comparison].schema
