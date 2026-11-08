package com.histmersion
package util.jobs

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api

/** `POST /api/jobs/{id}/{action}`, the `job_control` tool and `histmersion jobs pause|resume|cancel`. */
final class JobControlControllers(jobs: JobControlService):

  def control(id: Job.Id, action: JobAction.Wire): Either[Api.Error, JobInfo] =
    JobAction
      .parse(action)
      .toRight(
        Api.Error(
          Problem(s"unknown action '$action' — one of: ${JobAction.values.map(_.wire).mkString(", ")}")
        )
      )
      .flatMap(jobs.control(id, _))
