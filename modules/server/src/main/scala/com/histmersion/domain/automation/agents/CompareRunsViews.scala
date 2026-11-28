package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.Agents.{RunDetail, RunInfo}
import com.histmersion.api.automation.RunCompare
import com.histmersion.domain.CompareRuns
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import com.histmersion.domain.TwoStateTransformers.given
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.automation.Agents
import com.histmersion.domain.automation.Step
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{tolerated, LintRule}

/** `agent compare` at the server's edge: the recorded runs, as the store reads them, into what the domain compares; the
  * domain's comparison into what the wire shows.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object CompareRunsViews:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given roleToChoice: Transformer[AgentRole.Wire, CompareRuns.ModelChoice.Role] =
    r => CompareRuns.ModelChoice.Role(r.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given stepStatusToDomain: Transformer[Agents.StepInfo.Status, Step.Status.Wire] =
    st => Step.Status.Wire(st.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given runStatusToDomain: Transformer[RunInfo.Status, Run.State.Wire] =
    st => Run.State.Wire(st.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given runStateToWire: Transformer[Run.State.Wire, RunCompare.RunSide.Status] =
    st => RunCompare.RunSide.Status(st.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given roleToWire: Transformer[CompareRuns.ModelChoice.Role, AgentRole.Wire] =
    r => AgentRole.Wire(r.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given modelToWire: Transformer[CompareRuns.Step.Model, RunCompare.StepSide.Models] =
    m => RunCompare.StepSide.Models(m.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given actualToWire: Transformer[CompareRuns.Step.ActualModel, RunCompare.StepSide.Actual] =
    m => RunCompare.StepSide.Actual(m.value)

  private given Transformer[RunInfo, CompareRuns.RunHead] =
    Transformer
      .define[RunInfo, CompareRuns.RunHead]
      .withFieldComputed(_.modelFor, _.params.overrides.map(_.transformInto[CompareRuns.ModelChoice]))
      .buildTransformer

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def run(detail: RunDetail): CompareRuns.Run = detail.transformInto[CompareRuns.Run]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def comparison(c: CompareRuns.Comparison): RunCompare.Comparison = c.transformInto[RunCompare.Comparison]
