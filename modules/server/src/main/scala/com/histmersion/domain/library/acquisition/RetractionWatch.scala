package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.lint.{LintRule, tolerated}

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def csv(): Either[Problem, RetractionWatchClient.Csv] =
    http
      .text(Http.Url(cfg.retractionWatch.csvUrl.value))
      .map(t => RetractionWatchClient.Csv(t.value))
