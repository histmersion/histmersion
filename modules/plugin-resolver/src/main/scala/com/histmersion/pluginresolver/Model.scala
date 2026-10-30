package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

type MavenNameRule = Match["[A-Za-z0-9_.\\-+]+"]

/** A Maven group id: `org.scala-lang.modules`. */
type GroupId = GroupId.T
object GroupId extends RefinedType[String, MavenNameRule]

/** A Maven artifact id, with the Scala suffix a Scala module carries: `scala-xml_3`. */
type ArtifactId = ArtifactId.T
object ArtifactId extends RefinedType[String, MavenNameRule]

/** A module's version as its POM writes it: `2.4.0`, `0.1.0-SNAPSHOT`. */
type ModuleVersion = ModuleVersion.T
object ModuleVersion extends RefinedType[String, MavenNameRule]

/** A module whatever its version: what the server and a plugin can both have, once each. */
final case class ModuleKey(group: GroupId, artifact: ArtifactId):
  override def toString: String = s"${group.value}:${artifact.value}"

object ModuleKey:
  /** `group:artifact`. */
  def parse(raw: String): Either[String, ModuleKey] = raw.trim.split(':').toList match
    case List(g, a) => for group <- GroupId.either(g); artifact <- ArtifactId.either(a) yield ModuleKey(group, artifact)
    case _          => Left(s"not group:artifact: '$raw'")

final case class ModuleCoordinates(key: ModuleKey, version: ModuleVersion):
  override def toString: String = s"$key:${version.value}"

object ModuleCoordinates:
  /** `group:artifact:version`. */
  def parse(raw: String): Either[String, ModuleCoordinates] = raw.trim.split(':').toList match
    case List(g, a, v) =>
      for
        key <- ModuleKey.parse(s"$g:$a")
        version <- ModuleVersion.either(v).left.map(e => s"'$raw': version $e")
      yield ModuleCoordinates(key, version)
    case _ => Left(s"not group:artifact:version: '$raw'")

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

/** A Maven repository the dependencies are looked up in: an `https://` or `file:` URL. */
type RepositoryUrl = RepositoryUrl.T
object RepositoryUrl extends RefinedType[String, Match["(https?|file):.+"]]:
  /** A URL as given, or a local directory as its `file:` URL. */
  def fromArgument(raw: String): Either[String, RepositoryUrl] =
    val trimmed = raw.trim
    if trimmed.matches("(https?|file):.+") then either(trimmed)
    else either(Path.of(trimmed).toAbsolutePath.normalize.toUri.toString)

/** A plugin jar to resolve the dependencies of, with the text of the POM that lists them when it was published with
  * one. Without a POM it has no dependencies of its own.
  */
final case class PluginArtifact(jar: Path, pom: Option[String]):
  def fileName: String = jar.getFileName.toString

/** One module a plugin's dependencies need that the server does not ship, and the plugin jars it is there for.
  */
final case class Dependency(coordinates: ModuleCoordinates, file: Path, neededBy: List[String]):
  /** The file name in the plugins directory's `lib/`: the group first, as two groups may publish artifacts of the same
    * name and version (`core_3-1.0.2.jar`).
    */
  def libName: String = s"${coordinates.key.group.value}-${file.getFileName.toString}"

/** A plugin needs a module the server ships, at a NEWER version than the server's. One class loader holds one version,
  * and the server's would win, so the plugin cannot be linked.
  */
final case class Conflict(plugin: String, key: ModuleKey, wanted: ModuleVersion, shipped: ModuleVersion):
  def message: String =
    s"$plugin needs $key ${wanted.value}, but this Histmersion ships ${shipped.value} — " +
      s"build the plugin against ${shipped.value} (the histmersion-plugin-platform POM), or install a Histmersion that ships ${wanted.value}"

private[pluginresolver] def traverse[A, B](as: List[A])(f: A => Either[String, B]): Either[String, List[B]] =
  as.foldRight[Either[String, List[B]]](Right(Nil))((a, acc) => for b <- f(a); bs <- acc yield b :: bs)
