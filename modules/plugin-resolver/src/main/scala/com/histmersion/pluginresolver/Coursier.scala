package com.histmersion
package pluginresolver

import coursierapi.{Cache, Fetch, Logger, MavenRepository, Module, Repository, ResolutionParams, Version}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure

/** Resolution with coursier against Maven repositories, in the order given. The modules resolve to jars in coursier's
  * cache (`cache`, else coursier's own, `COURSIER_CACHE` or the platform's).
  */
final class Coursier(repositories: List[RepositoryUrl], cache: Option[Path]):

  /** The modules `roots` resolve to, the `excluded` modules and everything only they depend on left out, and each
    * module's jar (none for a module that is only a POM).
    */
  def resolve(
      roots: List[ModuleCoordinates],
      excluded: Set[ModuleKey]
  ): Either[Problem, List[(ModuleCoordinates, Option[Path])]] =
    val params = excluded.foldLeft(ResolutionParams.create())((p, k) => p.addExclusion(k.group.value, k.artifact.value))
    val fetch = Fetch
      .create()
      .withRepositories(repositories.map(r => MavenRepository.of(r.value): Repository)*)
      .withDependencies(roots.map(c => coursierapi.Dependency.of(module(c.key), c.version.value))*)
      .withResolutionParams(params)
      .withCache(cache.foldLeft(Cache.create().withLogger(Logger.nop()))((c, dir) => c.withLocation(dir.toFile)))
    Try(fetch.fetchResult()).toEither.left
      .map(e => Problem(Option(e.getMessage).getOrElse(e.getClass.getSimpleName)))
      .flatMap { result =>
        val jars = result.getArtifacts.asScala.toList.map(e => e.getKey.getUrl -> e.getValue.toPath)
        traverse(result.getDependencies.asScala.toList) { d =>
          ModuleCoordinates
            .parse(ModuleCoordinates.Text(s"${d.getModule.getOrganization}:${d.getModule.getName}:${d.getVersion}"))
            .map(c => c -> jars.collectFirst { case (url, file) if url.endsWith((Coursier.jarPath(c)).value) => file })
        }.map(_.distinctBy(_._1))
      }

  private def module(k: ModuleKey): Module = Module.of(k.group.value, k.artifact.value)

object Coursier:
  type JarPath = JarPath.T
  object JarPath extends Newtype[String, Pure]

  /** Maven's version order (`1.10` after `1.9`, `1.0-RC1` before `1.0`). */

  val order: Resolution.VersionOrder = (a, b) => Version.compare(a.value, b.value)

  /** The repository every resolution falls back to when none is named: Maven Central. */
  def central: RepositoryUrl = RepositoryUrl.applyUnsafe(Repository.central().getBase)

  /** Where a module's main jar sits in a Maven repository, from its root. */
  def jarPath(c: ModuleCoordinates): JarPath =
    val a = c.key.artifact.value
    val v = c.version.value
    JarPath(s"/${c.key.group.value.replace('.', '/')}/$a/$v/$a-$v.jar")

  /** Lay the plugins out as a Maven repository under `dir`, each at the coordinates its POM states, so that coursier
    * reads their POMs (parents, properties, dependency management) as it reads any other. The plugins without a POM are
    * left out: they have no dependencies of their own.
    */
  def pluginRepository(
      dir: Path,
      plugins: List[PluginArtifact]
  ): Either[Problem, List[(PluginArtifact, ModuleCoordinates)]] =
    traverse(plugins.flatMap(p => p.pom.map(p -> _))) { (plugin, pom) =>
      Pom.coordinates(pom).left.map(e => Problem(s"${plugin.fileName}: its POM: $e")).map { c =>
        val jar = dir.resolve((jarPath(c)).value.drop(1))
        Files.createDirectories(jar.getParent)
        Files.copy(plugin.jar, jar, StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(jar.resolveSibling(jar.getFileName.toString.stripSuffix(".jar") + ".pom"), (pom).value, UTF_8)
        plugin -> c
      }
    }
