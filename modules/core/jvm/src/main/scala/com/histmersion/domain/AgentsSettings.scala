package com.histmersion.domain

import com.histmersion.api.PlanQuota
import com.histmersion.config.{AgentApprovalsConfig, AgentProviderConfig, AgentsConfig}
import com.histmersion.values.{AgentRole, ApprovalKind, ApprovalMode, ProviderKind}

object ApprovalModes:
  /** A value that is not `auto` — a typo included — is the safe one. */
  def configured(raw: String): ApprovalMode = ApprovalMode.parse(raw).getOrElse(ApprovalMode.Manual)

extension (c: AgentApprovalsConfig)
  def statusMode: ApprovalMode  = ApprovalModes.configured(c.statusChange)
  def edgeMode: ApprovalMode    = ApprovalModes.configured(c.edges)
  /** `compile-article`: `manual` (the default) waits for a person before the article is written. */
  def publishMode: ApprovalMode = ApprovalModes.configured(c.articlePublish)
  /** `infer-filter`: `manual` (the default) records the filter only once a person approves it. */
  def filterMode: ApprovalMode  = ApprovalModes.configured(c.articleFilter)

  /** Whether the managers' supervisor decides an open approval of `kind` by its code checks (the
    * autopilot's auto approvals): `article-plan` and `goal-plan` = `auto` (or `checked`);
    * `article-publish` = `checked` — its `auto` approves in the run at once, unchecked, and the supervisor
    * only decides what was left open. Any other kind, or any other value, is a person's. */
  def checked(kind: ApprovalKind): Boolean = kind match
    case ApprovalKind.ArticlePlan    => AgentApprovalsConfig.isChecked(c.articlePlan)
    case ApprovalKind.GoalPlan       => AgentApprovalsConfig.isChecked(c.goalPlan)
    case ApprovalKind.ArticlePublish => AgentApprovalsConfig.isChecked(c.articlePublish)
    case _                           => false

extension (c: AgentsConfig)
  /** The HTTP provider block for a kind; the CLI providers have none. */
  def provider(k: ProviderKind): Option[AgentProviderConfig] = k match
    case ProviderKind.Anthropic  => Some(c.providers.anthropic)
    case ProviderKind.OpenAI     => Some(c.providers.openai)
    case ProviderKind.Local      => Some(c.providers.local)
    case ProviderKind.ClaudeCode | ProviderKind.Codex | ProviderKind.Opencode | ProviderKind.Plugin(_) => None

  /** The configured `provider:model` of a role, as written (empty = unset). */
  def role(r: AgentRole): String = r match
    case AgentRole.Probe      => c.roles.probe
    case AgentRole.Extractor  => c.roles.extractor
    case AgentRole.Reviewer   => c.roles.reviewer
    case AgentRole.ColdReader => c.roles.coldReader
    case AgentRole.Skeptic    => c.roles.skeptic
    case AgentRole.Fixer      => c.roles.fixer
    case AgentRole.Planner    => c.roles.planner
    case AgentRole.Writer     => c.roles.writer

  /** How the command-line providers are signed in — which models draw on a plan pool. */
  def logins: PlanQuota.Logins =
    PlanQuota.Logins(c.providers.claudeCode.useApiKey, c.providers.codex.useApiKey, c.providers.opencode.chatgptLogin)
