package com.histmersion
package api

import com.histmersion.api.automation.Pipeline
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.library.Source
import io.github.iltotore.iron.constraint.any.Pure

/** The label the UI shows for an identifier of any of the vocabularies — job types, wait codes, strategies, job kinds,
  * approval kinds, pipelines, manager kinds, statuses and states — else the identifier humanised.
  */
object UiLabels:
  export Wording.Id as Key
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  lazy val known: Map[Key, Text] =
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
      Run.State.values.toList.map(s => (s.wire.value: String) -> Wording.runState(s)))
      .map((k, v) => Key(k) -> Text(v))
      .toMap

  def label(id: Key): Text = known.getOrElse(Key((id).value.trim), Text(Wording.humanize(id)))
