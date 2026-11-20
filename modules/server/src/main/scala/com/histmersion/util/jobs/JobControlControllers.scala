package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api
import com.histmersion.util.data.maintenance.WriteGate

/** `POST /api/jobs/{id}/{action}`, the `job_control` tool and `histmersion jobs pause|resume|cancel`: a write, under
  * the maintenance gate.
  */
final class JobControlControllers(jobs: JobControlService, maintenance: WriteGate):

  def control(id: Job.Id, action: JobAction.Wire): Either[Api.Error, JobInfo] =
    JobAction
      .parse(action)
      .toRight(
        Api.Error(
          Problem.Unclassified(
            Legacy.Text(s"unknown action '$action' — one of: ${JobAction.values.map(_.wire).mkString(", ")}")
          )
        )
      )
      .flatMap(a => maintenance.guard(WriteGate.OperationName("job_control"))(jobs.control(id, a)))
