package com.histmersion
package pluginresolver

import com.histmersion.util.problem.{Legacy, Problem}
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
        version <- ModuleVersion.either(v).left.map(e => Problem.Unclassified(Legacy.Text(s"'$raw': version $e")))
      yield ModuleCoordinates(key, version)
    case _ => Left(Problem.Unclassified(Legacy.Text(s"not group:artifact:version: '$raw'")))
