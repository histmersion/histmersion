package com.histmersion
package domain.automation

import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable}
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon

/** What the automation says about the data it owns: the engine's tables (runs' steps, approvals, managers, leases, plan
  * pools), the goals and the work lists; the agents' homes and the Codex sessions it keeps files in; what a bundle
  * carries of it and how a merge treats it. It counts nothing for About.
  */
object AutomationData extends DataContribution:
  val owner: DataOwner = DataOwner("automation")

  val tables: List[OwnedTable] = OwnedTable.all(
    "agent_allowances",
    "agent_autopilot",
    "agent_leases",
    "agent_managers",
    "agent_messages",
    "agent_role_chains",
    "agent_role_fallthrough",
    "agent_state",
    "agent_steps",
    "approvals",
    "manager_events",
    "manager_hint_uses",
    "manager_hints",
    "manager_items",
    "plan_pools",
    "plan_quota_readings",
    "plan_quota_refreshes",
    "goals",
    "goal_aliases",
    "goal_focus",
    "goal_links",
    "goal_schedules",
    "queue_items"
  )

  /** The agent runtimes' homes and the Codex sessions. */
  override val stores: List[Location] = List(Location.AgentHomes, Location.CodexSessions)

  /** The goals, the work lists' items and the goals' old slugs. */
  override val carried: List[CarriedKind] = AutomationExchange.carried

  /** How a merge treats goals and the work lists' items. */
  override val mergeRules: List[MergeRules] = AutomationExchange.mergeRules

  def figures(using DbCon): DataFigures = DataFigures.empty
