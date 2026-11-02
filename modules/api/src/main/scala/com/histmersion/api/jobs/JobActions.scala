package com.histmersion.api.jobs

import com.histmersion.api.RegisteredJobKinds
import com.histmersion.api.given
object JobActions:
  val all: List[String] = List("pause", "resume", "cancel")

  /** The kinds whose jobs a person may pause and cancel. */
  val Stoppable: Set[String] = RegisteredJobKinds.all.stoppableKinds

  val CodeConflict = "conflict"
