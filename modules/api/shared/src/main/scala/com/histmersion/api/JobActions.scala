package com.histmersion.api

/** What `POST /api/jobs/{id}/{action}` takes, and its refusal's code. */
object JobActions:
  val all: List[String] = List("pause", "resume", "cancel")

  /** The error code of an action the job's state refuses (409). */
  val CodeConflict = "conflict"
