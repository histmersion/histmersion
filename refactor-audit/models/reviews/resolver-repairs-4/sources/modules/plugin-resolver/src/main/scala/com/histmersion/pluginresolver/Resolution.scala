package com.histmersion.pluginresolver

import java.nio.file.Path

/** The pure decisions of a resolution: which of a plugin's modules conflict with the server's, which go into the
  * plugins directory's `lib/`, and the files that record the outcome.
  */
object Resolution:

  /** Orders two versions as Maven does: negative when the first is older. */
  type ConflictsPlugin = _root_.com.histmersion.pluginresolver.Resolver.FileName
  val ConflictsPlugin: _root_.com.histmersion.pluginresolver.Resolver.FileName.type = _root_.com.histmersion.pluginresolver.Resolver.FileName
  type NeededByResultPart2 = _root_.com.histmersion.pluginresolver.Resolver.FileName
  val NeededByResultPart2: _root_.com.histmersion.pluginresolver.Resolver.FileName.type = _root_.com.histmersion.pluginresolver.Resolver.FileName
  type NeededByPerPluginPart1 = _root_.com.histmersion.pluginresolver.Resolver.FileName
  val NeededByPerPluginPart1: _root_.com.histmersion.pluginresolver.Resolver.FileName.type = _root_.com.histmersion.pluginresolver.Resolver.FileName
  type DependenciesNeededPart2 = _root_.com.histmersion.pluginresolver.Resolver.FileName
  val DependenciesNeededPart2: _root_.com.histmersion.pluginresolver.Resolver.FileName.type = _root_.com.histmersion.pluginresolver.Resolver.FileName
  type ClasspathFileResult = ClasspathFileResult.T
  object ClasspathFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputsFileResult = InputsFileResult.T
  object InputsFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputsFileFilesPart1 = _root_.com.histmersion.pluginresolver.Resolver.FileName
  val InputsFileFilesPart1: _root_.com.histmersion.pluginresolver.Resolver.FileName.type = _root_.com.histmersion.pluginresolver.Resolver.FileName
  type InputsFileFilesPart2 = _root_.com.histmersion.pluginresolver.Resolver.Sha256
  val InputsFileFilesPart2: _root_.com.histmersion.pluginresolver.Resolver.Sha256.type = _root_.com.histmersion.pluginresolver.Resolver.Sha256
  type ReportResult = ReportResult.T
  object ReportResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type VersionOrder = (ModuleVersion, ModuleVersion) => Int

  /** The modules `plugin` resolves to on its own that the server ships at an OLDER version. An older version than the
    * server's is no conflict: resolved together, the server's is chosen, as Maven chooses the newest.
    */
  def conflicts(
      plugin: com.histmersion.pluginresolver.Resolution.ConflictsPlugin,
      resolved: List[ModuleCoordinates],
      server: ServerModules,
      order: VersionOrder
  ): List[Conflict] =
    resolved
      .flatMap { c =>
        server.shipped
          .get(c.key)
          .filter(shipped => order(c.version, shipped) > 0)
          .map(Conflict(plugin, c.key, c.version, _))
      }
      .sortBy(_.key.toString)

  /** The plugin jars each module is there for: a module is needed by every plugin whose own resolution has it.
    */
  def neededBy(perPlugin: List[(com.histmersion.pluginresolver.Resolution.NeededByPerPluginPart1, List[ModuleCoordinates])]): Map[ModuleKey, List[com.histmersion.pluginresolver.Resolution.NeededByResultPart2]] =
    perPlugin
      .flatMap((plugin, modules) => modules.map(_.key -> plugin))
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.distinct.sorted)
      .toMap

  /** What `lib/` holds: every module of the joint resolution (the plugins together, the server's modules left out) that
    * is not a plugin itself, is not provided by the server and has a jar.
    */
  def dependencies(
      joint: List[(ModuleCoordinates, Option[Path])],
      plugins: Set[ModuleKey],
      server: ServerModules,
      needed: Map[ModuleKey, List[com.histmersion.pluginresolver.Resolution.DependenciesNeededPart2]]
  ): List[Dependency] =
    joint
      .collect {
        case (c, Some(file)) if !plugins.contains(c.key) && !(server.provides(c.key)).value =>
          Dependency(c, file, needed.getOrElse(c.key, Nil))
      }
      .sortBy(_.libName)

  /** The modules of a plugin's own resolution the server provides (shown, not installed). */
  def provided(resolved: List[ModuleCoordinates], plugins: Set[ModuleKey], server: ServerModules): List[ModuleKey] =
    resolved.map(_.key).filter(k => !plugins.contains(k) && (server.provides(k)).value).distinct.sortBy(_.toString)

  /** `lib/classpath`: the dependency jars' file names, one per line, in class-path order. */
  def classpathFile(deps: List[Dependency]): com.histmersion.pluginresolver.Resolution.ClasspathFileResult = _root_.com.histmersion.pluginresolver.Resolution.ClasspathFileResult(deps.map(_.libName).map((_).value + "\n").mkString)

  /** `lib/inputs`: `<sha256>  <file name>` of every plugin jar and POM the resolution was made from, sorted by name —
    * what `histmersion-server-jvm` compares with the plugins directory to know whether `lib/` is still current.
    */
  def inputsFile(files: List[(com.histmersion.pluginresolver.Resolution.InputsFileFilesPart1, com.histmersion.pluginresolver.Resolution.InputsFileFilesPart2)]): com.histmersion.pluginresolver.Resolution.InputsFileResult =
    _root_.com.histmersion.pluginresolver.Resolution.InputsFileResult(files.sortBy(_._1).map((name, sha) => s"$sha  $name\n").mkString)

  /** The lines `plugin add` and the relink show. */
  def report(deps: List[Dependency], provided: List[ModuleKey]): List[com.histmersion.pluginresolver.Resolution.ReportResult] =
    deps.map(d =>
      s"dependency: ${d.coordinates}" + (if d.neededBy.isEmpty then "" else s" (for ${d.neededBy.mkString(", ")})")
    ) ++
      (if provided.isEmpty then Nil
       else
         List(
           s"provided by Histmersion: ${provided.size} module(s) (${provided.take(6).mkString(", ")}" +
             (if provided.size > 6 then ", …)" else ")")
         ))
