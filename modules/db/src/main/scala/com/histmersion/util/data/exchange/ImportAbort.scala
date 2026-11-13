package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import io.github.iltotore.iron.constraint.any.Pure

/** A failure inside an import's transaction that must roll it back and still be reported as a value. */
final class ImportAbort(val problems: List[Problem]) extends RuntimeException(problems.map(_.sentence).mkString("; "))
