package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One OCR job. `status` is a [[JobState]] spelling; `progress` is free text for the CLI's line. */
final case class JobInfo(
    id: String,
    kind: String,
    status: String,
    citekey: Option[String] = None,
    progress: Option[String] = None,
    error: Option[String] = None,
    sha256: Option[String] = None,
    createdAt: String = "",
    updatedAt: String = "",
    /** The acquisition item the job ticks off when it succeeds (an upload started from one). */
    acquireItem: Option[Int] = None,
    /** What the job imports and what it left out, when that is more than a citekey ([[JobDetail]]). */
    detail: Option[JobDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean = state.isTerminal
  def succeeded: Boolean = state == JobState.Done
object JobInfo:
  given Schema[JobInfo] = summon[KindlingsSchema[JobInfo]].schema
