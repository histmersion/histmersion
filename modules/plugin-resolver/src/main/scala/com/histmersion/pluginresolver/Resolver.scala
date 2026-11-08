package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Resolver:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type Argument = Argument.T
  object Argument extends RefinedType[String, Pure]
  type Error = Problem
  val Error: Problem.type = Problem
  type ExitCode = ExitCode.T
  object ExitCode extends RefinedType[Int, Pure]
  type FileName = FileName.T
  object FileName extends RefinedType[String, Pure]:
    given Ordering[T] = Ordering.String.on(_.value)
  type Sha256 = Sha256.T
  object Sha256 extends RefinedType[String, Pure]
  type ManifestLine = ManifestLine.T
  object ManifestLine extends RefinedType[String, Pure]
