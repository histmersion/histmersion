package com.histmersion.api.jobs

import com.histmersion.api.RegisteredJobKinds
import com.histmersion.api.given
object JobActions:
  type StoppableText = StoppableText.T
  object StoppableText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stoppable = StoppableValue
  type StoppableValue = com.histmersion.api.jobs.JobActions.StoppableText
  val StoppableValue: _root_.com.histmersion.api.jobs.JobActions.StoppableText.type = _root_.com.histmersion.api.jobs.JobActions.StoppableText

  val all: List[com.histmersion.api.jobs.JobActions.Wire] = List(_root_.com.histmersion.api.jobs.JobActions.Wire("pause"), _root_.com.histmersion.api.jobs.JobActions.Wire("resume"), _root_.com.histmersion.api.jobs.JobActions.Wire("cancel"))

  /** The kinds whose jobs a person may pause and cancel. */
  val Stoppable: Set[com.histmersion.api.jobs.JobActions.Stoppable] = RegisteredJobKinds.all.stoppableKinds

  val CodeConflict = "conflict"
