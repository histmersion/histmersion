package com.histmersion.api

object JobActions:
  val all: List[String] = List("pause", "resume", "cancel")

  val Stoppable: Set[String] = Set(
    JobKind.MediaImport,
    JobKind.MediaCovers,
    JobKind.EventsImport,
    JobKind.BasemapFetch,
    JobKind.AcquireRecheck,
    JobKind.Init
  ).map(_.wire)

  val CodeConflict = "conflict"
