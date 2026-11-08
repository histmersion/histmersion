package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

object Resolver:
  type Argument = Argument.T
  object Argument extends Newtype[String, Pure]
  type ExitCode = ExitCode.T
  object ExitCode extends Newtype[Int, Pure]
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]
  type Sha256 = Sha256.T
  object Sha256 extends Newtype[String, Pure]
  type ManifestLine = ManifestLine.T
  object ManifestLine extends Newtype[String, Pure]
