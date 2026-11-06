package com.histmersion.config

import scala.annotation.tailrec
import org.ekrich.config.Config

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

/** Where Histmersion keeps its per-user files: the data directory (the bundle's cluster, logs and backups; the agent
  * trees) and the user config file. ONE resolver, so the server, the bundle supervisor, the agent trees and the CLI
  * cannot disagree about a path. Built once by the entry point from the environment and the `os.name` property.
  */
final case class UserPaths(environment: Environment, osName: UserPaths.OsName):
  def isMac: Boolean = UserPaths.isMac(osName)
  def defaultDataDir: Option[Path] = UserPaths.defaultDataDir(environment.lookup, osName)
  def dataDir(configured: com.histmersion.config.RuntimeConfig.DataDir): Option[Path] = UserPaths.dataDir(configured, environment.lookup, osName)
  def cacheDir: Option[Path] = UserPaths.cacheDir(environment.lookup)
  def configFile: Option[Path] = UserPaths.configFile(environment.lookup)

object UserPaths:
  type BasemapDir = BasemapDir.T
  object BasemapDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvVar = EnvVar.T
  object EnvVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HomeDir = HomeDir.T
  object HomeDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Names the config file outright, wherever it is. */
  type HomeEnvResult = HomeEnvResult.T
  object HomeEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultDataDirEnvResult = DefaultDataDirEnvResult.T
  object DefaultDataDirEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultDataDirOsName = _root_.com.histmersion.config.UserPaths.OsName
  val DefaultDataDirOsName: _root_.com.histmersion.config.UserPaths.OsName.type = _root_.com.histmersion.config.UserPaths.OsName
  type DataDirEnvResult = DataDirEnvResult.T
  object DataDirEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DataDirOsName = _root_.com.histmersion.config.UserPaths.OsName
  val DataDirOsName: _root_.com.histmersion.config.UserPaths.OsName.type = _root_.com.histmersion.config.UserPaths.OsName
  type CacheDirEnvResult = CacheDirEnvResult.T
  object CacheDirEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BasemapDirEnvResult = BasemapDirEnvResult.T
  object BasemapDirEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigFileEnvResult = ConfigFileEnvResult.T
  object ConfigFileEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OsName = OsName.T
  object OsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ConfigEnv = "HISTMERSION_CONFIG"

  val ConfigFileName = "histmersion.conf"

  def isMac(osName: com.histmersion.config.UserPaths.OsName): Boolean = osName.toLowerCase.contains("mac")

  private def home(env: com.histmersion.config.UserPaths.EnvVar => Option[com.histmersion.config.UserPaths.HomeEnvResult]): Option[com.histmersion.config.UserPaths.HomeDir] = env("HOME").map(_.trim).filter(_.nonEmpty)

  /** The platform's per-user data directory: `~/Library/Application Support/Histmersion` on macOS,
    * `$XDG_DATA_HOME/histmersion` (default `~/.local/share/histmersion`) elsewhere.
    */
  def defaultDataDir(env: com.histmersion.config.UserPaths.EnvVar => Option[com.histmersion.config.UserPaths.DefaultDataDirEnvResult], osName: com.histmersion.config.UserPaths.DefaultDataDirOsName): Option[Path] =
    if isMac(osName) then home(env).map(h => Path.of(h, "Library", "Application Support", "Histmersion"))
    else
      env("XDG_DATA_HOME")
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(Path.of(_, "histmersion"))
        .orElse(home(env).map(h => Path.of(h, ".local", "share", "histmersion")))

  /** `configured` (`runtime.data-dir`) when it is set, else [[defaultDataDir]]. */
  def dataDir(configured: com.histmersion.config.RuntimeConfig.DataDir, env: com.histmersion.config.UserPaths.EnvVar => Option[com.histmersion.config.UserPaths.DataDirEnvResult], osName: com.histmersion.config.UserPaths.DataDirOsName): Option[Path] =
    Option(configured).map(_.trim).filter(_.nonEmpty).map(Path.of(_)).orElse(defaultDataDir(env, osName))

  /** The per-user CACHE directory: `$XDG_CACHE_HOME/histmersion`, default `~/.cache/histmersion` — on macOS too (the
    * XDG rule, as for the config file). For files that can be rebuilt or fetched again (the pinned gazetteer extracts,
    * #1403): never settings (those are [[configFile]]'s), never the corpus (that is the data directory's). `None` when
    * no location can be worked out (HOME unset).
    */
  def cacheDir(env: com.histmersion.config.UserPaths.EnvVar => Option[com.histmersion.config.UserPaths.CacheDirEnvResult]): Option[Path] =
    env("XDG_CACHE_HOME")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
      .orElse(home(env).map(Path.of(_, ".cache")))
      .map(_.resolve("histmersion").toAbsolutePath.normalize)

  /** Where the base map's extracts and their caches live: `configured` (`basemap.dir`; a leading `~` is the user's home
    * directory), else `<cache dir>/basemap` ([[cacheDir]]), else `basemap` under `initDataDir` (`init.data-dir`) —
    * absolute and normalised.
    */
  def basemapDir(configured: com.histmersion.config.UserPaths.BasemapDir, initDataDir: com.histmersion.config.RuntimeConfig.DataDir, env: com.histmersion.config.UserPaths.EnvVar => Option[com.histmersion.config.UserPaths.BasemapDirEnvResult]): Path =
    Option(configured)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(p.replaceFirst("^~", System.getProperty("user.home"))))
      .orElse(cacheDir(env).map(_.resolve("basemap")))
      .getOrElse(Path.of(initDataDir).resolve("basemap"))
      .toAbsolutePath
      .normalize

  /** Where the user config file is looked for: `$HISTMERSION_CONFIG` when set, else
    * `$XDG_CONFIG_HOME/histmersion/histmersion.conf` (default `~/.config`) — on macOS too. Config is something a person
    * edits, so it lives with their other dotfiles, never in the server-owned data directory, and nothing in the file
    * (`runtime.data-dir`) can move it. `None` when no location can be worked out (HOME unset).
    */
  def configFile(env: com.histmersion.config.UserPaths.EnvVar => Option[com.histmersion.config.UserPaths.ConfigFileEnvResult]): Option[Path] =
    val explicit = env(ConfigEnv)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(home(env).filter(_ => p == "~" || p.startsWith("~/")).fold(p)(h => h + p.drop(1))))
    explicit
      .orElse {
        env("XDG_CONFIG_HOME")
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(Path.of(_))
          .orElse(home(env).map(Path.of(_, ".config")))
          .map(_.resolve("histmersion").resolve(ConfigFileName))
      }
      .map(_.toAbsolutePath.normalize)
