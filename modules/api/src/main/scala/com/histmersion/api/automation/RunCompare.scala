package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

object RunCompare:
  import Agents.{ModelOverride, Usage}

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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class StepRow(name: String, runs: List[Option[StepSide]]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


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
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class ReviewerSide(reviewer: String, verdict: Option[String], majors: Int, minors: Int)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class KindCount(kind: String, count: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class ReviewSide(
      verdict: Option[String],
      rounds: Int,
      reviewers: List[ReviewerSide],
      kinds: List[KindCount],
      majors: List[String]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class MajorOverlap(a: String, b: String, aMajors: Int, bMajors: Int, shared: Int, pairs: List[String])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class Comparison(
      pipeline: String,
      target: String,
      runs: List[RunSide],
      steps: List[StepRow],
      enrich: List[EnrichSide] = Nil,
      review: List[ReviewSide] = Nil,
      overlaps: List[MajorOverlap] = Nil,
      notes: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
