package com.histmersion
package api.jobs
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.library.Citekey
import com.histmersion.api.Sha256
import com.histmersion.api.automation.Queue

final case class JobInfo(
    id: SpatialHierarchyResult.Job.Id,
    kind: JobKinds.Wire,
    status: JobInfo.Status,
    citekey: Option[Citekey] = None,
    progress: Option[JobInfo.Progress] = None,
    error: Option[JobInfo.Error] = None,
    sha256: Option[Sha256] = None,
    createdAt: JobInfo.CreatedAt = JobInfo.CreatedAt(""),
    updatedAt: JobInfo.UpdatedAt = JobInfo.UpdatedAt(""),
    acquireItem: Option[Queue.QueueItem.Id] = None,
    detail: Option[JobDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema, CanEqual:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean = state.isTerminal
  def succeeded: Boolean = (state == JobState.Done)
object JobInfo:

  /** A job's kind as the wire names it (`ingest`, `merge-apply`); the server checks it against its registered kinds. */

  export JobState.{Wire as Status}
  type Progress = Progress.T
  object Progress extends Newtype[String, Pure]
  type Error = Error.T
  object Error extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]

  type Kind = JobKinds.Wire
  val Kind: JobKinds.Wire.type = JobKinds.Wire

  /** A job's id as the wire names it; the server checks it is one. */
  export SpatialHierarchyResult.Job.Id
