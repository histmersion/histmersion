package com.histmersion
package cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

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
  def signingOn: Boolean = scala.util.Try(Files.size(data.resolve("signing").resolve("gpg-key")) > 0).getOrElse(false)

  /** The Maven repository a plugin compiles against: the plugin API and the plugin platform (`maven/`). */
  def mavenRepo: Path = home.resolve("maven")
  def apiJar: Path =
    mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.PathName(PluginArtifacts.Api), ".jar").value)
  def apiSources: Path =
    mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.PathName(PluginArtifacts.Api), "-sources.jar").value)
  def apiPom: Path =
    mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.PathName(PluginArtifacts.Api), ".pom").value)

  /** The plugins' own dependencies, which `histmersion-relink --resolve` installs. */
  def lib: Path = plugins.resolve("lib")

  /** What the scripts need to act on THIS install and data directory, whatever the caller's environment. */
  def env: Map[LocalInstall.EnvVariable, LocalInstall.EnvSetting] =
    Map(
      LocalInstall.EnvVariable("HISTMERSION_HOME") -> LocalInstall.EnvSetting(home.toString),
      LocalInstall.EnvVariable("HISTMERSION_DATA_DIR") -> LocalInstall.EnvSetting(data.toString)
    )

object LocalInstall:
  type EnvSetting = EnvSetting.T
  object EnvSetting extends Newtype[String, Pure]
  type EnvVariable = EnvVariable.T
  object EnvVariable extends Newtype[String, Pure]

  /** The install: `cli.plugins.install-dir`, else the one this executable sits in (`<home>/bin/histmersion`, through
    * the `~/bin` symlink), else the platform default. It must hold the relink kit.
    */

  def locate(
      cfg: CliPluginsConfig,
      dataDir: RuntimeConfig.DataDir,
      env: UserPaths.Lookup,
      osName: UserPaths.OsName,
      exe: Option[Path]
  ): Either[Cli.Error.Message, LocalInstall] =
    def kitAt(p: Path) = Files.isRegularFile(p.resolve("bin").resolve("histmersion-relink"))
    val home = env(Environment.VariablesKey("HOME")).map(_.value).getOrElse("")
    val configured = Option(cfg.installDir)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(if p == "~" || p.startsWith("~/") then home + p.drop(1) else p))
    val fromExe = exe
      .flatMap(e => scala.util.Try(e.toRealPath()).toOption)
      .flatMap(e => Option(e.getParent))
      .flatMap(b => Option(b.getParent))
      .filter(kitAt)
    val default =
      if UserPaths.isMac(osName) then Path.of(home, "Applications", "Histmersion")
      else Path.of(home, ".local", "opt", "histmersion")
    val install = configured.orElse(fromExe).getOrElse(default)
    for
      d <- UserPaths
        .dataDir(dataDir, env, osName)
        .toRight(Cli.Error.Message("no data directory (HOME is unset; set HISTMERSION_DATA_DIR)"))
      _ <- Either.cond(
        kitAt(install),
        (),
        Cli.Error.Message(
          s"no Histmersion install with a relink kit at $install " +
            "(set cli.plugins.install-dir / HISTMERSION_INSTALL_DIR, or install a bundle built with the kit)"
        )
      )
    yield LocalInstall(install.toAbsolutePath.normalize, d.toAbsolutePath.normalize)
