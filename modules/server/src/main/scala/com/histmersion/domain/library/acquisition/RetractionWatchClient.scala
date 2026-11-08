package com.histmersion
package domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

trait RetractionWatchClient:
  def csv(): Either[RetractionWatchClient.Problem, RetractionWatchClient.Csv]

object RetractionWatchClient:
  type Csv = Csv.T
  object Csv extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
