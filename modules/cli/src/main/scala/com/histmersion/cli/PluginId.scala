package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A plugin id: the settings key (`histmersion.plugins.<id>`) and the source of the project's names. Stricter than the
  * host's `[a-z0-9][a-z0-9-]*`: it must also make a Scala class name.
  */
type PluginId = PluginId.T

object PluginId extends RefinedType[String, Match["[a-z][a-z0-9]*(-[a-z0-9]+)*"]]:
  type PackageName = PackageName.T
  object PackageName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypeName = TypeName.T
  object TypeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(raw: com.histmersion.cli.PluginId.Text): Either[com.histmersion.cli.PluginId.Problem, PluginId] =
    either(raw).left.map(_ =>
      s"'$raw' is not a plugin id: lower-case letters and digits in words joined by single " +
        "dashes, starting with a letter ([a-z][a-z0-9]*(-[a-z0-9]+)*)"
    )

  extension (id: PluginId)
    /** `my-mirror` → `MyMirror`. */
    def typeName: com.histmersion.cli.PluginId.TypeName = id.value.split('-').map(_.capitalize).mkString

    /** `my-mirror` → `mymirror`. */
    def packageName: com.histmersion.cli.PluginId.PackageName = id.value.replace("-", "")
