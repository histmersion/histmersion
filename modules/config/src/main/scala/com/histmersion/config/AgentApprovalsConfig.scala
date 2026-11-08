package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** What a run may change without a person: `manual` (the default) records an approval and waits, `auto` applies at
  * once. `statusChange` covers every status an agent proposes beyond filling `unassessed` with
  * `current`/`foundational`; `edges` covers the disagreement edges (challenges, refutes, updates, superseded_by).
  * Anything but `auto` is manual.
  */
final case class AgentApprovalsConfig(
    statusChange: AgentApprovalsConfig.Gate,
    edges: AgentApprovalsConfig.Gate,
    articlePublish: AgentApprovalsConfig.Gate,
    articleFilter: AgentApprovalsConfig.Gate = AgentApprovalsConfig.Gate("manual"),
    articlePlan: AgentApprovalsConfig.Gate = AgentApprovalsConfig.Gate("manual"),
    goalPlan: AgentApprovalsConfig.Gate = AgentApprovalsConfig.Gate("manual"),
    auto: AutoApprovalsConfig = AutoApprovalsConfig()
) derives ConfigReader

object AgentApprovalsConfig:
  type Gate = Gate.T
  object Gate extends Newtype[String, Pure]
  /** `auto` or `checked`, in any case. */


  def isChecked(raw: Gate): Boolean =
    val v = Option(raw).map(_.value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    v == "auto" || v == "checked"
