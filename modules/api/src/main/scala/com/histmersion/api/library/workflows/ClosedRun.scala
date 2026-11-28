package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class ClosedRun(line: ClosedRun.Line, lint: List[ClosedRun.Lint], acquire: Option[ClosedRun.Acquire]) derives JsonCodec

object ClosedRun:
  type Line = Line.T
  object Line extends Newtype[String, Pure]
  type Lint = Lint.T
  object Lint extends Newtype[String, Pure]
  type Acquire = Acquire.T
  object Acquire extends Newtype[String, Pure]
