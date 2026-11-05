package com.histmersion.plugin

/** One line of `agent doctor`: `level` is `ok`, `warn` or `fail`. */
final case class AiCheck(name: AiCheck.Name, level: AiCheck.Level, detail: AiCheck.Detail)

object AiCheck:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Level = Level.T
  object Level extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
