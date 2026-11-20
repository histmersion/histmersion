package com.histmersion
package domain.automation.agents

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.workflows.{AutomationHost, Contributions}
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.plugin.AiProvider
import com.histmersion.util.jobs.{JobStops, Pinning, Workers}

import javax.sql.DataSource
import scala.collection.immutable.ListMap
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.util.time.Timer
import com.histmersion.util.time.Pause
import com.histmersion.config.UserPaths
import com.histmersion.util.plugins.Plugin
import com.histmersion.domain.automation.approvals.ApprovalEffects

/** What the composition root gives the engine as it builds it: the settings and the database of the generation it runs
  * on, the work lists, how work keeps its generation, the plugins' AI providers, the agent roster, and — asked once the
  * engine exists — the domains' contributions and the host the engine reaches the rest of the server through
  * (`server/agents/ServerEngineSite` over the composition).
  */
trait EngineSite:
  /** The settings now (a reload switches them). */
  def stores: Stores
  def resources: Resources
  def jvmHome: JvmHome
  def config: AppConfig
  def ds: DataSource
  def queues: QueueService
  def workLists: WorkListControllers
  def pins: Pinning
  def jobStops: JobStops

  /** The server's long-running workers, as a restore holds them: the engine, the managers and the lease register there,
    * and each run, tick and renewal is one unit of work.
    */
  def workers: Workers

  /** The time the engine's decisions read: the composition's clock (a suite gives one of its own). */
  def clock: java.time.Clock
  def timer: Timer
  def pause: Pause

  /** A clock the daily approval digest also reads its hour on, beside the tick's time: none in a server (the tick's
    * time is the composition's clock); a suite's own, so that its ticks at the wall clock's hour, whatever it is, are
    * past the digest's hour.
    */
  def digestClock: Option[java.time.Clock]

  /** The process environment and the operating system the entry point read once (the agents' homes are found by them);
    * none by default (a test's site).
    */
  def userPaths: UserPaths = AgentTrees.NoEnvironment

  /** The AI providers of the pinned plugin generation, by name. */
  def aiProviders: () => ListMap[Plugin.Id, AiProvider]

  /** How many sources a goal gathers before its articles are planned (`agents.seed.min-sources`). */
  def sourcesToPlan: AgentsControllers.SourcesCount =
    AgentsControllers.SourcesCount(config.libraryAgents.seed.minSources.value)

  /** The most panel rounds a run or a manager may ask a review for (`agents.review.rounds-cap`). */
  def roundsCap: AgentsControllers.RoundsCap = AgentsControllers.RoundsCap(config.researchAgents.review.roundsCap.value)

  /** What the definitions are held to: the contributed tools and roles. */
  def roster: AgentRoster

  /** What the domains register into engine `a`. */
  def contributions(a: AgentsControllers): Contributions

  /** What approving a proposal about a source or an article does (the domains' effects). */
  def approvalEffects: ApprovalEffects

  /** What engine `a` asks of the rest of the server. */
  def host(a: AgentsControllers): AutomationHost
