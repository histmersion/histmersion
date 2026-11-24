package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** The caps and the thresholds of the checked auto approvals ([[AgentApprovalsConfig.checked]]): at most
  * `articlesPerGoalPerDay` planned articles of one research area, `publishPerDay` article drafts and `goalPlansPerDay`
  * goal plans approved automatically per UTC day (what is over waits for a person, or the next day); a draft is
  * published only when its filter returns at least `publishMinSources` sources; a goal plan may propose sub-goals only
  * for a goal less than `maxGoalDepth` levels below a root goal (0 = a root).
  */
@movesTo(FeaturePlace.AutomationApprovals)
final case class AutoApprovalsConfig(
    articlesPerGoalPerDay: AutoApprovalsConfig.ArticlesPerGoalPerDay = AutoApprovalsConfig.ArticlesPerGoalPerDay(6),
    publishPerDay: AutoApprovalsConfig.PublishPerDay = AutoApprovalsConfig.PublishPerDay(10),
    goalPlansPerDay: AutoApprovalsConfig.GoalPlansPerDay = AutoApprovalsConfig.GoalPlansPerDay(5),
    publishMinSources: AutoApprovalsConfig.PublishMinSources = AutoApprovalsConfig.PublishMinSources(3),
    maxGoalDepth: AutoApprovalsConfig.MaxGoalDepth = AutoApprovalsConfig.MaxGoalDepth(2),
    /** The daily caps scaled to the backlog (#3762 proposal 3): with a share above 0 a day's cap is
      * `max(cap, ⌈open approvals of the kind × share⌉)`, at most the ceiling ([[AutopilotPolicy.scaledCap]]); 0 keeps
      * the fixed cap.
      */
    goalPlansBacklogShare: AutoApprovalsConfig.GoalPlansBacklogShare = AutoApprovalsConfig.GoalPlansBacklogShare(0.0),
    goalPlansCeiling: AutoApprovalsConfig.GoalPlansCeiling = AutoApprovalsConfig.GoalPlansCeiling(50),
    publishBacklogShare: AutoApprovalsConfig.PublishBacklogShare = AutoApprovalsConfig.PublishBacklogShare(0.0),
    publishCeiling: AutoApprovalsConfig.PublishCeiling = AutoApprovalsConfig.PublishCeiling(50)
) derives ConfigReader

object AutoApprovalsConfig:
  type ArticlesPerGoalPerDay = ArticlesPerGoalPerDay.T
  object ArticlesPerGoalPerDay extends Newtype[Int, Pure]
  type PublishPerDay = PublishPerDay.T
  object PublishPerDay extends Newtype[Int, Pure]
  type GoalPlansPerDay = GoalPlansPerDay.T
  object GoalPlansPerDay extends Newtype[Int, Pure]
  type PublishMinSources = PublishMinSources.T
  object PublishMinSources extends Newtype[Int, Pure]
  type MaxGoalDepth = MaxGoalDepth.T
  object MaxGoalDepth extends Newtype[Int, Pure]
  type GoalPlansBacklogShare = GoalPlansBacklogShare.T
  object GoalPlansBacklogShare extends Newtype[Double, Pure]
  type GoalPlansCeiling = GoalPlansCeiling.T
  object GoalPlansCeiling extends Newtype[Int, Pure]
  type PublishBacklogShare = PublishBacklogShare.T
  object PublishBacklogShare extends Newtype[Double, Pure]
  type PublishCeiling = PublishCeiling.T
  object PublishCeiling extends Newtype[Int, Pure]
