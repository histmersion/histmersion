package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** What a run may change without a person: `manual` (the default) records an approval and waits, `auto` applies at
  * once. `statusChange` covers every status an agent proposes beyond filling `unassessed` with
  * `current`/`foundational`; `edges` covers the disagreement edges (challenges, refutes, updates, superseded_by).
  * Anything but `auto` is manual.
  */
final case class AgentApprovalsConfig(
    statusChange: String,
    edges: String,
    articlePublish: String,
    articleFilter: String = "manual",
    articlePlan: String = "manual",
    goalPlan: String = "manual",
    auto: AutoApprovalsConfig = AutoApprovalsConfig()
) derives ConfigReader

object AgentApprovalsConfig:
  /** `auto` or `checked`, in any case. */
  def isChecked(raw: String): Boolean =
    val v = Option(raw).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    v == "auto" || v == "checked"
