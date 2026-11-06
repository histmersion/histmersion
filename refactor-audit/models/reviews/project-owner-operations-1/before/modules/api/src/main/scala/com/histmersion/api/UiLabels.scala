package com.histmersion.api

import com.histmersion.api.automation.Pipeline
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.library.Source

/** The label the UI shows for an identifier of any of the vocabularies — job types, wait codes, strategies, job kinds,
  * approval kinds, pipelines, manager kinds, statuses and states — else the identifier humanised.
  */
object UiLabels:

  type KnownPart1 = KnownPart1.T
  object KnownPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownPart2 = KnownPart2.T
  object KnownPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelResult = LabelResult.T
  object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelId = LabelId.T
  object LabelId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  lazy val known: Map[com.histmersion.api.UiLabels.KnownPart1, com.histmersion.api.UiLabels.KnownPart2] =
    Wording.schedulerJobTypes ++ AgentScheduler.waitCodes ++ Wording.strategies ++
      RegisteredJobKinds.all.entries.map(e => e.kind.value -> e.name.value) ++
      Approval.Kind.values.map(k => k.wire -> Wording.approvalKind(k)) ++
      Pipeline.values.map(p => p.wire -> Wording.pipeline(p)) ++
      Manager.Kind.values.map(k => k.wire -> Wording.managerKind(k)) ++
      Source.Status.values.map(s => s.wire -> Wording.status(s)) ++
      Approval.State.values.map(s => s.wire -> Wording.approvalState(s)) ++
      JobState.values.map(s => s.wire -> Wording.jobState(s)) ++
      Run.State.values.map(s => s.wire -> Wording.runState(s))

  def label(id: com.histmersion.api.UiLabels.LabelId): com.histmersion.api.UiLabels.LabelResult = known.getOrElse(id.trim, Wording.humanize(id))
