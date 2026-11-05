package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

final case class ModuleCoordinates(key: ModuleKey, version: ModuleVersion):
  override def toString: String = s"$key:${version.value}"

object ModuleCoordinates:
  /** `group:artifact:version`. */
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(raw: com.histmersion.pluginresolver.ModuleCoordinates.ParseRaw): Either[com.histmersion.pluginresolver.ModuleCoordinates.ParseResultPart1, ModuleCoordinates] = (raw).value.trim.split(':').toList match
    case List(g, a, v) =>
      for
        key <- ModuleKey.parse(s"$g:$a")
        version <- ModuleVersion.either(v).left.map(e => s"'$raw': version $e")
      yield ModuleCoordinates(key, version)
    case _ => Left(_root_.com.histmersion.pluginresolver.ModuleCoordinates.ParseResultPart1(s"not group:artifact:version: '$raw'"))
