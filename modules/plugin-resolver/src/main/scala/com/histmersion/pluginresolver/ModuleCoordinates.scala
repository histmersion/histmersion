package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

final case class ModuleCoordinates(key: ModuleKey, version: ModuleVersion):
  override def toString: String = s"$key:${version.value}"

object ModuleCoordinates:
  /** `group:artifact:version`. */
  def parse(raw: String): Either[String, ModuleCoordinates] = raw.trim.split(':').toList match
    case List(g, a, v) =>
      for
        key <- ModuleKey.parse(s"$g:$a")
        version <- ModuleVersion.either(v).left.map(e => s"'$raw': version $e")
      yield ModuleCoordinates(key, version)
    case _ => Left(s"not group:artifact:version: '$raw'")
