package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[com.histmersion.domain.library.acquisition.RetractionWatch.Problem, com.histmersion.domain.library.acquisition.RetractionWatch.Csv] = http.text(cfg.retractionWatch.csvUrl)

object RetractionWatch:
  type Csv = Csv.T
  object Csv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
