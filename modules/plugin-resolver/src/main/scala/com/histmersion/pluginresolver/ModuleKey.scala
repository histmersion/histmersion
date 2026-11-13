package com.histmersion
package pluginresolver

import com.histmersion.util.problem.{Problem, Problems}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.constraint.any.Pure

/** A module whatever its version: what the server and a plugin can both have, once each. */
final case class ModuleKey(group: GroupId, artifact: ArtifactId):
  override def toString: String = s"${group.value}:${artifact.value}"

object ModuleKey:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** `group:artifact` as written. */
  type Written = Written.T
  object Written extends Newtype[String, Match["[A-Za-z0-9_.\\-+]+:[A-Za-z0-9_.\\-+]+"]]

  /** `group:artifact`. */
  def parse(raw: Text): Either[Problems, ModuleKey] = (raw).value.trim.split(':').toList match
    case List(g, a) => GroupId.parse(g).and(ArtifactId.parse(a)).map(ModuleKey(_, _))
    case _          => Left(Problem.Invalid.malformed(Written.refused(s"$raw".trim)))
