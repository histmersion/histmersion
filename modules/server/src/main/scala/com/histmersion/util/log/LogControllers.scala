package com.histmersion
package util.log

import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.problem.Problem.{NotFound, Raw, TypeName}
import com.histmersion.util.problem.Problems

import java.time.Instant

final class LogControllers(log: LogService, now: () => Instant):

  def events(filter: LogPage.Filter): Either[Problems, LogPage] =
    LogQuery.parse(filter, now()).map(log.page)

  def problem(id: LogEvent.ProblemId): Either[Problems, LogPage] =
    id.present
      .flatMap(log.problem(_, LogPage.Limit.max))
      .toRight(NotFound.Entity(TypeName.of(LogEvent.ProblemId), Raw.of(id)))
