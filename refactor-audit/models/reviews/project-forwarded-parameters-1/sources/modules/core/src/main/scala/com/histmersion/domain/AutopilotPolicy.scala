package com.histmersion.domain

import com.histmersion.api.Plural

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.domain.automation.ItemStatus
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Manager

/** The pure half of the autopilot (docs/research-process-managers.md "As built: the autopilot"): the code checks that
  * decide an approval without a person, the rule that moves a research goal's status on from the facts, the managers
  * every goal must have, the spread of their crons, when an article is extended with new sources, and when the
  * housekeeping is due. No I/O: the supervisor reads the facts and applies the result.
  */
object AutopilotPolicy:

  // ── auto approvals ─────────────────────────────────────────────────────────────────────────────

  /** What the checks say about one open approval: approve it (why, for the note), reject it (why), or leave it for a
    * person (or for tomorrow, when only a daily cap stood in the way).
    */
  type DepthGoResult = DepthGoResult.T
  object DepthGoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthGoS = DepthGoS.T
  object DepthGoS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthGoSeen = DepthGoSeen.T
  object DepthGoSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthGoN = DepthGoN.T
  object DepthGoN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ArticlePlanMinSources = ArticlePlanMinSources.T
  object ArticlePlanMinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePlanApprovedToday = ArticlePlanApprovedToday.T
  object ArticlePlanApprovedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePlanCap = ArticlePlanCap.T
  object ArticlePlanCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePublishMinSources = ArticlePublishMinSources.T
  object ArticlePublishMinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePublishApprovedToday = ArticlePublishApprovedToday.T
  object ArticlePublishApprovedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePublishCap = ArticlePublishCap.T
  object ArticlePublishCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanMaxDepth = GoalPlanMaxDepth.T
  object GoalPlanMaxDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanApprovedToday = GoalPlanApprovedToday.T
  object GoalPlanApprovedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanCap = GoalPlanCap.T
  object GoalPlanCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanFocusResult = GoalPlanFocusResult.T
  object GoalPlanFocusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanFocusGoal = GoalPlanFocusGoal.T
  object GoalPlanFocusGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanFocusActive = GoalPlanFocusActive.T
  object GoalPlanFocusActive extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanFocusOpenSubGoals = GoalPlanFocusOpenSubGoals.T
  object GoalPlanFocusOpenSubGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanFocusProposed = GoalPlanFocusProposed.T
  object GoalPlanFocusProposed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanFocusLimit = GoalPlanFocusLimit.T
  object GoalPlanFocusLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanSubGoalsResult = GoalPlanSubGoalsResult.T
  object GoalPlanSubGoalsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanSubGoalsGoal = GoalPlanSubGoalsGoal.T
  object GoalPlanSubGoalsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanSubGoalsOpenSubGoals = GoalPlanSubGoalsOpenSubGoals.T
  object GoalPlanSubGoalsOpenSubGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanSubGoalsProposed = GoalPlanSubGoalsProposed.T
  object GoalPlanSubGoalsProposed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlanSubGoalsLimit = GoalPlanSubGoalsLimit.T
  object GoalPlanSubGoalsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfinishedPlannedResult = UnfinishedPlannedResult.T
  object UnfinishedPlannedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfinishedPlannedGoalsPart1 = UnfinishedPlannedGoalsPart1.T
  object UnfinishedPlannedGoalsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfinishedPlannedGoalsPart2 = UnfinishedPlannedGoalsPart2.T
  object UnfinishedPlannedGoalsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnfinishedPlannedPlanned = UnfinishedPlannedPlanned.T
  object UnfinishedPlannedPlanned extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlansWaitResult = GoalPlansWaitResult.T
  object GoalPlansWaitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlansWaitUnfinished = GoalPlansWaitUnfinished.T
  object GoalPlansWaitUnfinished extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlansWaitLimit = GoalPlansWaitLimit.T
  object GoalPlansWaitLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScaledCapResult = ScaledCapResult.T
  object ScaledCapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScaledCapBase = ScaledCapBase.T
  object ScaledCapBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScaledCapBacklog = ScaledCapBacklog.T
  object ScaledCapBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScaledCapShare = ScaledCapShare.T
  object ScaledCapShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScaledCapCeiling = ScaledCapCeiling.T
  object ScaledCapCeiling extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapTextResult = CapTextResult.T
  object CapTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CapTextBase = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapBase
  val CapTextBase: _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapBase.type = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapBase
  type CapTextBacklog = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapBacklog
  val CapTextBacklog: _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapBacklog.type = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapBacklog
  type CapTextShare = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapShare
  val CapTextShare: _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapShare.type = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapShare
  type CapTextCeiling = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapCeiling
  val CapTextCeiling: _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapCeiling.type = _root_.com.histmersion.domain.AutopilotPolicy.ScaledCapCeiling
  type ArticlesInProgressResult = ArticlesInProgressResult.T
  object ArticlesInProgressResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesInProgressUnreviewed = ArticlesInProgressUnreviewed.T
  object ArticlesInProgressUnreviewed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlesInProgressCompileOpen = ArticlesInProgressCompileOpen.T
  object ArticlesInProgressCompileOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextArticleResult = NextArticleResult.T
  object NextArticleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextArticleCooldownHours = NextArticleCooldownHours.T
  object NextArticleCooldownHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthTextResult = DepthTextResult.T
  object DepthTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthTextD = DepthTextD.T
  object DepthTextD extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotePrefix = NotePrefix.T
  object NotePrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResult = NoteResult.T
  object NoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthResult = DepthResult.T
  object DepthResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthSlug = DepthSlug.T
  object DepthSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthParentOfArg1 = DepthParentOfArg1.T
  object DepthParentOfArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthParentOfResult = DepthParentOfResult.T
  object DepthParentOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RankResult = RankResult.T
  object RankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RankS = RankS.T
  object RankS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AdvanceResultPart1 = AdvanceResultPart1.T
  object AdvanceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AdvanceResultPart2 = AdvanceResultPart2.T
  object AdvanceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishedResult = FinishedResult.T
  object FinishedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinishedStatus = FinishedStatus.T
  object FinishedStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnsurePausedByAutopilot = EnsurePausedByAutopilot.T
  object EnsurePausedByAutopilot extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPauseResult = ToPauseResult.T
  object ToPauseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpreadResult = SpreadResult.T
  object SpreadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpreadKey = SpreadKey.T
  object SpreadKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaggerResult = StaggerResult.T
  object StaggerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaggerBase = StaggerBase.T
  object StaggerBase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaggerKey = _root_.com.histmersion.domain.AutopilotPolicy.SpreadKey
  val StaggerKey: _root_.com.histmersion.domain.AutopilotPolicy.SpreadKey.type = _root_.com.histmersion.domain.AutopilotPolicy.SpreadKey
  type ExtendDueResult = ExtendDueResult.T
  object ExtendDueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendDueNewSources = ExtendDueNewSources.T
  object ExtendDueNewSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendDueMin = ExtendDueMin.T
  object ExtendDueMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendDueCooldownDays = ExtendDueCooldownDays.T
  object ExtendDueCooldownDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharedIngestLeftResult = SharedIngestLeftResult.T
  object SharedIngestLeftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharedIngestLeftCap = SharedIngestLeftCap.T
  object SharedIngestLeftCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharedIngestLeftIngestedToday = SharedIngestLeftIngestedToday.T
  object SharedIngestLeftIngestedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueResult = DueResult.T
  object DueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueEveryHours = DueEveryHours.T
  object DueEveryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtaDaysResult = EtaDaysResult.T
  object EtaDaysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtaDaysOpen = EtaDaysOpen.T
  object EtaDaysOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EtaDaysDoneLast7 = EtaDaysDoneLast7.T
  object EtaDaysDoneLast7 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Verdict:
    case Approve(reason: Verdict.Approve.Reason)
    case Reject(reason: Verdict.Reject.Reason)
    case Leave(why: Verdict.Leave.Why)

  object Verdict:
    object Approve:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Reject:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Leave:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One article of an `article-plan`, as it stands NOW: its slug still free, its filter's hits. */
  final case class PlannedFacts(slug: PlannedFacts.Slug, slugFree: PlannedFacts.SlugFree, hits: PlannedFacts.Hits)

  object PlannedFacts:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SlugFree = SlugFree.T
    object SlugFree extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hits = Hits.T
    object Hits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** An `article-plan` (propose-articles): approved when every article's slug is still free and its filter returns at
    * least `minSources`, and the area's planned articles approved today stay within `cap`; a plan larger than the cap
    * is a person's.
    */
  def articlePlan(articles: List[PlannedFacts], minSources: com.histmersion.domain.AutopilotPolicy.ArticlePlanMinSources, approvedToday: com.histmersion.domain.AutopilotPolicy.ArticlePlanApprovedToday, cap: com.histmersion.domain.AutopilotPolicy.ArticlePlanCap): Verdict =
    val n = articles.size
    val bad = articles.flatMap { a =>
      if !a.slugFree then Some(s"${a.slug}: the slug is taken now")
      else if a.hits < minSources then
        Some(s"${a.slug}: its filter returns ${a.hits} source(s), fewer than $minSources")
      else None
    }
    if n == 0 then Verdict.Reject("the plan names no article")
    else if bad.nonEmpty then Verdict.Reject(s"checks failed — ${bad.mkString("; ")}")
    else if n > cap then
      Verdict.Leave(s"${Plural.count(n, "article")} exceed the daily cap of $cap for one research area")
    else if approvedToday + n > cap then
      Verdict.Leave(
        s"the area's daily cap is reached ($approvedToday of $cap articles approved today); decided tomorrow"
      )
    else
      Verdict.Approve(
        s"${Plural.count(n, "article")}, every slug free and every filter at least $minSources sources " +
          s"(${articles.map(a => s"${a.slug} ${a.hits}").mkString(", ")}); ${approvedToday + n} of $cap today"
      )

  /** A draft to publish (compile-article), as code reads it. `hits` = what the run's filter returned (None: not known).
    */
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
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Create = Create.T
    object Create extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilterRecorded = FilterRecorded.T
    object FilterRecorded extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hits = Hits.T
    object Hits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cited = Cited.T
    object Cited extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SlugFree = SlugFree.T
    object SlugFree extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** An `article-publish`: approved when the run recorded a filter that returns at least `minSources` sources, the code
    * self-check left no problem, the text cites a source and (a new article) the slug is still free — within `cap`
    * drafts a day. A failed check rejects it (the seeding manager compiles again); a reached cap leaves it for
    * tomorrow.
    */
  def articlePublish(d: DraftFacts, minSources: com.histmersion.domain.AutopilotPolicy.ArticlePublishMinSources, approvedToday: com.histmersion.domain.AutopilotPolicy.ArticlePublishApprovedToday, cap: com.histmersion.domain.AutopilotPolicy.ArticlePublishCap): Verdict =
    if !d.filterRecorded then Verdict.Reject("the run records no coverage filter")
    else if d.hits.forall(_ < minSources) then
      Verdict.Reject(
        s"its filter returns ${d.hits.fold("an unknown number of")(_.toString)} source(s), fewer than $minSources"
      )
    else if d.problems.nonEmpty then
      Verdict.Reject(
        s"the self-check left ${Plural.count(d.problems.size, "problem")}: ${d.problems.take(3).mkString("; ").take(600)}"
      )
    else if d.cited == 0 then Verdict.Reject("the text cites no source")
    else if d.create && !d.slugFree then Verdict.Reject(s"the slug ${d.slug} is taken now")
    else if approvedToday >= cap then Verdict.Leave(s"the daily cap of $cap drafts is reached; decided tomorrow")
    else
      Verdict.Approve(
        s"filter ${d.hits.getOrElse(0)} sources (at least $minSources), ${Plural.count(d.cited, "source")} cited, " +
          s"no self-check problem; ${approvedToday + 1} of $cap today"
      )

  /** A goal's plan (plan-goal), as code reads it; `depth` = how far below a root goal it is (0 = a root). */
  final case class GoalPlanFacts(goal: GoalPlanFacts.Goal, subGoals: GoalPlanFacts.SubGoals, queries: GoalPlanFacts.Queries, works: GoalPlanFacts.Works, depth: GoalPlanFacts.Depth)

  object GoalPlanFacts:
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SubGoals = SubGoals.T
    object SubGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queries = Queries.T
    object Queries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Works = Works.T
    object Works extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A `goal-plan`: approved when it proposes something, and sub-goals only for a goal less than `maxDepth` deep —
    * within `cap` plans a day.
    */
  def goalPlan(p: GoalPlanFacts, maxDepth: com.histmersion.domain.AutopilotPolicy.GoalPlanMaxDepth, approvedToday: com.histmersion.domain.AutopilotPolicy.GoalPlanApprovedToday, cap: com.histmersion.domain.AutopilotPolicy.GoalPlanCap): Verdict =
    if p.subGoals + p.queries + p.works == 0 then Verdict.Reject("the plan proposes nothing")
    else if p.subGoals > 0 && p.depth >= maxDepth then
      Verdict.Reject(
        s"it proposes ${Plural.count(p.subGoals, "sub-goal")} for a goal ${depthText(p.depth)}; " +
          s"sub-goals stop at depth $maxDepth (the deepest sub-goal the checks approve)"
      )
    else if approvedToday >= cap then Verdict.Leave(s"the daily cap of $cap goal plans is reached; decided tomorrow")
    else
      Verdict.Approve(
        s"${Plural.count(p.subGoals, "sub-goal")}, ${Plural.count(p.queries, "query", "queries")}, ${Plural.count(p.works, "work")}" +
          s" (depth ${p.depth} of $maxDepth); ${approvedToday + 1} of $cap today"
      )

  /** A goal plan while the focus is on (docs/research-process-managers.md "As built: the focus"): decided by code only
    * when its goal is one of the active goals and its unfinished sub-goals (`openSubGoals`, every level below it) plus
    * the `proposed` new ones stay within `limit`; else why it waits for a person.
    */
  def goalPlanFocus(goal: com.histmersion.domain.AutopilotPolicy.GoalPlanFocusGoal, active: com.histmersion.domain.AutopilotPolicy.GoalPlanFocusActive, openSubGoals: com.histmersion.domain.AutopilotPolicy.GoalPlanFocusOpenSubGoals, proposed: com.histmersion.domain.AutopilotPolicy.GoalPlanFocusProposed, limit: com.histmersion.domain.AutopilotPolicy.GoalPlanFocusLimit): Option[com.histmersion.domain.AutopilotPolicy.GoalPlanFocusResult] =
    if !active then Some(s"focus: goal $goal is not one of the active goals; its plan waits for a person or its turn")
    else if proposed > 0 && openSubGoals + proposed > math.max(0, limit) then
      Some(
        s"focus: goal $goal has ${Plural.count(openSubGoals, "unfinished sub-goal")} and the plan adds $proposed; at most " +
          s"$limit are opened by code (the autopilot's limit on open sub-goals)"
      )
    else None

  /** A goal plan decided by code whatever the focus (#3762 proposal 2): only its sub-goals are bounded — its unfinished
    * sub-goals (`openSubGoals`, every level below it) plus the `proposed` new ones stay within `limit`
    * (`agents.autopilot.max-open-sub-goals`); else why it waits for a person. Queries and works alone are always within
    * it.
    */
  def goalPlanSubGoals(goal: com.histmersion.domain.AutopilotPolicy.GoalPlanSubGoalsGoal, openSubGoals: com.histmersion.domain.AutopilotPolicy.GoalPlanSubGoalsOpenSubGoals, proposed: com.histmersion.domain.AutopilotPolicy.GoalPlanSubGoalsProposed, limit: com.histmersion.domain.AutopilotPolicy.GoalPlanSubGoalsLimit): Option[com.histmersion.domain.AutopilotPolicy.GoalPlanSubGoalsResult] =
    Option.when(proposed > 0 && openSubGoals + proposed > math.max(0, limit))(
      s"goal $goal has ${Plural.count(openSubGoals, "unfinished sub-goal")} and the plan adds $proposed; at most " +
        s"$limit are opened by code (the autopilot's limit on open sub-goals)"
    )

  /** The goals with an approved plan that are not finished yet (neither reviewed nor done): what a new goal plan would
    * add to. `goals` = every goal's slug and status.
    */
  def unfinishedPlanned(goals: List[(com.histmersion.domain.AutopilotPolicy.UnfinishedPlannedGoalsPart1, com.histmersion.domain.AutopilotPolicy.UnfinishedPlannedGoalsPart2)], planned: Set[com.histmersion.domain.AutopilotPolicy.UnfinishedPlannedPlanned]): com.histmersion.domain.AutopilotPolicy.UnfinishedPlannedResult =
    goals.count((slug, status) => planned(slug) && !finished(status))

  /** Why goal plans are not offered now, if they are not: more goals with an approved plan are unfinished than `limit`
    * (0 = no limit). While so, no plan-goal run starts for an unplanned goal and code decides no open goal plan; a
    * person still may.
    */
  def goalPlansWait(unfinished: com.histmersion.domain.AutopilotPolicy.GoalPlansWaitUnfinished, limit: com.histmersion.domain.AutopilotPolicy.GoalPlansWaitLimit): Option[com.histmersion.domain.AutopilotPolicy.GoalPlansWaitResult] =
    Option.when(limit > 0 && unfinished > limit)(
      s"goal plans wait until the unfinished goals drop to $limit: ${Plural.count(unfinished, "goal")} with an approved " +
        s"plan ${if unfinished == 1 then "is" else "are"} not reviewed yet (the autopilot's limit on unfinished goals)"
    )

  /** A daily cap scaled to the backlog (#3762 proposal 3): `share` ≤ 0 keeps `base`; else
    * `max(base, ⌈backlog × share⌉)`, never above `ceiling` (when it is above 0) — and never below 0.
    */
  def scaledCap(base: com.histmersion.domain.AutopilotPolicy.ScaledCapBase, backlog: com.histmersion.domain.AutopilotPolicy.ScaledCapBacklog, share: com.histmersion.domain.AutopilotPolicy.ScaledCapShare, ceiling: com.histmersion.domain.AutopilotPolicy.ScaledCapCeiling): com.histmersion.domain.AutopilotPolicy.ScaledCapResult =
    val b = math.max(0, base)
    if share <= 0 then b
    else
      val scaled = math.max(b, math.ceil(math.max(0, backlog) * share).toInt)
      if ceiling > 0 then math.min(scaled, math.max(b, ceiling)) else scaled

  /** The cap as the status shows it: `5 a day`, or `5 a day, 22 today for a backlog of 64 (at most 50)`. */
  def capText(base: com.histmersion.domain.AutopilotPolicy.CapTextBase, backlog: com.histmersion.domain.AutopilotPolicy.CapTextBacklog, share: com.histmersion.domain.AutopilotPolicy.CapTextShare, ceiling: com.histmersion.domain.AutopilotPolicy.CapTextCeiling): com.histmersion.domain.AutopilotPolicy.CapTextResult =
    val n = scaledCap(base, backlog, share, ceiling)
    if share <= 0 then s"$base a day"
    else s"$base a day, $n today for a backlog of $backlog" + (if ceiling > 0 then s" (at most $ceiling)" else "")

  // ── the next article (#3763) ───────────────────────────────────────────────────────────────────

  /** What decides whether a goal's next article is proposed: its status, its linked articles and how many of them have
    * not passed review, its approved articles not compiled yet, its seeding manager's state, and its proposal item's
    * status and when it settled.
    */
  final case class NextFacts(
      goal: NextFacts.Goal,
      status: NextFacts.Status,
      articles: NextFacts.Articles,
      unreviewed: NextFacts.Unreviewed,
      compileOpen: NextFacts.CompileOpen,
      seedState: Option[NextFacts.SeedState],
      proposeStatus: Option[NextFacts.ProposeStatus],
      proposeSettled: Option[java.time.Instant]
  )

  object NextFacts:
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Articles = Articles.T
    object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unreviewed = Unreviewed.T
    object Unreviewed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CompileOpen = CompileOpen.T
    object CompileOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeedState = SeedState.T
    object SeedState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ProposeStatus = ProposeStatus.T
    object ProposeStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Why a goal's articles are still in progress — what keeps a new proposal back (#3763: no new work while the goal
    * still has unfinished articles); None when they are all done (or it has none).
    */
  def articlesInProgress(unreviewed: com.histmersion.domain.AutopilotPolicy.ArticlesInProgressUnreviewed, compileOpen: com.histmersion.domain.AutopilotPolicy.ArticlesInProgressCompileOpen): Option[com.histmersion.domain.AutopilotPolicy.ArticlesInProgressResult] =
    Option.when(unreviewed > 0 || compileOpen > 0)(
      "its articles in progress come first (" + List(
        Option.when(compileOpen > 0)(s"$compileOpen to compile"),
        Option.when(unreviewed > 0)(s"$unreviewed to pass review")
      ).flatten.mkString(", ") +
        "): a new proposal waits until they are published and reviewed"
    )

  /** Whether to propose a goal's NEXT article now (#3763), and why: the goal has articles, every one of them compiled
    * and past its review, its seeding manager is active, its proposal item is not open, and that proposal settled at
    * least `cooldownHours` ago (0 = never). A person's goal status `done` ends it. The proposal then goes through the
    * approvals policy like any other (`article-plan`).
    */
  def nextArticle(f: NextFacts, now: java.time.Instant, cooldownHours: com.histmersion.domain.AutopilotPolicy.NextArticleCooldownHours): Option[com.histmersion.domain.AutopilotPolicy.NextArticleResult] =
    val open = f.proposeStatus.exists(st => ItemStatus.parse(st).exists(!_.isFinal))
    val cooled = f.proposeSettled.forall(t => !t.plusSeconds(math.max(0, cooldownHours).toLong * 3600L).isAfter(now))
    Option.when(
      cooldownHours > 0 && f.status != Goal.Status.Done && f.articles > 0 &&
        articlesInProgress(f.unreviewed, f.compileOpen).isEmpty && f.seedState.contains(
          Manager.State.Active.wire
        ) && !open && cooled
    )(
      s"its ${Plural.count(f.articles, "article")} are compiled and passed review: its next article is proposed " +
        "(the plan goes through the article-plan approvals)"
    )

  private def depthText(d: com.histmersion.domain.AutopilotPolicy.DepthTextD): com.histmersion.domain.AutopilotPolicy.DepthTextResult = if d == 0 then "at the root" else s"$d level(s) below a root"

  /** The note an automatic decision carries — what marks it as the autopilot's (and what the daily caps count).
    */
  val NotePrefix: com.histmersion.domain.AutopilotPolicy.NotePrefix = Approval.DecidedBy.AutopilotNote
  def note(v: Verdict): Option[com.histmersion.domain.AutopilotPolicy.NoteResult] = v match
    case Verdict.Approve(r) => Some(s"$NotePrefix approved — $r")
    case Verdict.Reject(r)  => Some(s"$NotePrefix rejected — $r")
    case Verdict.Leave(_)   => None

  /** How deep a goal is: the number of parents above it (a cycle or a missing parent stops the walk). */
  def depth(slug: com.histmersion.domain.AutopilotPolicy.DepthSlug, parentOf: com.histmersion.domain.AutopilotPolicy.DepthParentOfArg1 => Option[com.histmersion.domain.AutopilotPolicy.DepthParentOfResult]): com.histmersion.domain.AutopilotPolicy.DepthResult =
    @annotation.tailrec
    def go(s: com.histmersion.domain.AutopilotPolicy.DepthGoS, seen: Set[com.histmersion.domain.AutopilotPolicy.DepthGoSeen], n: com.histmersion.domain.AutopilotPolicy.DepthGoN): com.histmersion.domain.AutopilotPolicy.DepthGoResult = parentOf(s) match
      case Some(p) if !seen(p) && n < 64 => go(p, seen + p, n + 1)
      case _                             => n
    go(slug, Set(slug), 0)

  // ── goal status ────────────────────────────────────────────────────────────────────────────────

  /** What a goal's status is moved on from. `managed` = it has an active seeding manager or watcher; `articles` = the
    * articles linked to it that exist; `compileOpen` = its seeding manager still has an article to compile (or a plan
    * or a proposal waiting for a decision); `reviewed` = every linked article has a review ledger with no open MAJOR
    * and no review item open; `children` = the statuses of its sub-goals.
    */
  final case class GoalFacts(
      slug: GoalFacts.Slug,
      status: GoalFacts.Status,
      managed: GoalFacts.Managed,
      articles: GoalFacts.Articles,
      compileOpen: GoalFacts.CompileOpen,
      reviewed: GoalFacts.Reviewed,
      children: List[GoalFacts.Children]
  )

  object GoalFacts:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Managed = Managed.T
    object Managed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Articles = Articles.T
    object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CompileOpen = CompileOpen.T
    object CompileOpen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reviewed = Reviewed.T
    object Reviewed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Children = Children.T
    object Children extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def rank(s: com.histmersion.domain.AutopilotPolicy.RankS): com.histmersion.domain.AutopilotPolicy.RankResult = Goal.Status.all.indexOf(s)

  /** The status the facts support, when it is FURTHER than the goal's own (never back; `done` and an unknown status are
    * a person's): seeding once it is managed; compiled once it has an article (a parent: once its sub-goals are
    * compiled) and nothing is left to compile; reviewed once every article passed its review and every sub-goal is
    * reviewed. With why, for the log.
    */
  def advance(f: GoalFacts): Option[(com.histmersion.domain.AutopilotPolicy.AdvanceResultPart1, com.histmersion.domain.AutopilotPolicy.AdvanceResultPart2)] =
    val cur = rank(f.status)
    if cur < 0 || f.status == Goal.Status.Done then None
    else
      val kids = f.children.map(rank)
      val hasOwn = f.articles > 0
      val compiled = (hasOwn || kids.nonEmpty) && !f.compileOpen && kids.forall(_ >= rank(Goal.Status.Compiled))
      val reviewed = compiled && (!hasOwn || f.reviewed) && kids.forall(_ >= rank(Goal.Status.Reviewed))
      val (target, why) =
        if reviewed then
          (
            Goal.Status.Reviewed,
            (if hasOwn then s"its ${Plural.count(f.articles, "article")} passed review" else "") +
              (if kids.nonEmpty then
                 s"${if hasOwn then ", " else ""}its ${Plural.count(kids.size, "sub-goal")} are reviewed"
               else "")
          )
        else if compiled then
          (
            Goal.Status.Compiled,
            (if hasOwn then s"${Plural.count(f.articles, "article")} compiled, none left to compile"
             else "nothing left to compile") +
              (if kids.nonEmpty then s", its ${Plural.count(kids.size, "sub-goal")} are compiled" else "")
          )
        else if f.managed then (Goal.Status.Seeding, "its seeding manager and watcher are active")
        else (f.status, "")
      Option.when(rank(target) > cur)((target, why))

  // ── the managers every goal has ────────────────────────────────────────────────────────────────

  /** A goal as the autopilot sees it: its status, and whether it still needs a plan (it records none and links no
    * query).
    */
  final case class GoalRow(slug: GoalRow.Slug, status: GoalRow.Status, needsPlan: GoalRow.NeedsPlan)

  object GoalRow:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NeedsPlan = NeedsPlan.T
    object NeedsPlan extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A manager as the autopilot sees it. `planItem` = it has a plan-goal item (any status). */
  final case class ManagerRow(
      id: ManagerRow.Id,
      kind: ManagerRow.Kind,
      goal: Option[ManagerRow.Goal],
      state: ManagerRow.State,
      cron: Option[ManagerRow.Cron],
      autoIngest: Option[ManagerRow.AutoIngest],
      planItem: ManagerRow.PlanItem = false,
      extendMinNew: Option[ManagerRow.ExtendMinNew] = None
  )

  object ManagerRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AutoIngest = AutoIngest.T
    object AutoIngest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanItem = PlanItem.T
    object PlanItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExtendMinNew = ExtendMinNew.T
    object ExtendMinNew extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The settings `ensure` works with. */
  final case class Settings(
      watchCron: Settings.WatchCron,
      seedCron: Settings.SeedCron,
      finishedWatchCron: Settings.FinishedWatchCron,
      watchIngestPerDay: Settings.WatchIngestPerDay,
      extendMinNew: Settings.ExtendMinNew
  )

  object Settings:
    type WatchCron = WatchCron.T
    object WatchCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeedCron = SeedCron.T
    object SeedCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishedWatchCron = FinishedWatchCron.T
    object FinishedWatchCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WatchIngestPerDay = WatchIngestPerDay.T
    object WatchIngestPerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExtendMinNew = ExtendMinNew.T
    object ExtendMinNew extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One change `ensure` asks for. */
  enum Step:
    /** Create the goal's seeding manager, active, with its cron (and a plan-goal item before its first proposal when
      * the goal has no plan).
      */
    case CreateSeed(goal: Step.CreateSeed.Goal, cron: Step.CreateSeed.Cron, planFirst: Step.CreateSeed.PlanFirst)

    /** Create the goal's watcher, active, auto-ingest on (at most `perDay` a day), with its cron. */
    case CreateWatch(goal: Step.CreateWatch.Goal, cron: Step.CreateWatch.Cron, perDay: Step.CreateWatch.PerDay)

    /** Resume a library-wide manager, or one the autopilot paused when it was turned off. */
    case Resume(id: Step.Resume.Id)

    /** Give a manager with no cron its spread cron. */
    case SetCron(id: Step.SetCron.Id, cron: Step.SetCron.Cron)

    /** Turn a watcher's auto-ingest on (it never said either way). */
    case AutoIngestOn(id: Step.AutoIngestOn.Id, perDay: Step.AutoIngestOn.PerDay)

    /** A plan-goal item first, for a goal that still needs a plan. */
    case AddPlanItem(id: Step.AddPlanItem.Id, goal: Step.AddPlanItem.Goal)

    /** The review manager's extend threshold (it sets none). */
    case ExtendMinNew(id: Step.ExtendMinNew.Id, n: Step.ExtendMinNew.N)

  object Step:
    object CreateSeed:
      type Goal = Goal.T
      object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Cron = Cron.T
      object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type PlanFirst = PlanFirst.T
      object PlanFirst extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object CreateWatch:
      type Goal = Goal.T
      object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Cron = Cron.T
      object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type PerDay = PerDay.T
      object PerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Resume:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object SetCron:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Cron = Cron.T
      object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object AutoIngestOn:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type PerDay = PerDay.T
      object PerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object AddPlanItem:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Goal = Goal.T
      object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object ExtendMinNew:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type N = N.T
      object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Finished: reviewed or done — such a goal keeps a watcher (new sources, the currency check), no new seeding
    * manager.
    */
  def finished(status: com.histmersion.domain.AutopilotPolicy.FinishedStatus): com.histmersion.domain.AutopilotPolicy.FinishedResult = status == Goal.Status.Reviewed || status == Goal.Status.Done

  /** What must change so every goal has its managers (idempotent: nothing, when they are there). Library-wide: `review`
    * and `enrich` active (`review` with the extend threshold). Every unfinished goal: a seeding manager (a plan-goal
    * item first when it has no plan) and a watcher with auto-ingest on; a finished goal: a watcher. A manager a person
    * paused stays paused — only the ones in `pausedByAutopilot` are resumed. A manager's settings a person chose are
    * kept (only an unset cron, auto-ingest or extend threshold is filled).
    */
  def ensure(
      goals: List[GoalRow],
      managers: List[ManagerRow],
      pausedByAutopilot: Set[com.histmersion.domain.AutopilotPolicy.EnsurePausedByAutopilot],
      s: Settings
  ): List[Step] =
    val byId = managers.map(m => m.id -> m).toMap
    val library = List(ReviewId, EnrichId).flatMap { id =>
      byId.get(id).toList.flatMap { m =>
        Option.when(m.state == Manager.State.Paused.wire)(Step.Resume(id)).toList ++
          Option
            .when(id == ReviewId && m.extendMinNew.isEmpty && s.extendMinNew > 0)(Step.ExtendMinNew(id, s.extendMinNew))
            .toList
      }
    }
    val perGoal = goals.sortBy(_.slug).flatMap { g =>
      val done = finished(g.status)
      val seed = byId.get(seedId(g.slug)) match
        case None if done => Nil
        case None         => List(Step.CreateSeed(g.slug, stagger(s.seedCron, seedId(g.slug)), g.needsPlan))
        case Some(m)      =>
          Option.when(pausedByAutopilot(m.id) && m.state == Manager.State.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(!done && m.cron.isEmpty)(Step.SetCron(m.id, stagger(s.seedCron, m.id))).toList ++
            Option
              .when(!done && g.needsPlan && !m.planItem && m.state != Manager.State.Stopped.wire)(
                Step.AddPlanItem(m.id, g.slug)
              )
              .toList
      val watchCron = if done then s.finishedWatchCron else s.watchCron
      val watch = byId.get(watchId(g.slug)) match
        case None    => List(Step.CreateWatch(g.slug, stagger(watchCron, watchId(g.slug)), s.watchIngestPerDay))
        case Some(m) =>
          Option.when(pausedByAutopilot(m.id) && m.state == Manager.State.Paused.wire)(Step.Resume(m.id)).toList ++
            Option.when(m.cron.isEmpty)(Step.SetCron(m.id, stagger(watchCron, m.id))).toList ++
            Option.when(m.autoIngest.isEmpty)(Step.AutoIngestOn(m.id, s.watchIngestPerDay)).toList
      seed ++ watch
    }
    library ++ perGoal

  /** The managers the autopilot pauses when it is turned off: the active seeding managers and watchers (the
    * library-wide ones and a person's paused ones are left as they are).
    */
  def toPause(managers: List[ManagerRow]): List[com.histmersion.domain.AutopilotPolicy.ToPauseResult] =
    managers
      .filter(m =>
        m.state == Manager.State.Active.wire && (m.kind == Manager.Kind.Seed.wire || m.kind == Manager.Kind.Watch.wire)
      )
      .map(_.id)

  // ── crons spread per goal ──────────────────────────────────────────────────────────────────────

  /** A stable non-negative hash of `key` (the same on every machine and every run). */
  def spread(key: com.histmersion.domain.AutopilotPolicy.SpreadKey): com.histmersion.domain.AutopilotPolicy.SpreadResult = key.foldLeft(17)((h, c) => 31 * h + c) & Int.MaxValue

  /** `@daily`, `@weekly` and `@monthly` as five fields at a minute, hour (and weekday or day of the month) of `key`'s
    * own, so dozens of managers with the same cron do not fire in the same minute; any other cron is kept as given.
    */
  def stagger(base: com.histmersion.domain.AutopilotPolicy.StaggerBase, key: com.histmersion.domain.AutopilotPolicy.StaggerKey): com.histmersion.domain.AutopilotPolicy.StaggerResult =
    val h = spread(key)
    val (min, hour, day) = (h % 60, (h / 60) % 24, h / 1440)
    base.trim.toLowerCase match
      case "@daily"   => s"$min $hour * * *"
      case "@weekly"  => s"$min $hour * * ${day % 7}"
      case "@monthly" => s"$min $hour ${1 + day % 28} * *"
      case _          => base.trim

  // ── new sources reaching an article ────────────────────────────────────────────────────────────

  /** Why an article is extended now, if it is: its filter returns at least `min` sources it neither cites nor gives a
    * reason for, and it was not extended in the last `cooldownDays`. `min` 0 = never.
    */
  def extendDue(
      newSources: com.histmersion.domain.AutopilotPolicy.ExtendDueNewSources,
      min: com.histmersion.domain.AutopilotPolicy.ExtendDueMin,
      lastExtend: Option[Instant],
      cooldownDays: com.histmersion.domain.AutopilotPolicy.ExtendDueCooldownDays,
      now: Instant
  ): Option[com.histmersion.domain.AutopilotPolicy.ExtendDueResult] =
    if min <= 0 || newSources < min then None
    else if lastExtend.exists(t => t.plus(Duration.ofDays(math.max(0, cooldownDays).toLong)).isAfter(now)) then None
    else Some(s"its filter returns ${Plural.count(newSources, "source")} it neither cites nor gives a reason for")

  // ── caps, housekeeping, throughput ─────────────────────────────────────────────────────────────

  /** What is left today of the ingests all watchers share (None: no shared cap). */
  def sharedIngestLeft(cap: com.histmersion.domain.AutopilotPolicy.SharedIngestLeftCap, ingestedToday: com.histmersion.domain.AutopilotPolicy.SharedIngestLeftIngestedToday): Option[com.histmersion.domain.AutopilotPolicy.SharedIngestLeftResult] =
    Option.when(cap > 0)(math.max(0, cap - math.max(0, ingestedToday)))

  /** Is a housekeeping job due: never run, or `everyHours` passed since (0 = never). */
  def due(last: Option[Instant], everyHours: com.histmersion.domain.AutopilotPolicy.DueEveryHours, now: Instant): com.histmersion.domain.AutopilotPolicy.DueResult =
    everyHours > 0 && last.forall(l => !l.plus(Duration.ofHours(everyHours.toLong)).isAfter(now))

  /** Days until `open` items are done at the pace of the last week (`doneLast7` items); None when nothing is open or
    * nothing was done.
    */
  def etaDays(open: com.histmersion.domain.AutopilotPolicy.EtaDaysOpen, doneLast7: com.histmersion.domain.AutopilotPolicy.EtaDaysDoneLast7): Option[com.histmersion.domain.AutopilotPolicy.EtaDaysResult] =
    Option.when(open > 0 && doneLast7 > 0)(math.max(1, math.ceil(open * 7.0 / doneLast7).toInt))
