package com.histmersion
package pluginresolver

import com.histmersion.util.problem.{Legacy, Problem}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.constraint.any.Pure

/** A module whatever its version: what the server and a plugin can both have, once each. */
final case class ModuleKey(group: GroupId, artifact: ArtifactId):
  override def toString: String = s"${group.value}:${artifact.value}"

object ModuleKey:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** `group:artifact`. */

  def parse(raw: Text): Either[Problem, ModuleKey] = (raw).value.trim.split(':').toList match
    case List(g, a) =>
      (for group <- GroupId.either(g); artifact <- ArtifactId.either(a)
      yield ModuleKey(group, artifact)).left.map(refactorError => Problem.Unclassified(Legacy.Text(refactorError)))
    case _ => Left(Problem.Unclassified(Legacy.Text(s"not group:artifact: '$raw'")))
