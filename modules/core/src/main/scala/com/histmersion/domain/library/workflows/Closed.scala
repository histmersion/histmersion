package com.histmersion
package domain.library.workflows

import io.github.iltotore.iron.constraint.any.Pure

final case class Closed(line: Closed.Line, lint: List[Closed.Lint], acquire: Option[Closed.Acquire])

object Closed:
  type Line = Line.T
  object Line extends Newtype[String, Pure]
  type Lint = Lint.T
  object Lint extends Newtype[String, Pure]
  type Acquire = Acquire.T
  object Acquire extends Newtype[String, Pure]
