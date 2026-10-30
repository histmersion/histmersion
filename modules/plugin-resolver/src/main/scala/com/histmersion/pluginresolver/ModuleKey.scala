package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A module whatever its version: what the server and a plugin can both have, once each. */
final case class ModuleKey(group: GroupId, artifact: ArtifactId):
  override def toString: String = s"${group.value}:${artifact.value}"

object ModuleKey:
  /** `group:artifact`. */
  def parse(raw: String): Either[String, ModuleKey] = raw.trim.split(':').toList match
    case List(g, a) => for group <- GroupId.either(g); artifact <- ArtifactId.either(a) yield ModuleKey(group, artifact)
    case _          => Left(s"not group:artifact: '$raw'")
