package com.histmersion.api

import com.histmersion.values.{
  ApprovalKind,
  BundleKind,
  Confidence,
  DecidedBy,
  DecisionKind,
  DraftMode,
  EdgeKind,
  EditionsPolicy,
  GoalLinkKind,
  IssueKind,
  IssueSeverity,
  LocationPrecision,
  MatchMethod,
  MatchOutcome,
  ScheduleKind,
  SourceStatus
}
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/** The wire instances of the vocabularies `values` defines: each enum is its wire string ([[WireEnum]]), and its schema
  * a string with the enumeration of those strings. Exported to the package, so every api file finds them as it found
  * them on the enums' companions. Another module imports `com.histmersion.api.VocabularyCodecs.given` in every file
  * that derives a codec or schema: without it Kindlings derives one for a vocabulary field by itself, and spells the
  * value differently (an enum with parameters as an object), with nothing failing to compile.
  */
object VocabularyCodecs:
  given Encoder[SourceStatus] = WireEnum.encoder
  given Decoder[SourceStatus] = WireEnum.decoder
  given Schema[SourceStatus] = WireEnum.schema

  given Encoder[Confidence] = WireEnum.encoder
  given Decoder[Confidence] = WireEnum.decoder
  given Schema[Confidence] = WireEnum.schema

  given Encoder[EdgeKind] = WireEnum.encoder
  given Decoder[EdgeKind] = WireEnum.decoder
  given Schema[EdgeKind] = WireEnum.schema

  given Encoder[LocationPrecision] = WireEnum.encoder
  given Decoder[LocationPrecision] = WireEnum.decoder
  given Schema[LocationPrecision] = WireEnum.schema

  given Encoder[IssueSeverity] = WireEnum.encoder
  given Decoder[IssueSeverity] = WireEnum.decoder
  given Schema[IssueSeverity] = WireEnum.schema

  given Encoder[IssueKind] = WireEnum.encoder
  given Decoder[IssueKind] = WireEnum.decoder
  given Schema[IssueKind] = WireEnum.schema

  given Encoder[DecisionKind] = WireEnum.encoder
  given Decoder[DecisionKind] = WireEnum.decoder
  given Schema[DecisionKind] = WireEnum.schema

  given Encoder[MatchOutcome] = WireEnum.encoder
  given Decoder[MatchOutcome] = WireEnum.decoder
  given Schema[MatchOutcome] = WireEnum.schema

  given Encoder[EditionsPolicy] = WireEnum.encoder
  given Decoder[EditionsPolicy] = WireEnum.decoder
  given Schema[EditionsPolicy] = WireEnum.schema

  given Encoder[BundleKind] = WireEnum.encoder
  given Decoder[BundleKind] = WireEnum.decoder
  given Schema[BundleKind] = WireEnum.schema

  given Encoder[DecidedBy] = WireEnum.encoder
  given Decoder[DecidedBy] = WireEnum.decoder
  given Schema[DecidedBy] = WireEnum.schema

  given Encoder[MatchMethod] = WireEnum.encoder
  given Decoder[MatchMethod] = WireEnum.decoder
  given Schema[MatchMethod] = WireEnum.schema

  given Encoder[ApprovalKind] = WireEnum.encoder
  given Decoder[ApprovalKind] = WireEnum.decoder
  given Schema[ApprovalKind] = WireEnum.schema

  given Encoder[DraftMode] = WireEnum.encoder
  given Decoder[DraftMode] = WireEnum.decoder
  given Schema[DraftMode] = WireEnum.schema

  given Encoder[GoalLinkKind] = WireEnum.encoder
  given Decoder[GoalLinkKind] = WireEnum.decoder
  given Schema[GoalLinkKind] = WireEnum.schema

  given Encoder[ScheduleKind] = WireEnum.encoder
  given Decoder[ScheduleKind] = WireEnum.decoder
  given Schema[ScheduleKind] = WireEnum.schema

export VocabularyCodecs.given
