package com.histmersion
package domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult
import io.github.iltotore.iron.constraint.any.Pure

trait WikiClient:
  def refs(articleUrl: WikiClient.ArticleUrl): Either[WikiClient.Problem, WikirefsResult]

object WikiClient:
  type ArticleUrl = ArticleUrl.T
  object ArticleUrl extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
