package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Closed(line: Closed.Line, lint: List[Closed.Lint], acquire: Option[Closed.Acquire]) derives JsonCodec

object Closed:
  type Line = Line.T
  object Line extends Newtype[String, Pure]
  type Lint = Lint.T
  object Lint extends Newtype[String, Pure]
  type Acquire = Acquire.T
  object Acquire extends Newtype[String, Pure]
