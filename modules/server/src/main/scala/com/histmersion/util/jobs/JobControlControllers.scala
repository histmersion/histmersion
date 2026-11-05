package com.histmersion.util.jobs

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api

/** `POST /api/jobs/{id}/{action}`, the `job_control` tool and `histmersion jobs pause|resume|cancel`. */
final class JobControlControllers(jobs: JobControlService):

  def control(id: com.histmersion.util.jobs.JobControlControllers.ControlId, action: com.histmersion.util.jobs.JobControlControllers.ControlAction): Either[Api.Error, JobInfo] =
    JobAction
      .parse(action)
      .toRight(Api.Error(s"unknown action '$action' — one of: ${JobAction.values.map(_.wire).mkString(", ")}"))
      .flatMap(jobs.control(id.trim, _))

object JobControlControllers:
  type ControlId = ControlId.T
  object ControlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlAction = ControlAction.T
  object ControlAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
