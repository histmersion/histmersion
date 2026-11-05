package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion plugin …` (docs/research-plugins.md §7.2): the plugins of the Histmersion installed on THIS machine.
  * `installDir` empty = the install this CLI binary belongs to (the directory above its `bin/`), else the platform
  * default; `mavenRepos` is comma-separated (an env override is one string), tried in order for
  * `plugin add group:artifact:version`; `curl` downloads (`plugin add <url>`).
  */
final case class CliPluginsConfig(installDir: CliPluginsConfig.InstallDir, mavenRepos: CliPluginsConfig.MavenRepos, curl: CliPluginsConfig.Curl) derives ConfigReader:
  def repos: List[com.histmersion.config.CliPluginsConfig.ReposResult] = mavenRepos.split(",").toList.map(_.trim).filter(_.nonEmpty)

object CliPluginsConfig:
  type ReposResult = ReposResult.T
  object ReposResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type InstallDir = InstallDir.T
  object InstallDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MavenRepos = MavenRepos.T
  object MavenRepos extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Curl = Curl.T
  object Curl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
