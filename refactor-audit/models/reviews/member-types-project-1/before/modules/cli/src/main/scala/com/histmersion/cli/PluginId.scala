package com.histmersion.cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A plugin id: the settings key (`histmersion.plugins.<id>`) and the source of the project's names. Stricter than the
  * host's `[a-z0-9][a-z0-9-]*`: it must also make a Scala class name.
  */
type PluginId = PluginId.T

object PluginId extends RefinedType[String, Match["[a-z][a-z0-9]*(-[a-z0-9]+)*"]]:
  def parse(raw: String): Either[String, PluginId] =
    either(raw).left.map(_ =>
      s"'$raw' is not a plugin id: lower-case letters and digits in words joined by single " +
        "dashes, starting with a letter ([a-z][a-z0-9]*(-[a-z0-9]+)*)"
    )

  extension (id: PluginId)
    /** `my-mirror` → `MyMirror`. */
    def typeName: String = id.value.split('-').map(_.capitalize).mkString

    /** `my-mirror` → `mymirror`. */
    def packageName: String = id.value.replace("-", "")
