package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.Agents.{RunDetail, RunInfo}
import com.histmersion.api.automation.RunCompare
import com.histmersion.domain.CompareRuns
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

/** `agent compare` at the server's edge: the recorded runs, as the store reads them, into what the domain compares; the
  * domain's comparison into what the wire shows.
  */
object CompareRunsViews:
  private given roleToChoice: Transformer[com.histmersion.api.automation.AgentRole.Wire, CompareRuns.ModelChoice.Role] =
    r => CompareRuns.ModelChoice.Role(r.value)
  private given stepStatusToDomain: Transformer[com.histmersion.api.automation.Agents.StepInfo.Status, com.histmersion.domain.automation.Step.Status.Wire] =
    st => com.histmersion.domain.automation.Step.Status.Wire(st.value)
  private given runStatusToDomain: Transformer[RunInfo.Status, com.histmersion.domain.automation.Run.State.Wire] =
    st => com.histmersion.domain.automation.Run.State.Wire(st.value)
  private given runStateToWire: Transformer[com.histmersion.domain.automation.Run.State.Wire, RunCompare.RunSide.Status] =
    st => RunCompare.RunSide.Status(st.value)
  private given roleToWire: Transformer[CompareRuns.ModelChoice.Role, com.histmersion.api.automation.AgentRole.Wire] =
    r => com.histmersion.api.automation.AgentRole.Wire(r.value)
  private given modelToWire: Transformer[CompareRuns.Step.Model, RunCompare.StepSide.Models] =
    m => RunCompare.StepSide.Models(m.value)
  private given actualToWire: Transformer[CompareRuns.Step.ActualModel, RunCompare.StepSide.Actual] =
    m => RunCompare.StepSide.Actual(m.value)

  private given Transformer[RunInfo, CompareRuns.RunHead] =
    Transformer
      .define[RunInfo, CompareRuns.RunHead]
      .withFieldComputed(_.modelFor, _.params.overrides.map(_.transformInto[CompareRuns.ModelChoice]))
      .buildTransformer

  def run(detail: RunDetail): CompareRuns.Run = detail.transformInto[CompareRuns.Run]

  def comparison(c: CompareRuns.Comparison): RunCompare.Comparison = c.transformInto[RunCompare.Comparison]
