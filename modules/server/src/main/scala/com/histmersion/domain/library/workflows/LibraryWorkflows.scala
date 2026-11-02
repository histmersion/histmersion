package com.histmersion.domain.library.workflows

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.{AgentsHandlers, StepAgent}
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.workflows.WorkflowDefinition
import com.histmersion.util.database.Transactor

import javax.sql.DataSource

/** What the library contributes to the workflow engine: its three pipelines as workflows (enrich-source,
  * currency-check, acquisition-sweep), the enrich and watch manager kinds, and the acquisition work and source
  * proposals they and research's workflows share.
  */
final class LibraryWorkflows(
    cfg: () => AppConfig,
    ds: DataSource,
    transactor: Transactor,
    site: LibrarySite,
    a: AgentsHandlers
):
  /** The acquisition toolset, the ingest backlog, the acquisition list's re-check and the seeding. */
  val work: AcquisitionWork = AcquisitionWork(cfg, ds, transactor, site, a)

  private val upstream: () => AgentUpstream = () => work.acquisition

  /** How a source's proposed status or edge goes through the approvals (research's review proposes them too). */
  val proposals: SourceProposals = SourceProposals(
    a.approvals,
    cfg().agents,
    ck => site.sources.get(ck, body = false).toOption.map(_.source.status),
    p => site.sources.patch(p.citekey, p.patch).left.map(_.message).map(_ => p.describe)
  )

  val enrichSource: WorkflowDefinition = LibraryWorkflow(
    EnrichSourcePipeline(site, cfg().agents, cfg().libraryAgents, upstream, proposals),
    List(StepAgent.EnrichClassify, StepAgent.EnrichFindings, StepAgent.EnrichNotes),
    a.definitions
  )

  val currencyCheck: WorkflowDefinition =
    LibraryWorkflow(CurrencyCheckPipeline(site, a.goals, cfg().agents, upstream, proposals), Nil, a.definitions)

  val acquisitionSweep: WorkflowDefinition = LibraryWorkflow(
    AcquisitionSweepPipeline(site, a, cfg().agents, upstream, work.oaBacklog),
    List(StepAgent.AcquisitionRanker),
    a.definitions
  )

  /** The library-wide enrich manager (auto-enrich after an ingest). */
  val enrichKind: EnrichManagerKind = EnrichManagerKind(site, a)

  /** A research area's watcher. */
  val watchKind: WatchManagerKind = WatchManagerKind(site, work, cfg().libraryAgents)

  val managerKinds: List[ManagerKindDefinition] = List(enrichKind, watchKind)
