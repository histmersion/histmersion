package com.histmersion
package domain.research.workflows

import com.histmersion.util.json.JsonCodec
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.domain.automation.agents.Checkpointed
import com.histmersion.domain.Proposal
import com.histmersion.domain.library.workflows.TwinTransformers
import com.histmersion.api.research.workflows as wire

object ResearchTransformers extends ResearchRecords, TwinTransformers:
  override given ledger: Checkpointed[Ledger] = stored[Ledger, wire.ReviewLedger]
  override given amended: Checkpointed[Amended] = stored[Amended, wire.Amended]
  override given snapshot: Checkpointed[Snapshot] = stored[Snapshot, wire.Snapshot]
  override given finding: Checkpointed[Finding] = stored[Finding, wire.ReviewFinding]
  override given findings: Checkpointed[List[Finding]] = stored[List[Finding], List[wire.ReviewFinding]]
  override given merged: Checkpointed[Merged] = stored[Merged, wire.Merged]
  override given report: Checkpointed[Report] = stored[Report, wire.Report]
  override given recheck: Checkpointed[Recheck] = stored[Recheck, wire.RecheckedFindings]
  override given fix: Checkpointed[Fix] = stored[Fix, wire.Fix]
  override given extracted: Checkpointed[Extracted] = stored[Extracted, wire.Extracted]
  override given support: Checkpointed[Support] = stored[Support, wire.Support]
  override given proposal: Checkpointed[Proposal] = stored[Proposal, wire.Proposal]

  val extractAnswer: JsonCodec[ExtractAnswer] = answer[ExtractAnswer, wire.ExtractionAnswer]
  val draftAnswer: JsonCodec[DraftAnswer] = answer[DraftAnswer, wire.WrittenDraft]
  val rulesAnswer: JsonCodec[RulesAnswer] = answer[RulesAnswer, wire.GuideReview]
  val coldReaderAnswer: JsonCodec[ColdReaderAnswer] = answer[ColdReaderAnswer, wire.ColdReaderAnswer]
  val skepticAnswer: JsonCodec[SkepticAnswer] = answer[SkepticAnswer, wire.SkepticAnswer]
  val recheckAnswer: JsonCodec[RecheckAnswer] = answer[RecheckAnswer, wire.RecheckAnswer]
  val fixerAnswer: JsonCodec[FixerAnswer] = answer[FixerAnswer, wire.FixerAnswer]
