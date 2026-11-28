package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.Problem

import io.scalaland.chimney.dsl.*

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.agents.StepAgent
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.workflows.ToolContribution
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.domain.Creator
import com.histmersion.domain.automation.workflows.WorkflowDefinition
import com.histmersion.util.database.Transactor

import javax.sql.DataSource
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.lint.{tolerated, LintRule}

/** What the library contributes to the workflow engine: its three pipelines as workflows (enrich-source,
  * currency-check, acquisition-sweep), the enrich and watch manager kinds, its agent tools, and the acquisition work
  * and source proposals they and research's workflows share.
  */
final class LibraryWorkflows(
    cfg: () => AppConfig,
    ds: DataSource,
    transactor: Transactor,
    site: LibrarySite,
    a: AgentsControllers
):
  /** The acquisition toolset, the ingest backlog, the acquisition list's re-check and the seeding. */
  val work: AcquisitionWork = AcquisitionWork(cfg, ds, transactor, site, a)

  private val upstream: () => AgentUpstream = () => work.acquisition

  /** How a source's proposed status or edge goes through the approvals (research's review proposes them too). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val proposals: SourceProposals = new SourceProposals(
    SourceApprovalService(transactor, a.approvalStore, a.clock),
    cfg().agents,
    ck =>
      Citekey
        .option(ck.value)
        .flatMap(c => site.sources.get(c, body = SourcesControllers.GetBody(false)).toOption.map(_.source.status)),
    p =>
      site.sources
        .patch(p.citekey, p.patch)
        .left
        .map(e => e.problems)
        .map(_ => SourceProposals.Outcome.Applied.What(p.describe.value))
  )

  val enrichSource: WorkflowDefinition = LibraryWorkflow(
    EnrichSourcePipeline(site, cfg().agents, cfg().libraryAgents, upstream, proposals, EnrichSourceTransformers),
    List(StepAgent.EnrichClassify, StepAgent.EnrichFindings, StepAgent.EnrichNotes),
    a.definitions,
    EnrichComparison(EnrichSourceTransformers)
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

  /** A source, its passages, the search, the facets, the citation graph, the read-only query and the follow-ups, as an
    * agent reads them.
    */
  val tools: List[ToolContribution] = LibraryAgentTools(site, upstream, cfg().agents.tools).contributions
