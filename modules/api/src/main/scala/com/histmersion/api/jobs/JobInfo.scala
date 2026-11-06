package com.histmersion.api.jobs
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class JobInfo(
    id: com.histmersion.api.SpatialHierarchyResult.Job.Id,
    kind: com.histmersion.api.jobs.JobKinds.Wire,
    status: JobInfo.Status,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    progress: Option[JobInfo.Progress] = None,
    error: Option[JobInfo.Error] = None,
    sha256: Option[com.histmersion.api.Sha256] = None,
    createdAt: JobInfo.CreatedAt = _root_.com.histmersion.api.jobs.JobInfo.CreatedAt(""),
    updatedAt: JobInfo.UpdatedAt = _root_.com.histmersion.api.jobs.JobInfo.UpdatedAt(""),
    acquireItem: Option[com.histmersion.api.automation.Queue.QueueItem.Id] = None,
    detail: Option[JobDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean = state.isTerminal
  def succeeded: Boolean = (state == JobState.Done)
object JobInfo:

  /** A job's kind as the wire names it (`ingest`, `merge-apply`); the server checks it against its registered kinds. */

  type Status = com.histmersion.api.jobs.JobState.Wire
  val Status: _root_.com.histmersion.api.jobs.JobState.Wire.type = _root_.com.histmersion.api.jobs.JobState.Wire
  type Progress = Progress.T
  object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Kind = com.histmersion.api.jobs.JobKinds.Wire
  val Kind: _root_.com.histmersion.api.jobs.JobKinds.Wire.type = _root_.com.histmersion.api.jobs.JobKinds.Wire

  /** A job's id as the wire names it; the server checks it is one. */
  type Id = com.histmersion.api.SpatialHierarchyResult.Job.Id
  val Id: _root_.com.histmersion.api.SpatialHierarchyResult.Job.Id.type = _root_.com.histmersion.api.SpatialHierarchyResult.Job.Id
