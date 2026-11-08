package com.histmersion
package domain.library.acquisition

import com.histmersion.api.Problem
import com.histmersion.config.*
import com.histmersion.domain.*

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[Problem, RetractionWatchClient.Csv] =
    http
      .text(Http.Url(cfg.retractionWatch.csvUrl.value))
      .left
      .map(e => Problem(e.value))
      .map(t => RetractionWatchClient.Csv(t.value))
