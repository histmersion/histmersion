package com.histmersion.util.data.exchange

/** A failure inside an import's transaction that must roll it back and still be reported as a value. */
final class ImportAbort(val problems: List[ImportAbort.Problem]) extends RuntimeException(problems.mkString("; "))

object ImportAbort:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
