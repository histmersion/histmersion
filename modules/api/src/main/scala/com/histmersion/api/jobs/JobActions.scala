package com.histmersion.api.jobs

import com.histmersion.api.RegisteredJobKinds
import com.histmersion.api.given
object JobActions:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stoppable = StoppableValue
  type StoppableValue = StoppableValue.T
  object StoppableValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val all: List[com.histmersion.api.jobs.JobActions.Wire] = List(_root_.com.histmersion.api.jobs.JobActions.Wire("pause"), _root_.com.histmersion.api.jobs.JobActions.Wire("resume"), _root_.com.histmersion.api.jobs.JobActions.Wire("cancel"))

  /** The kinds whose jobs a person may pause and cancel. */
  val Stoppable: Set[com.histmersion.api.jobs.JobActions.Stoppable] = RegisteredJobKinds.all.stoppableKinds

  val CodeConflict = "conflict"
