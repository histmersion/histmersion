package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

trait RetractionWatchClient:
  def csv(): Either[String, String]
