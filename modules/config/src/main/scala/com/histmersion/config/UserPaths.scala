package com.histmersion
package config

import scala.annotation.tailrec
import org.ekrich.config.Config

import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** Where Histmersion keeps its per-user files: the data directory (the bundle's cluster, logs and backups; the agent
  * trees) and the user config file. ONE resolver, so the server, the bundle supervisor, the agent trees and the CLI
  * cannot disagree about a path. Built once by the entry point from the environment and the `os.name` property.
  */
final case class UserPaths(environment: Environment, osName: UserPaths.OsName):
  def isMac: Boolean = UserPaths.isMac(osName)
  def defaultDataDir: Option[Path] = UserPaths.defaultDataDir(environment.lookup, osName)
  def dataDir(configured: RuntimeConfig.DataDir): Option[Path] = UserPaths.dataDir(configured, environment.lookup, osName)
  def cacheDir: Option[Path] = UserPaths.cacheDir(environment.lookup)
  def configFile: Option[Path] = UserPaths.configFile(environment.lookup)

object UserPaths:
  type BasemapDir = BasemapDir.T
  object BasemapDir extends Newtype[String, Pure]
  type Lookup = Environment.VariablesKey => Option[Environment.VariablesValue]
  type HomeDir = HomeDir.T
  object HomeDir extends Newtype[String, Pure]

  type OsName = OsName.T
  object OsName extends Newtype[String, Pure]

  val ConfigEnv: Environment.VariablesKey = Environment.VariablesKey("HISTMERSION_CONFIG")

  val ConfigFileName = "histmersion.conf"

  def isMac(osName: OsName): Boolean = osName.value.toLowerCase.contains("mac")

  private def home(env: Lookup): Option[HomeDir] =
    env(Environment.VariablesKey("HOME")).map(_.value.trim).filter(_.nonEmpty).map(HomeDir(_))

  /** The platform's per-user data directory: `~/Library/Application Support/Histmersion` on macOS,
    * `$XDG_DATA_HOME/histmersion` (default `~/.local/share/histmersion`) elsewhere.
    */
  def defaultDataDir(env: Lookup, osName: OsName): Option[Path] =
    if isMac(osName) then home(env).map(h => Path.of(h.value, "Library", "Application Support", "Histmersion"))
    else
      env(Environment.VariablesKey("XDG_DATA_HOME"))
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .map(Path.of(_, "histmersion"))
        .orElse(home(env).map(h => Path.of(h.value, ".local", "share", "histmersion")))

  /** `configured` (`runtime.data-dir`) when it is set, else [[defaultDataDir]]. */
  def dataDir(configured: RuntimeConfig.DataDir, env: Lookup, osName: OsName): Option[Path] =
    Option(configured).map(_.value.trim).filter(_.nonEmpty).map(Path.of(_)).orElse(defaultDataDir(env, osName))

  /** The per-user CACHE directory: `$XDG_CACHE_HOME/histmersion`, default `~/.cache/histmersion` — on macOS too (the
    * XDG rule, as for the config file). For files that can be rebuilt or fetched again (the pinned gazetteer extracts,
    * #1403): never settings (those are [[configFile]]'s), never the corpus (that is the data directory's). `None` when
    * no location can be worked out (HOME unset).
    */
  def cacheDir(env: Lookup): Option[Path] =
    env(Environment.VariablesKey("XDG_CACHE_HOME"))
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
      .orElse(home(env).map(h => Path.of(h.value, ".cache")))
      .map(_.resolve("histmersion").toAbsolutePath.normalize)

  /** Where the base map's extracts and their caches live: `configured` (`basemap.dir`; a leading `~` is the user's home
    * directory), else `<cache dir>/basemap` ([[cacheDir]]), else `basemap` under `initDataDir` (`init.data-dir`) —
    * absolute and normalised.
    */
  def basemapDir(configured: BasemapDir, initDataDir: RuntimeConfig.DataDir, env: Lookup): Path =
    Option(configured)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(p.replaceFirst("^~", System.getProperty("user.home"))))
      .orElse(cacheDir(env).map(_.resolve("basemap")))
      .getOrElse(Path.of(initDataDir.value).resolve("basemap"))
      .toAbsolutePath
      .normalize

  /** Where the user config file is looked for: `$HISTMERSION_CONFIG` when set, else
    * `$XDG_CONFIG_HOME/histmersion/histmersion.conf` (default `~/.config`) — on macOS too. Config is something a person
    * edits, so it lives with their other dotfiles, never in the server-owned data directory, and nothing in the file
    * (`runtime.data-dir`) can move it. `None` when no location can be worked out (HOME unset).
    */
  def configFile(env: Lookup): Option[Path] =
    val explicit = env(ConfigEnv)
      .map(_.value.trim)
      .filter(_.nonEmpty)
      .map(p => Path.of(home(env).filter(_ => p == "~" || p.startsWith("~/")).fold(p)(h => h.value + p.drop(1))))
    explicit
      .orElse {
        env(Environment.VariablesKey("XDG_CONFIG_HOME"))
          .map(_.value.trim)
          .filter(_.nonEmpty)
          .map(Path.of(_))
          .orElse(home(env).map(h => Path.of(h.value, ".config")))
          .map(_.resolve("histmersion").resolve(ConfigFileName))
      }
      .map(_.toAbsolutePath.normalize)
