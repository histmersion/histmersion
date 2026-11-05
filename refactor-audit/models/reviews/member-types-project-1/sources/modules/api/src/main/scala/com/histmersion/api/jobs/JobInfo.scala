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
    status: JobInfo.Status,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    progress: Option[JobInfo.Progress] = None,
    error: Option[JobInfo.Error] = None,
    sha256: Option[com.histmersion.api.Sha256] = None,
    createdAt: JobInfo.CreatedAt = "",
    updatedAt: JobInfo.UpdatedAt = "",
    acquireItem: Option[com.histmersion.api.automation.Queue.QueueItem.Id] = None,
    detail: Option[JobDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: com.histmersion.api.jobs.JobInfo.IsTerminalResult = state.isTerminal
  def succeeded: com.histmersion.api.jobs.JobInfo.SucceededResult = state == JobState.Done
object JobInfo:

  /** A job's kind as the wire names it (`ingest`, `merge-apply`); the server checks it against its registered kinds. */
  type IsTerminalResult = IsTerminalResult.T
  object IsTerminalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SucceededResult = SucceededResult.T
  object SucceededResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Progress = Progress.T
  object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]

  /** A job's id as the wire names it; the server checks it is one. */
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
