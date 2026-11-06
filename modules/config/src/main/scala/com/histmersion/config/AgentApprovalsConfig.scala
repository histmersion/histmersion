package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** What a run may change without a person: `manual` (the default) records an approval and waits, `auto` applies at
  * once. `statusChange` covers every status an agent proposes beyond filling `unassessed` with
  * `current`/`foundational`; `edges` covers the disagreement edges (challenges, refutes, updates, superseded_by).
  * Anything but `auto` is manual.
  */
final case class AgentApprovalsConfig(
    statusChange: AgentApprovalsConfig.StatusChange,
    edges: AgentApprovalsConfig.Edges,
    articlePublish: AgentApprovalsConfig.ArticlePublish,
    articleFilter: AgentApprovalsConfig.ArticleFilter = "manual",
    articlePlan: AgentApprovalsConfig.ArticlePlan = "manual",
    goalPlan: AgentApprovalsConfig.GoalPlan = "manual",
    auto: AutoApprovalsConfig = AutoApprovalsConfig()
) derives ConfigReader

object AgentApprovalsConfig:
  type Gate = Gate.T
  object Gate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** `auto` or `checked`, in any case. */

  type StatusChange = StatusChange.T
  object StatusChange extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Edges = Edges.T
  object Edges extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePublish = ArticlePublish.T
  object ArticlePublish extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFilter = ArticleFilter.T
  object ArticleFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticlePlan = ArticlePlan.T
  object ArticlePlan extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalPlan = GoalPlan.T
  object GoalPlan extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def isChecked(raw: com.histmersion.config.AgentApprovalsConfig.Gate): Boolean =
    val v = Option(raw).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    v == "auto" || v == "checked"
