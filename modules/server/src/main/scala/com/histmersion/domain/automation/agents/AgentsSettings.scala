package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.planquota.Logins
import com.histmersion.domain.PlanAnswer

import com.histmersion.server.agents.{ClaudeCode, Codex, Opencode}

import com.histmersion.api.automation.PlanQuota
import com.histmersion.config.{
  AgentApprovalsConfig,
  AgentPlanConfig,
  AgentProviderConfig,
  AgentsConfig,
  ScheduledRefresh
}
import com.histmersion.domain.{ApprovalModes, ReadSchedule}
import com.histmersion.domain.automation.{AgentRole, ProviderKind}

import java.time.Duration
import com.histmersion.domain.automation.Approval
import com.histmersion.lint.{tolerated, LintRule}

extension (c: AgentApprovalsConfig)
  @tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statusMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.statusChange.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def edgeMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.edges.value))

  /** `compile-article`: `manual` (the default) waits for a person before the article is written. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def publishMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.articlePublish.value))

  /** `infer-filter`: `manual` (the default) records the filter only once a person approves it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def filterMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.articleFilter.value))

  /** Whether the managers' supervisor decides an open approval of `kind` by its code checks (the autopilot's auto
    * approvals): `article-plan` and `goal-plan` = `auto` (or `checked`); `article-publish` = `checked` — its `auto`
    * approves in the run at once, unchecked, and the supervisor only decides what was left open. Any other kind, or any
    * other value, is a person's.
    */
  def checked(kind: Approval.Kind): Boolean = kind match
    case Approval.Kind.ArticlePlan    => AgentApprovalsConfig.isChecked(c.articlePlan)
    case Approval.Kind.GoalPlan       => AgentApprovalsConfig.isChecked(c.goalPlan)
    case Approval.Kind.ArticlePublish => AgentApprovalsConfig.isChecked(c.articlePublish)
    case _                            => false

extension (c: AgentsConfig)
  /** The HTTP provider block for a kind; the CLI providers have none. */
  def provider(k: ProviderKind): Option[AgentProviderConfig] = k match
    case ProviderKind.Anthropic => Some(c.providers.anthropic)
    case ProviderKind.OpenAI    => Some(c.providers.openai)
    case ProviderKind.Local     => Some(c.providers.local)
    case ProviderKind.ClaudeCode | ProviderKind.Codex | ProviderKind.Opencode | ProviderKind.Plugin(_) => None

  /** The configured `provider:model` of a role, as written (empty = unset). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def role(r: AgentRole): String = r match
    case AgentRole.Probe      => c.roles.probe.value
    case AgentRole.Extractor  => c.roles.extractor.value
    case AgentRole.Reviewer   => c.roles.reviewer.value
    case AgentRole.ColdReader => c.roles.coldReader.value
    case AgentRole.Skeptic    => c.roles.skeptic.value
    case AgentRole.Fixer      => c.roles.fixer.value
    case AgentRole.Planner    => c.roles.planner.value
    case AgentRole.Writer     => c.roles.writer.value

  /** How the command-line providers are signed in — which models draw on a plan pool. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def logins: Logins =
    Logins(
      (if c.providers.claudeCode.useApiKey.value then Logins.ClaudeCodeApiKey.ApiKey
       else Logins.ClaudeCodeApiKey.Login),
      (if c.providers.codex.useApiKey.value then Logins.CodexApiKey.ApiKey
       else Logins.CodexApiKey.Login),
      (if c.providers.opencode.chatgptLogin.value then Logins.OpencodeChatgpt.Chatgpt
       else Logins.OpencodeChatgpt.Other)
    )

extension (c: AgentPlanConfig)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def caps: PlanAnswer.Caps = PlanAnswer.Caps(
    PlanAnswer.Caps.SubGoals(c.maxSubGoals.value),
    PlanAnswer.Caps.Queries(c.maxQueries.value),
    PlanAnswer.Caps.Works(c.maxWorks.value),
    PlanAnswer.Caps.QueryChars(c.queryChars.value)
  )

extension (s: ScheduledRefresh)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readSchedule: ReadSchedule = s match
    case ScheduledRefresh.Off             => ReadSchedule.Off
    case ScheduledRefresh.Every(interval) => ReadSchedule.Every(Duration.ofMinutes(interval.value.toLong))
