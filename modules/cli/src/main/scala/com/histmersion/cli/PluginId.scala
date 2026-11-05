package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A plugin id: the settings key (`histmersion.plugins.<id>`) and the source of the project's names. Stricter than the
  * host's `[a-z0-9][a-z0-9-]*`: it must also make a Scala class name.
  */
type PluginId = PluginId.T

object PluginId extends RefinedType[String, Match["[a-z][a-z0-9]*(-[a-z0-9]+)*"]]:
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeNameResult = TypeNameResult.T
  object TypeNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PackageNameResult = PackageNameResult.T
  object PackageNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(raw: com.histmersion.cli.PluginId.ParseRaw): Either[com.histmersion.cli.PluginId.ParseResultPart1, PluginId] =
    either(raw).left.map(_ =>
      s"'$raw' is not a plugin id: lower-case letters and digits in words joined by single " +
        "dashes, starting with a letter ([a-z][a-z0-9]*(-[a-z0-9]+)*)"
    )

  extension (id: PluginId)
    /** `my-mirror` → `MyMirror`. */
    def typeName: com.histmersion.cli.PluginId.TypeNameResult = id.value.split('-').map(_.capitalize).mkString

    /** `my-mirror` → `mymirror`. */
    def packageName: com.histmersion.cli.PluginId.PackageNameResult = id.value.replace("-", "")
