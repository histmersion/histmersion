package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** One line of `agent doctor`: `level` is `ok`, `warn` or `fail`. */
final case class AiCheck(name: AiCheck.Name, level: AiCheck.Level, detail: AiCheck.Detail)

object AiCheck:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Level = Level.T
  object Level extends Newtype[String, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
