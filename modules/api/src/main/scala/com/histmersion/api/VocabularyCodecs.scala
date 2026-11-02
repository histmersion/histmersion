package com.histmersion.api

import com.histmersion.domain.library.{Confidence, EditionsPolicy, MatchMethod, MatchOutcome}
import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.domain.research.DraftMode
import com.histmersion.domain.automation.ScheduleKind
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source
import com.histmersion.util.data.exchange.Bundle
import com.histmersion.util.data.exchange.Issue

/** Every file of another module that derives a codec or schema imports `com.histmersion.api.VocabularyCodecs.given`:
  * without it Kindlings derives its own instance for a vocabulary field, spelled differently (an enum with parameters
  * as an object), and nothing fails to compile.
  */
object VocabularyCodecs:
  given given_Encoder_SourceStatus: Encoder[Source.Status] = WireEnum.encoder
  given given_Decoder_SourceStatus: Decoder[Source.Status] = WireEnum.decoder
  given given_Schema_SourceStatus: Schema[Source.Status] = WireEnum.schema

  given Encoder[Confidence] = WireEnum.encoder
  given Decoder[Confidence] = WireEnum.decoder
  given Schema[Confidence] = WireEnum.schema

  given given_Encoder_EdgeKind: Encoder[Edge.Kind] = WireEnum.encoder
  given given_Decoder_EdgeKind: Decoder[Edge.Kind] = WireEnum.decoder
  given given_Schema_EdgeKind: Schema[Edge.Kind] = WireEnum.schema

  given given_Encoder_PlacePrecision: Encoder[Place.Precision] = WireEnum.encoder
  given given_Decoder_PlacePrecision: Decoder[Place.Precision] = WireEnum.decoder
  given given_Schema_PlacePrecision: Schema[Place.Precision] = WireEnum.schema

  given given_Encoder_IssueSeverity: Encoder[Issue.Severity] = WireEnum.encoder
  given given_Decoder_IssueSeverity: Decoder[Issue.Severity] = WireEnum.decoder
  given given_Schema_IssueSeverity: Schema[Issue.Severity] = WireEnum.schema

  given given_Encoder_IssueKind: Encoder[Issue.Kind] = WireEnum.encoder
  given given_Decoder_IssueKind: Decoder[Issue.Kind] = WireEnum.decoder
  given given_Schema_IssueKind: Schema[Issue.Kind] = WireEnum.schema

  given Encoder[DecisionKind] = WireEnum.encoder
  given Decoder[DecisionKind] = WireEnum.decoder
  given Schema[DecisionKind] = WireEnum.schema

  given Encoder[MatchOutcome] = WireEnum.encoder
  given Decoder[MatchOutcome] = WireEnum.decoder
  given Schema[MatchOutcome] = WireEnum.schema

  given Encoder[EditionsPolicy] = WireEnum.encoder
  given Decoder[EditionsPolicy] = WireEnum.decoder
  given Schema[EditionsPolicy] = WireEnum.schema

  given given_Encoder_BundleKind: Encoder[Bundle.Kind] = WireEnum.encoder
  given given_Decoder_BundleKind: Decoder[Bundle.Kind] = WireEnum.decoder
  given given_Schema_BundleKind: Schema[Bundle.Kind] = WireEnum.schema

  given given_Encoder_IssueDecidedBy: Encoder[Issue.DecidedBy] = WireEnum.encoder
  given given_Decoder_IssueDecidedBy: Decoder[Issue.DecidedBy] = WireEnum.decoder
  given given_Schema_IssueDecidedBy: Schema[Issue.DecidedBy] = WireEnum.schema

  given Encoder[MatchMethod] = WireEnum.encoder
  given Decoder[MatchMethod] = WireEnum.decoder
  given Schema[MatchMethod] = WireEnum.schema

  given given_Encoder_ApprovalKind: Encoder[Approval.Kind] = WireEnum.encoder
  given given_Decoder_ApprovalKind: Decoder[Approval.Kind] = WireEnum.decoder
  given given_Schema_ApprovalKind: Schema[Approval.Kind] = WireEnum.schema

  given Encoder[DraftMode] = WireEnum.encoder
  given Decoder[DraftMode] = WireEnum.decoder
  given Schema[DraftMode] = WireEnum.schema

  given given_Encoder_GoalLinkKind: Encoder[Goal.LinkKind] = WireEnum.encoder
  given given_Decoder_GoalLinkKind: Decoder[Goal.LinkKind] = WireEnum.decoder
  given given_Schema_GoalLinkKind: Schema[Goal.LinkKind] = WireEnum.schema

  given Encoder[ScheduleKind] = WireEnum.encoder
  given Decoder[ScheduleKind] = WireEnum.decoder
  given Schema[ScheduleKind] = WireEnum.schema

export VocabularyCodecs.given
