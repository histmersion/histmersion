package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The Histmersion installed on THIS machine, as the plugin commands see it: the bundle (`bin/`, `kit/`) and the data
  * directory (`plugins/`, `signing/`).
  */
final case class LocalInstall(home: Path, data: Path):
  def bin: Path = home.resolve("bin")
  def relink: Path = bin.resolve("histmersion-relink")
  def signing: Path = bin.resolve("histmersion-signing")
  def kitJson: Path = home.resolve("kit").resolve("kit.json")
  def record: Path = bin.resolve("histmersion-server.plugin-set.json")
  def plugins: Path = data.resolve("plugins")
  def index: Path = plugins.resolve("index.json")
  def staging: Path = plugins.resolve(".staging")
  def signingOn: com.histmersion.cli.LocalInstall.SigningOnResult = scala.util.Try(Files.size(data.resolve("signing").resolve("gpg-key")) > 0).getOrElse(false)

  /** The Maven repository a plugin compiles against: the plugin API and the plugin platform (`maven/`). */
  def mavenRepo: Path = home.resolve("maven")
  def apiJar: Path = mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.Api, ".jar"))
  def apiSources: Path = mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.Api, "-sources.jar"))
  def apiPom: Path = mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.Api, ".pom"))

  /** The plugins' own dependencies, which `histmersion-relink --resolve` installs. */
  def lib: Path = plugins.resolve("lib")

  /** What the scripts need to act on THIS install and data directory, whatever the caller's environment. */
  def env: Map[com.histmersion.cli.LocalInstall.EnvResultPart1, com.histmersion.cli.LocalInstall.EnvResultPart2] = Map("HISTMERSION_HOME" -> home.toString, "HISTMERSION_DATA_DIR" -> data.toString)

object LocalInstall:
  /** The install: `cli.plugins.install-dir`, else the one this executable sits in (`<home>/bin/histmersion`, through
    * the `~/bin` symlink), else the platform default. It must hold the relink kit.
    */
  type SigningOnResult = SigningOnResult.T
  object SigningOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvResultPart1 = EnvResultPart1.T
  object EnvResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvResultPart2 = EnvResultPart2.T
  object EnvResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateResultPart1 = LocateResultPart1.T
  object LocateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateDataDir = LocateDataDir.T
  object LocateDataDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateEnvArg1 = LocateEnvArg1.T
  object LocateEnvArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateEnvResult = LocateEnvResult.T
  object LocateEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocateOsName = LocateOsName.T
  object LocateOsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def locate(
      cfg: CliPluginsConfig,
      dataDir: com.histmersion.cli.LocalInstall.LocateDataDir,
      env: com.histmersion.cli.LocalInstall.LocateEnvArg1 => Option[com.histmersion.cli.LocalInstall.LocateEnvResult],
      osName: com.histmersion.cli.LocalInstall.LocateOsName,
      exe: Option[Path]
  ): Either[com.histmersion.cli.LocalInstall.LocateResultPart1, LocalInstall] =
    def kitAt(p: Path) = Files.isRegularFile(p.resolve("bin").resolve("histmersion-relink"))
    val home = env("HOME").getOrElse("")
    val configured = Option(cfg.installDir)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(if p == "~" || p.startsWith("~/") then home + p.drop(1) else p))
    val fromExe = exe
      .flatMap(e => scala.util.Try(e.toRealPath()).toOption)
      .flatMap(e => Option(e.getParent))
      .flatMap(b => Option(b.getParent))
      .filter(kitAt)
    val default =
      if osName.toLowerCase.contains("mac") then Path.of(home, "Applications", "Histmersion")
      else Path.of(home, ".local", "opt", "histmersion")
    val install = configured.orElse(fromExe).getOrElse(default)
    for
      d <- UserPaths
        .dataDir(dataDir, env, osName)
        .toRight("no data directory (HOME is unset; set HISTMERSION_DATA_DIR)")
      _ <- Either.cond(
        kitAt(install),
        (),
        s"no Histmersion install with a relink kit at $install " +
          "(set cli.plugins.install-dir / HISTMERSION_INSTALL_DIR, or install a bundle built with the kit)"
      )
    yield LocalInstall(install.toAbsolutePath.normalize, d.toAbsolutePath.normalize)
