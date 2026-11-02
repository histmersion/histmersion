package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

final class RetractionWatch(cfg: AcquireConfig, http: Http) extends RetractionWatchClient:
  def csv(): Either[String, String] = http.text(cfg.retractionWatch.csvUrl)
