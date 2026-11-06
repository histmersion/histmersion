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
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  lazy val known: Map[com.histmersion.api.UiLabels.Key, com.histmersion.api.UiLabels.Text] =
    Wording.schedulerJobTypes ++ AgentScheduler.waitCodes ++ Wording.strategies ++
      RegisteredJobKinds.all.entries.map(e => e.kind.value -> e.name.value) ++
      Approval.Kind.values.map(k => k.wire -> Wording.approvalKind(k)) ++
      Pipeline.values.map(p => p.wire -> Wording.pipeline(p)) ++
      Manager.Kind.values.map(k => k.wire -> Wording.managerKind(k)) ++
      Source.Status.values.map(s => s.wire -> Wording.status(s)) ++
      Approval.State.values.map(s => s.wire -> Wording.approvalState(s)) ++
      JobState.values.map(s => s.wire -> Wording.jobState(s)) ++
      Run.State.values.map(s => s.wire -> Wording.runState(s))

  def label(id: com.histmersion.api.UiLabels.Key): com.histmersion.api.UiLabels.Text = known.getOrElse(_root_.com.histmersion.api.UiLabels.Key((id).value.trim), Wording.humanize(id))
