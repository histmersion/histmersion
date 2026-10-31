package com.histmersion.util.jobs

import com.histmersion.api.{ApiError, JobInfo}

/** `POST /api/jobs/{id}/{action}`, the `job_control` tool and `histmersion jobs pause|resume|cancel`. */
final class JobControlControllers(jobs: JobControlService):

  def control(id: String, action: String): Either[ApiError, JobInfo] =
    JobAction
      .parse(action)
      .toRight(ApiError(s"unknown action '$action' — one of: ${JobAction.values.map(_.wire).mkString(", ")}"))
      .flatMap(jobs.control(id.trim, _))
