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
  type ParseResultPart1 = Resolver.Problem
  val ParseResultPart1: Resolver.Problem.type = Resolver.Problem

  def parse(raw: Text): Either[ParseResultPart1, ModuleCoordinates] = (raw).value.trim.split(':').toList match
    case List(g, a, v) =>
      for
        key <- ModuleKey.parse(ModuleKey.Text(s"$g:$a"))
        version <- ModuleVersion.either(v).left.map(e => Resolver.Problem(s"'$raw': version $e"))
      yield ModuleCoordinates(key, version)
    case _ => Left(ParseResultPart1(s"not group:artifact:version: '$raw'"))
