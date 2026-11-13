package com.histmersion
package pluginresolver

import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problems.traverseAll
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** What the server's class path holds (the relink kit's `modules`), and the modules the build keeps off every class
  * path (`excludes`): a plugin gets neither from its own dependencies.
  */
final case class ServerModules(shipped: Map[ModuleKey, ModuleVersion], excluded: Set[ModuleKey]):
  def provides(key: ModuleKey): Boolean = (shipped.contains(key) || excluded.contains(key))

object ServerModules:
  /** The kit's two files, one entry per line; blank lines and `#` comments ignored. */
  type ParseEntriesLines = Resolver.ManifestLine
  val ParseEntriesLines: Resolver.ManifestLine.type = Resolver.ManifestLine

  type ParseModules = Resolver.ManifestLine
  val ParseModules: Resolver.ManifestLine.type = Resolver.ManifestLine
  type ParseExcludes = Resolver.ManifestLine
  val ParseExcludes: Resolver.ManifestLine.type = Resolver.ManifestLine

  def parse(modules: List[ParseModules], excludes: List[ParseExcludes]): Either[Problems, ServerModules] =
    def entries(lines: List[ParseEntriesLines]) =
      lines.map((_).value.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    entries(modules)
      .traverseAll(m => ModuleCoordinates.parse(ModuleCoordinates.Text(m)))
      .and(entries(excludes).traverseAll(e => ModuleKey.parse(ModuleKey.Text(e))))
      .map((shipped, excluded) => ServerModules(shipped.map(c => c.key -> c.version).toMap, excluded.toSet))
