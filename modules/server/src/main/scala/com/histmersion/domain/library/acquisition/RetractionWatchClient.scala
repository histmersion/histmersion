package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem

import com.histmersion.config.*
import com.histmersion.domain.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
trait RetractionWatchClient:
  def csv(): Either[Problem, RetractionWatchClient.Csv]

object RetractionWatchClient:
  type Csv = Csv.T
  object Csv extends Newtype[String, Pure]
