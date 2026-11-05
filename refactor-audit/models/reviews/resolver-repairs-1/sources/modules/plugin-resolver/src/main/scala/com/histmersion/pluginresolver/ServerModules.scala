package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** What the server's class path holds (the relink kit's `modules`), and the modules the build keeps off every class
  * path (`excludes`): a plugin gets neither from its own dependencies.
  */
final case class ServerModules(shipped: Map[ModuleKey, ModuleVersion], excluded: Set[ModuleKey]):
  def provides(key: ModuleKey): com.histmersion.pluginresolver.ServerModules.ProvidesResult = _root_.com.histmersion.pluginresolver.ServerModules.ProvidesResult(shipped.contains(key) || excluded.contains(key))

object ServerModules:
  /** The kit's two files, one entry per line; blank lines and `#` comments ignored. */
  type ParseEntriesLines = ParseEntriesLines.T
  object ParseEntriesLines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ProvidesResult = ProvidesResult.T
  object ProvidesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseModules = ParseModules.T
  object ParseModules extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseExcludes = ParseExcludes.T
  object ParseExcludes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(modules: List[com.histmersion.pluginresolver.ServerModules.ParseModules], excludes: List[com.histmersion.pluginresolver.ServerModules.ParseExcludes]): Either[com.histmersion.pluginresolver.ServerModules.ParseResultPart1, ServerModules] =
    def entries(lines: List[com.histmersion.pluginresolver.ServerModules.ParseEntriesLines]) = lines.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    for
      shipped <- traverse(entries(modules))(ModuleCoordinates.parse)
      excluded <- traverse(entries(excludes))(ModuleKey.parse)
    yield ServerModules(shipped.map(c => c.key -> c.version).toMap, excluded.toSet)
