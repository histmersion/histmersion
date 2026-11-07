package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

final case class ModuleCoordinates(key: ModuleKey, version: ModuleVersion):
  override def toString: String = s"$key:${version.value}"

object ModuleCoordinates:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** `group:artifact:version`. */
  type ParseResultPart1 = _root_.com.histmersion.pluginresolver.Resolver.Problem
  val ParseResultPart1: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem

  def parse(raw: com.histmersion.pluginresolver.ModuleCoordinates.Text): Either[com.histmersion.pluginresolver.ModuleCoordinates.ParseResultPart1, ModuleCoordinates] = (raw).value.trim.split(':').toList match
    case List(g, a, v) =>
      for
        key <- ModuleKey.parse(_root_.com.histmersion.pluginresolver.ModuleKey.Text(s"$g:$a"))
        version <- ModuleVersion.either(v).left.map(e => _root_.com.histmersion.pluginresolver.Resolver.Problem(s"'$raw': version $e"))
      yield ModuleCoordinates(key, version)
    case _ => Left(_root_.com.histmersion.pluginresolver.ModuleCoordinates.ParseResultPart1(s"not group:artifact:version: '$raw'"))
