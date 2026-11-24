package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path
import com.histmersion.lint.{LintRule, tolerated}

/** One module a plugin's dependencies need that the server does not ship, and the plugin jars it is there for.
  */
final case class Dependency(coordinates: ModuleCoordinates, file: Path, neededBy: List[Dependency.NeededBy]):
  /** The file name in the plugins directory's `lib/`: the group first, as two groups may publish artifacts of the same
    * name and version (`core_3-1.0.2.jar`).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def libName: Resolver.FileName =
    Resolver.FileName(s"${coordinates.key.group.value}-${file.getFileName.toString}")

object Dependency:
  export Resolver.FileName as NeededBy
