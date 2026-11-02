package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.AgentsHandlers
import com.histmersion.server.{Deps, Handlers}

/** The library's acquisition work the engine schedules, held by the watch manager kind until the library contributes
  * it: the acquisition toolset (OpenAlex/Crossref, refs, assess, followups), the watchers' ingest backlog, the
  * acquisition list's legal re-check and the seeding of goals with no source.
  */
final class AcquisitionWork(deps: Deps, h: Handlers, a: AgentsHandlers):
  private def cfg = deps.cfg.agents

  /** The acquisition toolset. A `var` because it is built AFTER [[Handlers]] (it needs the ingest queue), and attached
    * by the composition before the pool starts; until then — and in a test that attaches nothing — the steps that need
    * it say it is offline.
    */
  @volatile private var upstream: AgentUpstream = AgentUpstream.Offline

  def attach(u: AgentUpstream): Unit = upstream = u

  /** The acquisition operations as attached now (the watch kind's candidate sweep, #3761). */
  def acquisition: AgentUpstream = upstream

  /** The watchers' ingest backlog: the legal open-access picks past a day's allowance, ingested on later days (the
    * sweep parks them, the managers' tick drains them).
    */
  val oaBacklog: OaBacklog = OaBacklog(deps.ds, cfg, deps.cfg.libraryAgents, h, () => upstream, a)

  /** The acquisition list's legal re-check (the managers' tick runs it; `queue acquire recheck` by hand). */
  val acquireRecheck: AcquireRecheckService =
    AcquireRecheckService(
      deps.ds,
      com.histmersion.domain.library.acquisition.AcquireCheckService(deps.transactor),
      deps.cfg.acquire.recheck,
      h,
      () => upstream,
      a
    )

  /** Seed before the plan: goals with no source gather candidates (no model) before their plan is approved. */
  val seeding: SeedService = SeedService(
    deps.ds,
    com.histmersion.domain.library.acquisition.CandidateService(deps.transactor),
    cfg,
    h,
    a,
    () => upstream,
    oaBacklog
  )
