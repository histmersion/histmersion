package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Goal

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class GoalProgress(
    slug: GoalProgress.Slug,
    title: GoalProgress.Title,
    status: GoalProgress.Status,
    parent: Option[GoalProgress.Parent] = None,
    managers: List[ManagerBrief] = Nil,
    sources: GoalProgress.Sources = GoalProgress.Sources(0),
    articles: GoalProgress.Articles = GoalProgress.Articles(0),
    openApprovals: GoalProgress.OpenApprovals = GoalProgress.OpenApprovals(0),
    lastActivity: Option[GoalProgress.LastActivity] = None,
    doneLast7: GoalProgress.DoneLast7 = GoalProgress.DoneLast7(0),
    open: GoalProgress.Open = GoalProgress.Open(0),
    etaDays: Option[GoalProgress.EtaDays] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object GoalProgress:
  export api.research.ReviewGoal.Slug
  export Goal.Title
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  export api.research.ReviewGoal.Slug as Parent
  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  type Articles = Articles.T
  object Articles extends Newtype[Int, Pure]
  type OpenApprovals = OpenApprovals.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object OpenApprovals extends Newtype[Int, Pure]
  type LastActivity = LastActivity.T
  object LastActivity extends Newtype[String, Pure]
  type DoneLast7 = DoneLast7.T
  object DoneLast7 extends Newtype[Int, Pure]
  type Open = Open.T
  object Open extends Newtype[Int, Pure]
  type EtaDays = EtaDays.T
  object EtaDays extends Newtype[Int, Pure]
