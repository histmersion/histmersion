package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult

trait WikiClient:
  def refs(articleUrl: com.histmersion.domain.library.acquisition.WikiClient.ArticleUrl): Either[com.histmersion.domain.library.acquisition.WikiClient.Problem, WikirefsResult]

object WikiClient:
  type ArticleUrl = ArticleUrl.T
  object ArticleUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
