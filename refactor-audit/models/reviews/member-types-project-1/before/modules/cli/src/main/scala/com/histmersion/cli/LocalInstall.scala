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
  def signingOn: Boolean = scala.util.Try(Files.size(data.resolve("signing").resolve("gpg-key")) > 0).getOrElse(false)

  /** The Maven repository a plugin compiles against: the plugin API and the plugin platform (`maven/`). */
  def mavenRepo: Path = home.resolve("maven")
  def apiJar: Path = mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.Api, ".jar"))
  def apiSources: Path = mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.Api, "-sources.jar"))
  def apiPom: Path = mavenRepo.resolve(PluginArtifacts.path(PluginArtifacts.Api, ".pom"))

  /** The plugins' own dependencies, which `histmersion-relink --resolve` installs. */
  def lib: Path = plugins.resolve("lib")

  /** What the scripts need to act on THIS install and data directory, whatever the caller's environment. */
  def env: Map[String, String] = Map("HISTMERSION_HOME" -> home.toString, "HISTMERSION_DATA_DIR" -> data.toString)

object LocalInstall:
  /** The install: `cli.plugins.install-dir`, else the one this executable sits in (`<home>/bin/histmersion`, through
    * the `~/bin` symlink), else the platform default. It must hold the relink kit.
    */
  def locate(
      cfg: CliPluginsConfig,
      dataDir: String,
      env: String => Option[String],
      osName: String,
      exe: Option[Path]
  ): Either[String, LocalInstall] =
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
