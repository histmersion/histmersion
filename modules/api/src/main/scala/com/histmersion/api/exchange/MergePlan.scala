package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class MergePlan(
    bundleId: UUID,
    from: Bundle.Instance,
    into: Bundle.Instance,
    formatVersion: MergePlan.FormatVersion,
    schemaVersion: MergePlan.SchemaVersion,
    asOf: Option[MergePlan.AsOf],
    planHash: MergePlan.PlanHash,
    matches: MatchCounts,
    sources: com.histmersion.api.exchange.PlanMatches,
    issues: List[MergeIssue],
    info: List[MergePlan.Info],
    problems: List[MergePlan.Problems],
    resolutionErrors: List[MergePlan.ResolutionErrors],
    resolved: MergePlan.Resolved,
    changes: MergePlan.Changes = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def open: List[MergeIssue] = issues.filter(_.open)
  def blocking: List[MergeIssue] = issues.filter(_.severity == MergeIssue.Severity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == MergeIssue.Severity.Default)
object MergePlan:


  type FormatVersion = FormatVersion.T
  object FormatVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchemaVersion = SchemaVersion.T
  object SchemaVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AsOf = AsOf.T
  object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanHash = PlanHash.T
  object PlanHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Info = Info.T
  object Info extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problems = Problems.T
  object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolutionErrors = ResolutionErrors.T
  object ResolutionErrors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Resolved = Resolved.T
  object Resolved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changes = Changes.T
  object Changes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Request(bundle: Request.Bundle, resolutions: MergeResolutions = MergeResolutions())
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type Bundle = Bundle.T
    object Bundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
