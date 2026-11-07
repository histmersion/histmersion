package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A module whatever its version: what the server and a plugin can both have, once each. */
final case class ModuleKey(group: GroupId, artifact: ArtifactId):
  override def toString: String = s"${group.value}:${artifact.value}"

object ModuleKey:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** `group:artifact`. */
  type ParseResultPart1 = _root_.com.histmersion.pluginresolver.Resolver.Problem
  val ParseResultPart1: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem

  def parse(raw: com.histmersion.pluginresolver.ModuleKey.Text): Either[com.histmersion.pluginresolver.ModuleKey.ParseResultPart1, ModuleKey] = (raw).value.trim.split(':').toList match
    case List(g, a) => (for group <- GroupId.either(g); artifact <- ArtifactId.either(a) yield ModuleKey(group, artifact)).left.map(refactorError => _root_.com.histmersion.pluginresolver.Resolver.Problem(refactorError))
    case _          => Left(_root_.com.histmersion.pluginresolver.ModuleKey.ParseResultPart1(s"not group:artifact: '$raw'"))
