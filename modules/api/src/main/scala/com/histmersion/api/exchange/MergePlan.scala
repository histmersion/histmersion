package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problem
import com.histmersion.lint.{tolerated, LintRule}

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
    problems: List[Problem],
    resolutionErrors: List[Problem],
    resolved: MergePlan.Resolved,
    changes: MergePlan.Changes = MergePlan.Changes(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def open: List[MergeIssue] = issues.filter((refactorCallback => ((refactorCallback.open) == (true))))
  def blocking: List[MergeIssue] = issues.filter(_.severity == MergeIssue.Severity.Blocking)
  def defaults: List[MergeIssue] = issues.filter(_.severity == MergeIssue.Severity.Default)
object MergePlan:
  export Bundle.Manifest.FormatVersion
  export Bundle.Manifest.SchemaVersion
  export Bundle.Manifest.AsOf
  type PlanHash = PlanHash.T
  object PlanHash extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Info = Info.T
  object Info extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Resolved = Resolved.T
  object Resolved extends Newtype[Int, Pure]
  type Changes = Changes.T
  object Changes extends Newtype[Int, Pure]

  final case class Request(bundle: Request.Bundle, resolutions: MergeResolutions = MergeResolutions())
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Request:
    export api.exchange.Bundle.Name as Bundle
