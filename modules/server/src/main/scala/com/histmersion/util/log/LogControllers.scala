package com.histmersion
package util.log

import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.problem.Problem.{NotFound, Raw, TypeName}
import com.histmersion.util.problem.Problems

import java.time.Instant

/** `GET /api/log`, `GET /api/problems/{id}`, the `log` tool and `histmersion log`: the filters checked, then a page.
  */
final class LogControllers(log: LogService, now: () => Instant):

  def events(filter: LogPage.Filter): Either[Problems, LogPage] =
    LogQuery.parse(filter, now()).map(log.page)

  /** What happened around a problem, by the id its caller was shown: the steps of the trace it was raised in, oldest
    * first.
    */
  def problem(id: LogEvent.ProblemId): Either[Problems, LogPage] =
    log
      .problem(LogEvent.ProblemId(id.value.trim), LogQuery.Limit(LogPage.MaxLimit))
      .toRight(NotFound.Entity(TypeName.of(LogEvent.ProblemId), Raw.of(id)))
