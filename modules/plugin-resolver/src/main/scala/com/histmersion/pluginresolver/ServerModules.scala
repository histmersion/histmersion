package com.histmersion
package pluginresolver

import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problems.traverseAll
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import com.histmersion.lint.{LintRule, tolerated}

/** What the server's class path holds (the relink kit's `modules`), and the modules the build keeps off every class
  * path (`excludes`): a plugin gets neither from its own dependencies.
  */
final case class ServerModules(shipped: Map[ModuleKey, ModuleVersion], excluded: Set[ModuleKey]):
  def provides(key: ModuleKey): Boolean = (shipped.contains(key) || excluded.contains(key))

object ServerModules:
  /** The kit's two files, one entry per line; blank lines and `#` comments ignored. */
  export Resolver.{ManifestLine as ParseEntriesLines, ManifestLine as ParseExcludes, ManifestLine as ParseModules}

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(modules: List[ParseModules], excludes: List[ParseExcludes]): Either[Problems, ServerModules] =
    def entries(lines: List[ParseEntriesLines]) =
      lines.map((_).value.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    entries(modules)
      .traverseAll(m => ModuleCoordinates.parse(ModuleCoordinates.Text(m)))
      .and(entries(excludes).traverseAll(e => ModuleKey.parse(ModuleKey.Text(e))))
      .map((shipped, excluded) => ServerModules(shipped.map(c => c.key -> c.version).toMap, excluded.toSet))
