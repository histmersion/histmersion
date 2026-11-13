package com.histmersion
package pluginresolver

import com.histmersion.util.problem.{Problem, Problems}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.constraint.any.Pure

final case class ModuleCoordinates(key: ModuleKey, version: ModuleVersion):
  override def toString: String = s"$key:${version.value}"

object ModuleCoordinates:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** `group:artifact:version` as written. */
  type Written = Written.T
  object Written extends Newtype[String, Match["[A-Za-z0-9_.\\-+]+:[A-Za-z0-9_.\\-+]+:[A-Za-z0-9_.\\-+]+"]]

  /** `group:artifact:version`. */
  def parse(raw: Text): Either[Problems, ModuleCoordinates] = (raw).value.trim.split(':').toList match
    case List(g, a, v) =>
      ModuleKey.parse(ModuleKey.Text(s"$g:$a")).and(ModuleVersion.parse(v)).map(ModuleCoordinates(_, _))
    case _ => Left(Problem.Invalid.malformed(Written.refused(s"$raw".trim)))
