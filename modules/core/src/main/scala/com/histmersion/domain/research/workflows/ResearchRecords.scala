package com.histmersion
package domain.research.workflows

import scala.compiletime.deferred
import com.histmersion.domain.automation.agents.Checkpointed
import com.histmersion.domain.Proposal

trait ResearchRecords:
  given ledger: Checkpointed[Ledger] = deferred
  given amended: Checkpointed[Amended] = deferred
  given snapshot: Checkpointed[Snapshot] = deferred
  given finding: Checkpointed[Finding] = deferred
  given findings: Checkpointed[List[Finding]] = deferred
  given merged: Checkpointed[Merged] = deferred
  given report: Checkpointed[Report] = deferred
  given recheck: Checkpointed[Recheck] = deferred
  given fix: Checkpointed[Fix] = deferred
  given extracted: Checkpointed[Extracted] = deferred
  given support: Checkpointed[Support] = deferred
  given proposal: Checkpointed[Proposal] = deferred
