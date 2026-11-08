package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.{ClaudeCode, Codex, Opencode}

import com.histmersion.api.automation.PlanQuota
import com.histmersion.config.{
  AgentApprovalsConfig,
  AgentPlanConfig,
  AgentProviderConfig,
  AgentsConfig,
  ScheduledRefresh
}
import com.histmersion.domain.{ApprovalModes, PlanGoal, ReadSchedule}
import com.histmersion.domain.automation.{AgentRole, ProviderKind}

import java.time.Duration
import com.histmersion.domain.automation.Approval

extension (c: AgentApprovalsConfig)
  def statusMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.statusChange.value))
  def edgeMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.edges.value))

  /** `compile-article`: `manual` (the default) waits for a person before the article is written. */
  def publishMode: Approval.Mode = ApprovalModes.configured(Approval.Mode.Wire(c.articlePublish.value))

  /** `infer-filter`: `manual` (the default) records the filter only once a person approves it. */
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
  def logins: PlanQuota.Logins =
    PlanQuota.Logins(
      PlanQuota.Logins.ClaudeCodeApiKey(c.providers.claudeCode.useApiKey.value),
      PlanQuota.Logins.CodexApiKey(c.providers.codex.useApiKey.value),
      PlanQuota.Logins.OpencodeChatgpt(c.providers.opencode.chatgptLogin.value)
    )

extension (c: AgentPlanConfig)
  def caps: PlanGoal.Caps = PlanGoal.Caps(
      PlanGoal.Caps.SubGoals(c.maxSubGoals.value),
      PlanGoal.Caps.Queries(c.maxQueries.value),
      PlanGoal.Caps.Works(c.maxWorks.value),
      PlanGoal.Caps.QueryChars(c.queryChars.value)
    )

extension (s: ScheduledRefresh)
  def readSchedule: ReadSchedule = s match
    case ScheduledRefresh.Off             => ReadSchedule.Off
    case ScheduledRefresh.Every(interval) => ReadSchedule.Every(Duration.ofMinutes(interval.value.toLong))
