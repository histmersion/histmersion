package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** What the server's class path holds (the relink kit's `modules`), and the modules the build keeps off every class
  * path (`excludes`): a plugin gets neither from its own dependencies.
  */
final case class ServerModules(shipped: Map[ModuleKey, ModuleVersion], excluded: Set[ModuleKey]):
  def provides(key: ModuleKey): Boolean = shipped.contains(key) || excluded.contains(key)

object ServerModules:
  /** The kit's two files, one entry per line; blank lines and `#` comments ignored. */
  def parse(modules: List[String], excludes: List[String]): Either[String, ServerModules] =
    def entries(lines: List[String]) = lines.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    for
      shipped <- traverse(entries(modules))(ModuleCoordinates.parse)
      excluded <- traverse(entries(excludes))(ModuleKey.parse)
    yield ServerModules(shipped.map(c => c.key -> c.version).toMap, excluded.toSet)
