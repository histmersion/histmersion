package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Resolver:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Argument = Argument.T
  object Argument extends Newtype[String, Pure]
  type Error = Problem
  val Error: Problem.type = Problem
  type ExitCode = ExitCode.T
  object ExitCode extends Newtype[Int, Pure]
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]
  type Sha256 = Sha256.T
  object Sha256 extends Newtype[String, Pure]
  type ManifestLine = ManifestLine.T
  object ManifestLine extends Newtype[String, Pure]
