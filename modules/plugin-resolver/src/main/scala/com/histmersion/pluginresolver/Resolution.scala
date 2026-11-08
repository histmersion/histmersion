package com.histmersion
package pluginresolver

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

/** The pure decisions of a resolution: which of a plugin's modules conflict with the server's, which go into the
  * plugins directory's `lib/`, and the files that record the outcome.
  */
object Resolution:
  type ClasspathFile = ClasspathFile.T
  object ClasspathFile extends Newtype[String, Pure]
  type InputsFile = InputsFile.T
  object InputsFile extends Newtype[String, Pure]
  type ReportLine = ReportLine.T
  object ReportLine extends Newtype[String, Pure]

  /** Orders two versions as Maven does: negative when the first is older. */
  export Resolver.{
    FileName as ConflictsPlugin,
    FileName as DependenciesNeededPart2,
    FileName as InputsFileFilesPart1,
    FileName as NeededByPerPluginPart1,
    FileName as NeededByResultPart2,
    Sha256 as InputsFileFilesPart2
  }

  type VersionOrder = (ModuleVersion, ModuleVersion) => Int

  /** The modules `plugin` resolves to on its own that the server ships at an OLDER version. An older version than the
    * server's is no conflict: resolved together, the server's is chosen, as Maven chooses the newest.
    */
  def conflicts(
      plugin: Resolution.ConflictsPlugin,
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
  def neededBy(
      perPlugin: List[(Resolution.NeededByPerPluginPart1, List[ModuleCoordinates])]
  ): Map[ModuleKey, List[Resolution.NeededByResultPart2]] =
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
      needed: Map[ModuleKey, List[Resolution.DependenciesNeededPart2]]
  ): List[Dependency] =
    joint
      .collect {
        case (c, Some(file)) if !plugins.contains(c.key) && !(server.provides(c.key)) =>
          Dependency(c, file, needed.getOrElse(c.key, Nil))
      }
      .sortBy(_.libName)

  /** The modules of a plugin's own resolution the server provides (shown, not installed). */
  def provided(resolved: List[ModuleCoordinates], plugins: Set[ModuleKey], server: ServerModules): List[ModuleKey] =
    resolved.map(_.key).filter(k => !plugins.contains(k) && (server.provides(k))).distinct.sortBy(_.toString)

  /** `lib/classpath`: the dependency jars' file names, one per line, in class-path order. */
  def classpathFile(deps: List[Dependency]): ClasspathFile = ClasspathFile(
    deps.map(_.libName).map((_).value + "\n").mkString
  )

  /** `lib/inputs`: `<sha256>  <file name>` of every plugin jar and POM the resolution was made from, sorted by name —
    * what `histmersion-server-jvm` compares with the plugins directory to know whether `lib/` is still current.
    */
  def inputsFile(files: List[(Resolution.InputsFileFilesPart1, Resolution.InputsFileFilesPart2)]): InputsFile =
    InputsFile(files.sortBy(_._1).map((name, sha) => s"$sha  $name\n").mkString)

  /** The lines `plugin add` and the relink show. */
  def report(deps: List[Dependency], provided: List[ModuleKey]): List[ReportLine] =
    (deps.map(d =>
      s"dependency: ${d.coordinates}" + (if d.neededBy.isEmpty then "" else s" (for ${d.neededBy.mkString(", ")})")
    ) ++
      (if provided.isEmpty then Nil
       else
         List(
           s"provided by Histmersion: ${provided.size} module(s) (${provided.take(6).mkString(", ")}" +
             (if provided.size > 6 then ", …)" else ")")
         ))).map(refactorValue3 => ReportLine(refactorValue3))
