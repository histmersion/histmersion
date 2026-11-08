package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.constraint.any.Pure

final case class ModuleCoordinates(key: ModuleKey, version: ModuleVersion):
  override def toString: String = s"$key:${version.value}"

object ModuleCoordinates:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** `group:artifact:version`. */

  def parse(raw: Text): Either[Problem, ModuleCoordinates] = (raw).value.trim.split(':').toList match
    case List(g, a, v) =>
      for
        key <- ModuleKey.parse(ModuleKey.Text(s"$g:$a"))
        version <- ModuleVersion.either(v).left.map(e => Problem(s"'$raw': version $e"))
      yield ModuleCoordinates(key, version)
    case _ => Left(Problem.notCoordinates(raw))
