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
  type Key = _root_.com.histmersion.api.Wording.Id
  val Key: _root_.com.histmersion.api.Wording.Id.type = _root_.com.histmersion.api.Wording.Id
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  lazy val known: Map[com.histmersion.api.UiLabels.Key, com.histmersion.api.UiLabels.Text] =
    (Wording.schedulerJobTypes.toList.map((k, v) => (k.value: String) -> v) ++
      AgentScheduler.waitCodes.toList.map((k, v) => (k.value: String) -> v) ++
      Wording.strategies.toList.map((k, v) => (k.value: String) -> v) ++
      RegisteredJobKinds.all.entries.map(e => (e.kind.value: String) -> (e.name.value: String)) ++
      Approval.Kind.values.toList.map(k => (k.wire.value: String) -> Wording.approvalKind(k)) ++
      Pipeline.values.toList.map(p => (p.wire.value: String) -> Wording.pipeline(p)) ++
      Manager.Kind.values.toList.map(k => (k.wire.value: String) -> Wording.managerKind(k)) ++
      Source.Status.values.toList.map(s => (s.wire.value: String) -> Wording.status(s)) ++
      Approval.State.values.toList.map(s => (s.wire.value: String) -> Wording.approvalState(s)) ++
      JobState.values.toList.map(s => (s.wire.value: String) -> Wording.jobState(s)) ++
      Run.State.values.toList.map(s => (s.wire.value: String) -> Wording.runState(s))).map((k, v) => _root_.com.histmersion.api.UiLabels.Key(k) -> _root_.com.histmersion.api.UiLabels.Text(v)).toMap

  def label(id: com.histmersion.api.UiLabels.Key): com.histmersion.api.UiLabels.Text = known.getOrElse(_root_.com.histmersion.api.UiLabels.Key((id).value.trim), _root_.com.histmersion.api.UiLabels.Text(Wording.humanize(id)))
