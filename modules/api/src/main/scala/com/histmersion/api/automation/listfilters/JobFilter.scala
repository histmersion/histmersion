package com.histmersion
package api.automation.listfilters

import sttp.tapir.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.ListFilters.many
import com.histmersion.api.automation.ListFilters.OptionLabel
import com.histmersion.api.automation.ListFilters.single
import com.histmersion.api.automation.ListFilters.words
import com.histmersion.api.automation.ListFilters.Term
import com.histmersion.api.automation.ListFilters.Qualifier
import com.histmersion.api.automation.ListFilters.entry
import com.histmersion.api.jobs.JobState
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.Newtype

final case class JobFilter(
    status: List[JobFilter.Status] = Nil,
    kind: List[JobFilter.Kind] = Nil,
    since: Option[JobFilter.Since] = None,
    until: Option[JobFilter.Until] = None,
    subject: Option[JobFilter.Subject] = None
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def qualifier: Option[Qualifier] = {
    val n = normalized;
    words(
      (n.status).map(refactorValue14 => (refactorValue14).value),
      (n.kind).map(refactorValue15 => (refactorValue15).value)
    )
  }
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def normalized: JobFilter =
    JobFilter(
      many(status, true)(_.value, JobFilter.Status(_)),
      many(kind, true)(_.value, JobFilter.Kind(_)),
      single(since)(_.value, JobFilter.Since(_)),
      single(until)(_.value, JobFilter.Until(_)),
      single(subject)(_.value, JobFilter.Subject(_))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def options: List[(Term, Option[OptionLabel])] =
    val n = normalized
    n.status.map(v => entry("status", Some(v.value))) ++ n.kind.map(v => entry("kind", Some(v.value))) ++ List(
      entry("since", n.since.map(_.value)),
      entry("until", n.until.map(_.value)),
      entry("subject", n.subject.map(_.value))
    )

object JobFilter:

  export JobState.Wire as Status
  export JobKinds.Wire as Kind
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
