package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.values.IssueSeverity

/** The plan (§6.2): what an import would do and every issue, in one run. */
final case class MergePlan(
    bundleId: UUID,
    from: BundleInstance,
    into: BundleInstance,
    formatVersion: Int,
    schemaVersion: String,
    asOf: Option[String],
    /** A hash of the outcome — every match, every issue id and every decision in force. The same inputs give the same
      * hash; `merge apply` (M5) re-plans and compares it.
      */
    planHash: String,
    matches: MatchCounts,
    /** Every source of the bundle that is not simply the same record here. */
    sources: List[SourceMatch],
    issues: List[MergeIssue],
    /** Reported only: what will be carried along, dropped or derived. */
    info: List[String],
    /** What makes the bundle impossible to import whatever is decided (a missing blob, a newer schema). */
    problems: List[String],
    /** Resolutions that could not be used: an id not in this plan (stale — the records changed since — or never one),
      * or a decision the issue does not allow. Errors, never ignored.
      */
    resolutionErrors: List[String],
    /** How many resolutions were used. */
    resolved: Int,
    /** How many rows `merge apply` would write (insert, update, fold or delete) as decided now. After an apply,
      * planning the same bundle again gives 0.
      */
    changes: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def open: List[MergeIssue] = issues.filter(_.open)
  def blocking: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == IssueSeverity.Default)
object MergePlan:
  given Schema[MergePlan] = summon[KindlingsSchema[MergePlan]].schema
