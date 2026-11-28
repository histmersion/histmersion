package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.reviewarticle.Verdict
import com.histmersion.domain.reviewarticle.Finding
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research
import com.histmersion.Newtype
import com.histmersion.domain.automation.Run
import com.histmersion.domain.reviewarticle.Reviewer
import com.histmersion.domain.reviewarticle.Merged

final case class Outcome(
    slug: Outcome.Slug,
    rounds: Outcome.Rounds,
    verdict: Verdict,
    open: List[Finding],
    applied: Outcome.Applied,
    rejected: Outcome.Rejected,
    fixed: Outcome.Fixed,
    approvals: Outcome.Approvals
) derives JsonCodec

object Outcome:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Slug = research.Slug
  val Slug: research.Slug.type = research.Slug
  type Rounds = Rounds.T
  object Rounds extends Newtype[Int, Pure]
  type Applied = Applied.T
  object Applied extends Newtype[Int, Pure]
  type Rejected = Rejected.T
  object Rejected extends Newtype[Int, Pure]
  type Fixed = Fixed.T
  object Fixed extends Newtype[Boolean, Pure]
  type Approvals = Approvals.T
  object Approvals extends Newtype[Int, Pure]

  extension (o: Outcome)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def logLine(last: Merged, runId: Run.Id): String =
      val counts = List(Reviewer.Rules, Reviewer.ColdReader, Reviewer.Skeptic, Reviewer.Mechanical)
        .map(r => s"${r.prefix}:${last.count(r)}")
        .mkString(" ")
      val fixer = if o.fixed.value then s", fixer applied ${o.applied} / rejected ${o.rejected}" else ""
      s"review-article [[${o.slug}]]: round ${o.rounds} → ${o.verdict.wire} ($counts)$fixer (agent run ${runId.value.take(8)})"
