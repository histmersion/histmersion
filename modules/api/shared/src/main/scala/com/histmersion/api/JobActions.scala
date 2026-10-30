package com.histmersion.api

/** What `POST /api/jobs/{id}/{action}` takes, and its refusal's code. */
object JobActions:
  val all: List[String] = List("pause", "resume", "cancel")

  /** The long jobs that report between their parts (a batch of pictures, a catalogue's records, a layer of
    * the base map, one acquisition item, a batch of a gazetteer in the init job's loads) and so stop cleanly
    * there; each skips, when run again, the parts it had done. */
  val Stoppable: Set[String] = Set(JobKind.MediaImport, JobKind.MediaCovers, JobKind.EventsImport, JobKind.BasemapFetch,
    JobKind.AcquireRecheck, JobKind.Init).map(_.wire)

  /** The error code of an action the job's state refuses (409). */
  val CodeConflict = "conflict"
