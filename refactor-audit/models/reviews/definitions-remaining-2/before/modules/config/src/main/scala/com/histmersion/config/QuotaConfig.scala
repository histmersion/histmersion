package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Subscription pools (docs/research-plan-quotas.md §5): a snapshot older than `staleMinutes` is unknown; a window with
  * no floor of its own has `defaultFloor`; a stale reading lets a run go on only with `margin` points to spare;
  * `fallthrough` names the roles whose chain may fall from a plan below its floor to a paid key (the others pause).
  */
final case class QuotaConfig(
    staleMinutes: Int,
    defaultFloor: Int,
    margin: Int,
    pollSeconds: Int,
    claudePlan: ClaudePlanConfig,
    chatgptPlan: PlanPoolConfig,
    fallthrough: String
) derives ConfigReader
