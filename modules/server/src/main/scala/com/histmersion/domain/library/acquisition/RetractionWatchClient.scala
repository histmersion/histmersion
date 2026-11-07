package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

trait RetractionWatchClient:
  def csv(): Either[com.histmersion.domain.library.acquisition.RetractionWatchClient.Problem, com.histmersion.domain.library.acquisition.RetractionWatchClient.Csv]

object RetractionWatchClient:
  type Csv = Csv.T
  object Csv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
