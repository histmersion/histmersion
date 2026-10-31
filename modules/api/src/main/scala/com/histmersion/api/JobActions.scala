package com.histmersion.api

object JobActions:
  val all: List[String] = List("pause", "resume", "cancel")

  /** The kinds whose jobs a person may pause and cancel. */
  val Stoppable: Set[String] = JobKindsAdapter.all.stoppableKinds

  val CodeConflict = "conflict"
