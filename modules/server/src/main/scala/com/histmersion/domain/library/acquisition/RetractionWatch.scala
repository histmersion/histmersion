package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem

import com.histmersion.config.*
import com.histmersion.domain.*

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[Problem, RetractionWatchClient.Csv] =
    http
      .text(Http.Url(cfg.retractionWatch.csvUrl.value))
      .left
      .map(_.problem)
      .map(t => RetractionWatchClient.Csv(t.value))
