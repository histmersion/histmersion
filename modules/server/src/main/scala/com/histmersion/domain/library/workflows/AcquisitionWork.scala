package com.histmersion.domain.library.workflows

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.library.acquisition.{AcquireCheckService, CandidateService}
import com.histmersion.util.database.Transactor

import javax.sql.DataSource

/** The library's acquisition work the engine schedules, held by the watch manager kind: the acquisition toolset
  * (OpenAlex/Crossref, refs, assess, followups), the watchers' ingest backlog, the acquisition list's legal re-check
  * and the seeding of goals with no source.
  */
final class AcquisitionWork(
    cfg: () => AppConfig,
    ds: DataSource,
    transactor: Transactor,
    site: LibrarySite,
    val agents: AgentsControllers
):

  /** The acquisition toolset. A `var` because it is built after the library's site (it needs the ingest queue), and
    * attached by the composition before the pool starts; until then — and in a test that attaches nothing — the steps
    * that need it say it is offline.
    */
  @volatile private var upstream: AgentUpstream = AgentUpstream.Offline

  def attach(u: AgentUpstream): Unit = upstream = u

  /** The acquisition operations as attached now (the watch kind's candidate sweep, #3761). */
  def acquisition: AgentUpstream = upstream

  /** The watchers' ingest backlog: the legal open-access picks past a day's allowance, ingested on later days (the
    * sweep parks them, the managers' tick drains them).
    */
  val oaBacklog: OaBacklog = OaBacklog(cfg().agents, cfg().libraryAgents, site, () => upstream, agents)

  /** The acquisition list's legal re-check (the managers' tick runs it; `queue acquire recheck` by hand). */
  val acquireRecheck: AcquireRecheckService =
    AcquireRecheckService(ds, AcquireCheckService(transactor), cfg().acquire.recheck, site, () => upstream, agents)

  /** Seed before the plan: goals with no source gather candidates (no model) before their plan is approved. */
  val seeding: SeedService =
    SeedService(CandidateService(transactor), cfg().agents, site, agents, () => upstream, oaBacklog)
