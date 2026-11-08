package com.histmersion
package domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure

trait RetractionWatchClient:
  def csv(): Either[RetractionWatchClient.Problem, RetractionWatchClient.Csv]

object RetractionWatchClient:
  type Csv = Csv.T
  object Csv extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
