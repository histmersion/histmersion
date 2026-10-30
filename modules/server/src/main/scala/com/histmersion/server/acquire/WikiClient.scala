package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

trait WikiClient:
  def refs(articleUrl: String): Either[String, WikirefsResult]
