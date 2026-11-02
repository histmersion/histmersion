package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult

trait WikiClient:
  def refs(articleUrl: String): Either[String, WikirefsResult]
