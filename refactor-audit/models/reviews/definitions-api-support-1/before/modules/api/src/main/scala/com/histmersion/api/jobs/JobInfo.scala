package com.histmersion.api.jobs
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class JobInfo(
    id: com.histmersion.api.jobs.JobInfo.Id,
    kind: com.histmersion.api.jobs.JobInfo.Kind,
    status: String,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    progress: Option[String] = None,
    error: Option[String] = None,
    sha256: Option[com.histmersion.api.Sha256] = None,
    createdAt: String = "",
    updatedAt: String = "",
    acquireItem: Option[Int] = None,
    detail: Option[JobDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean = state.isTerminal
  def succeeded: Boolean = state == JobState.Done
object JobInfo:

  /** A job's kind as the wire names it (`ingest`, `merge-apply`); the server checks it against its registered kinds. */
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]

  /** A job's id as the wire names it; the server checks it is one. */
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
