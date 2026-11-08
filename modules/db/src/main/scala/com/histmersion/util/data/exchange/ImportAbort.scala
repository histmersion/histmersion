package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A failure inside an import's transaction that must roll it back and still be reported as a value. */
final class ImportAbort(val problems: List[ImportAbort.Problem]) extends RuntimeException(problems.mkString("; "))

object ImportAbort:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
