package com.histmersion
package api
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.library.Confidence
import com.histmersion.api.exchange.EditionsPolicy
import com.histmersion.api.library.MatchMethod
import com.histmersion.api.library.MatchOutcome
import com.histmersion.api.research.DraftMode
import com.histmersion.api.automation.ScheduleKind
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Every file of another module that derives a codec or schema imports `com.histmersion.api.VocabularyCodecs.given`:
  * without it Kindlings derives its own instance for a vocabulary field, spelled differently (an enum with parameters
  * as an object), and nothing fails to compile.
  */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@atBoundary(BoundaryKind.Json, "gives each vocabulary its JSON encoder, decoder and schema")
@movesTo(FeaturePlace.UtilCommon)
object VocabularyCodecs:
  given given_JsonValueCodec_SourceStatus: JsonValueCodec[Source.Status] = WireEnum.codec
  given given_Schema_SourceStatus: Schema[Source.Status] = WireEnum.schema

  given JsonValueCodec[Confidence] = WireEnum.codec
  given Schema[Confidence] = WireEnum.schema

  given given_JsonValueCodec_EdgeKind: JsonValueCodec[Edge.Kind] = WireEnum.codec
  given given_Schema_EdgeKind: Schema[Edge.Kind] = WireEnum.schema

  given given_JsonValueCodec_PlacePrecision: JsonValueCodec[Place.Precision] = WireEnum.codec
  given given_Schema_PlacePrecision: Schema[Place.Precision] = WireEnum.schema

  given given_JsonValueCodec_IssueSeverity: JsonValueCodec[MergeIssue.Severity] = WireEnum.codec
  given given_Schema_IssueSeverity: Schema[MergeIssue.Severity] = WireEnum.schema

  given given_JsonValueCodec_IssueKind: JsonValueCodec[MergeIssue.Kind] = WireEnum.codec
  given given_Schema_IssueKind: Schema[MergeIssue.Kind] = WireEnum.schema

  given JsonValueCodec[MergeDecision.Kind] = WireEnum.codec
  given Schema[MergeDecision.Kind] = WireEnum.schema

  given JsonValueCodec[MatchOutcome] = WireEnum.codec
  given Schema[MatchOutcome] = WireEnum.schema

  given JsonValueCodec[EditionsPolicy] = WireEnum.codec
  given Schema[EditionsPolicy] = WireEnum.schema

  given given_JsonValueCodec_BundleKind: JsonValueCodec[Bundle.Kind] = WireEnum.codec
  given given_Schema_BundleKind: Schema[Bundle.Kind] = WireEnum.schema

  given given_JsonValueCodec_IssueDecidedBy: JsonValueCodec[MergeIssue.DecidedBy] = WireEnum.codec
  given given_Schema_IssueDecidedBy: Schema[MergeIssue.DecidedBy] = WireEnum.schema

  given JsonValueCodec[MatchMethod] = WireEnum.codec
  given Schema[MatchMethod] = WireEnum.schema

  given given_JsonValueCodec_ApprovalKind: JsonValueCodec[Approval.Kind] = WireEnum.codec
  given given_Schema_ApprovalKind: Schema[Approval.Kind] = WireEnum.schema

  given JsonValueCodec[DraftMode] = WireEnum.codec
  given Schema[DraftMode] = WireEnum.schema

  given given_JsonValueCodec_GoalLinkKind: JsonValueCodec[Goal.LinkKind] = WireEnum.codec
  given given_Schema_GoalLinkKind: Schema[Goal.LinkKind] = WireEnum.schema

  given JsonValueCodec[ScheduleKind] = WireEnum.codec
  given Schema[ScheduleKind] = WireEnum.schema

export VocabularyCodecs.given
