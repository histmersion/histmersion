package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.values.IssueSeverity
import com.histmersion.api.given

final case class MergePlan(
    bundleId: UUID,
    from: BundleInstance,
    into: BundleInstance,
    formatVersion: Int,
    schemaVersion: String,
    asOf: Option[String],
    planHash: String,
    matches: MatchCounts,
    sources: com.histmersion.api.exchange.PlanMatches,
    issues: List[MergeIssue],
    info: List[String],
    problems: List[String],
    resolutionErrors: List[String],
    resolved: Int,
    changes: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def open: List[MergeIssue] = issues.filter(_.open)
  def blocking: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Default)
object MergePlan:
  given Schema[MergePlan] = summon[KindlingsSchema[MergePlan]].schema
