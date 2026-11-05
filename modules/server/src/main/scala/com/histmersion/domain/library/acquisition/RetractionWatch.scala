package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[com.histmersion.domain.library.acquisition.RetractionWatch.CsvResultPart1, com.histmersion.domain.library.acquisition.RetractionWatch.CsvResultPart2] = http.text(cfg.retractionWatch.csvUrl)

object RetractionWatch:
  type CsvResultPart1 = CsvResultPart1.T
  object CsvResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CsvResultPart2 = CsvResultPart2.T
  object CsvResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
