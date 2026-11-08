package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergePlan(
    bundleId: UUID,
    from: Bundle.Instance,
    into: Bundle.Instance,
    formatVersion: MergePlan.FormatVersion,
    schemaVersion: MergePlan.SchemaVersion,
    asOf: Option[MergePlan.AsOf],
    planHash: MergePlan.PlanHash,
    matches: MatchCounts,
    sources: PlanMatches,
    issues: List[MergeIssue],
    info: List[MergePlan.Info],
    problems: List[MergePlan.Problems],
    resolutionErrors: List[MergePlan.ResolutionErrors],
    resolved: MergePlan.Resolved,
    changes: MergePlan.Changes = MergePlan.Changes(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def open: List[MergeIssue] = issues.filter((refactorCallback => ((refactorCallback.open) == (true))))
  def blocking: List[MergeIssue] = issues.filter(_.severity == MergeIssue.Severity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == MergeIssue.Severity.Default)
object MergePlan:

  type FormatVersion = FormatVersion.T
  object FormatVersion extends Newtype[Int, Pure]
  type SchemaVersion = SchemaVersion.T
  object SchemaVersion extends Newtype[String, Pure]
  type AsOf = AsOf.T
  object AsOf extends Newtype[String, Pure]
  type PlanHash = PlanHash.T
  object PlanHash extends Newtype[String, Pure]
  type Info = Info.T
  object Info extends Newtype[String, Pure]
  type Problems = Problems.T
  object Problems extends Newtype[String, Pure]
  type ResolutionErrors = ResolutionErrors.T
  object ResolutionErrors extends Newtype[String, Pure]
  type Resolved = Resolved.T
  object Resolved extends Newtype[Int, Pure]
  type Changes = Changes.T
  object Changes extends Newtype[Int, Pure]

  final case class Request(bundle: Request.Bundle, resolutions: MergeResolutions = MergeResolutions())
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Request:
    export com.histmersion.api.exchange.Bundle.Name as Bundle
