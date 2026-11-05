package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** One module a plugin's dependencies need that the server does not ship, and the plugin jars it is there for.
  */
final case class Dependency(coordinates: ModuleCoordinates, file: Path, neededBy: List[Dependency.NeededBy]):
  /** The file name in the plugins directory's `lib/`: the group first, as two groups may publish artifacts of the same
    * name and version (`core_3-1.0.2.jar`).
    */
  def libName: com.histmersion.pluginresolver.Dependency.LibNameResult = _root_.com.histmersion.pluginresolver.Dependency.LibNameResult(s"${coordinates.key.group.value}-${file.getFileName.toString}")

object Dependency:
  type LibNameResult = LibNameResult.T
  object LibNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NeededBy = NeededBy.T
  object NeededBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
