package com.histmersion
package cli

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A plugin id: the settings key (`histmersion.plugins.<id>`) and the source of the project's names. Stricter than the
  * host's `[a-z0-9][a-z0-9-]*`: it must also make a Scala class name.
  */
type PluginId = PluginId.T

object PluginId extends Newtype[String, Match["[a-z][a-z0-9]*(-[a-z0-9]+)*"]]:
  given Display[T] = Display(_.value)

  type PackageName = PackageName.T
  object PackageName extends Newtype[String, Pure]
  type TypeName = TypeName.T
  object TypeName extends Newtype[String, Pure]

  def parse(raw: String): Either[String, PluginId] =
    either(raw).left.map(_ =>
      s"'$raw' is not a plugin id: lower-case letters and digits in words joined by single " +
        "dashes, starting with a letter ([a-z][a-z0-9]*(-[a-z0-9]+)*)"
    )

  extension (id: PluginId)
    /** `my-mirror` → `MyMirror`. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def typeName: TypeName = TypeName(id.value.split('-').map(_.capitalize).mkString)

    /** `my-mirror` → `mymirror`. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def packageName: PackageName = PackageName(id.value.replace("-", ""))
