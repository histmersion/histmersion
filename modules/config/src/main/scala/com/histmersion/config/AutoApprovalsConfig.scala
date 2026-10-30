package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The caps and the thresholds of the checked auto approvals ([[AgentApprovalsConfig.checked]]): at most
  * `articlesPerGoalPerDay` planned articles of one research area, `publishPerDay` article drafts and `goalPlansPerDay`
  * goal plans approved automatically per UTC day (what is over waits for a person, or the next day); a draft is
  * published only when its filter returns at least `publishMinSources` sources; a goal plan may propose sub-goals only
  * for a goal less than `maxGoalDepth` levels below a root goal (0 = a root).
  */
final case class AutoApprovalsConfig(
    articlesPerGoalPerDay: Int = 6,
    publishPerDay: Int = 10,
    goalPlansPerDay: Int = 5,
    publishMinSources: Int = 3,
    maxGoalDepth: Int = 2,
    /** The daily caps scaled to the backlog (#3762 proposal 3): with a share above 0 a day's cap is
      * `max(cap, ⌈open approvals of the kind × share⌉)`, at most the ceiling ([[AutopilotPolicy.scaledCap]]); 0 keeps
      * the fixed cap.
      */
    goalPlansBacklogShare: Double = 0,
    goalPlansCeiling: Int = 50,
    publishBacklogShare: Double = 0,
    publishCeiling: Int = 50
) derives ConfigReader
