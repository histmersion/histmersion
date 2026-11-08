package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One line of `agent doctor`: `level` is `ok`, `warn` or `fail`. */
final case class AiCheck(name: AiCheck.Name, level: AiCheck.Level, detail: AiCheck.Detail)

object AiCheck:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Level = Level.T
  object Level extends RefinedType[String, Pure]
  type Detail = Detail.T
  object Detail extends RefinedType[String, Pure]
