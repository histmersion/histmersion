package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult

trait WikiClient:
  def refs(articleUrl: com.histmersion.domain.library.acquisition.WikiClient.RefsArticleUrl): Either[com.histmersion.domain.library.acquisition.WikiClient.RefsResultPart1, WikirefsResult]

object WikiClient:
  type RefsResultPart1 = RefsResultPart1.T
  object RefsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsArticleUrl = RefsArticleUrl.T
  object RefsArticleUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
