package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

trait RetractionWatchClient:
  def csv(): Either[String, String]
