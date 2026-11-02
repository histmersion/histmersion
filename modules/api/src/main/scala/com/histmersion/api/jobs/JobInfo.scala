package com.histmersion.api.jobs
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
    acquireItem: Option[Int] = None,
    detail: Option[JobDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean = state.isTerminal
  def succeeded: Boolean = state == JobState.Done
object JobInfo:
  given Schema[JobInfo] = summon[KindlingsSchema[JobInfo]].schema
