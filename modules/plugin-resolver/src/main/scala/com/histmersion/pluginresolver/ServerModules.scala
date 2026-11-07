package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** What the server's class path holds (the relink kit's `modules`), and the modules the build keeps off every class
  * path (`excludes`): a plugin gets neither from its own dependencies.
  */
final case class ServerModules(shipped: Map[ModuleKey, ModuleVersion], excluded: Set[ModuleKey]):
  def provides(key: ModuleKey): Boolean = (shipped.contains(key) || excluded.contains(key))

object ServerModules:
  /** The kit's two files, one entry per line; blank lines and `#` comments ignored. */
  type ParseEntriesLines = _root_.com.histmersion.pluginresolver.Resolver.ManifestLine
  val ParseEntriesLines: _root_.com.histmersion.pluginresolver.Resolver.ManifestLine.type = _root_.com.histmersion.pluginresolver.Resolver.ManifestLine

  type ParseResultPart1 = _root_.com.histmersion.pluginresolver.Resolver.Problem
  val ParseResultPart1: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem
  type ParseModules = _root_.com.histmersion.pluginresolver.Resolver.ManifestLine
  val ParseModules: _root_.com.histmersion.pluginresolver.Resolver.ManifestLine.type = _root_.com.histmersion.pluginresolver.Resolver.ManifestLine
  type ParseExcludes = _root_.com.histmersion.pluginresolver.Resolver.ManifestLine
  val ParseExcludes: _root_.com.histmersion.pluginresolver.Resolver.ManifestLine.type = _root_.com.histmersion.pluginresolver.Resolver.ManifestLine

  def parse(modules: List[com.histmersion.pluginresolver.ServerModules.ParseModules], excludes: List[com.histmersion.pluginresolver.ServerModules.ParseExcludes]): Either[com.histmersion.pluginresolver.ServerModules.ParseResultPart1, ServerModules] =
    def entries(lines: List[com.histmersion.pluginresolver.ServerModules.ParseEntriesLines]) = lines.map((_).value.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    for
      shipped <- traverse(entries(modules))({ val refactorFunction = ModuleCoordinates.parse; (refactorValue5) => refactorFunction(_root_.com.histmersion.pluginresolver.ModuleCoordinates.Text(refactorValue5)) })
      excluded <- traverse(entries(excludes))({ val refactorFunction2 = ModuleKey.parse; (refactorValue) => refactorFunction2(_root_.com.histmersion.pluginresolver.ModuleKey.Text(refactorValue)) })
    yield ServerModules(shipped.map(c => c.key -> c.version).toMap, excluded.toSet)
