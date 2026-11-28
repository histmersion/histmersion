package com.histmersion
package domain.automation.autopilot

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AutopilotPolicy.ApprovedToday
import com.histmersion.domain.AutopilotPolicy.Cap
import com.histmersion.domain.AutopilotPolicy.MinSources
import com.histmersion.domain.AutopilotPolicy.Rationale
import com.histmersion.domain.AutopilotPolicy.counted

final case class DraftFacts(
    slug: DraftFacts.Slug,
    create: DraftFacts.Create,
    filterRecorded: DraftFacts.FilterRecorded,
    hits: Option[DraftFacts.Hits],
    problems: List[DraftFacts.Problems],
    cited: DraftFacts.Cited,
    slugFree: DraftFacts.SlugFree
)

object DraftFacts:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Create = Create.T
  object Create extends Newtype[Boolean, Pure]
  type FilterRecorded = FilterRecorded.T
  object FilterRecorded extends Newtype[Boolean, Pure]
  type Hits = Hits.T
  object Hits extends Newtype[Int, Pure]
  type Problems = Problems.T
  object Problems extends Newtype[String, Pure]
  type Cited = Cited.T
  object Cited extends Newtype[Int, Pure]
  type SlugFree = SlugFree.T
  object SlugFree extends Newtype[Boolean, Pure]

  extension (d: DraftFacts)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def articlePublish(minSources: MinSources, approvedToday: ApprovedToday, cap: Cap): Ruling =
      if !d.filterRecorded.value then Ruling.Reject(Rationale("the run records no coverage filter"))
      else if d.hits.forall(_.value < minSources.value) then
        Ruling.Reject(
          Rationale(
            s"its filter returns ${d.hits.fold("an unknown number of")(_.toString)} source(s), fewer than $minSources"
          )
        )
      else if d.problems.nonEmpty then
        Ruling.Reject(
          Rationale(
            s"the self-check left ${counted(d.problems.size, "problem")}: ${d.problems.take(3).mkString("; ").take(600)}"
          )
        )
      else if d.cited.value == 0 then Ruling.Reject(Rationale("the text cites no source"))
      else if d.create.value && !d.slugFree.value then Ruling.Reject(Rationale(s"the slug ${d.slug} is taken now"))
      else if approvedToday.value >= cap.value then
        Ruling.Leave(Rationale(s"the daily cap of $cap drafts is reached; decided tomorrow"))
      else
        Ruling.Approve(
          Rationale(
            s"filter ${d.hits.getOrElse(0)} sources (at least $minSources), ${counted(d.cited.value, "source")} cited, " +
              s"no self-check problem; ${approvedToday.value + 1} of $cap today"
          )
        )
