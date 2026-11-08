package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion plugin …` (docs/research-plugins.md §7.2): the plugins of the Histmersion installed on THIS machine.
  * `installDir` empty = the install this CLI binary belongs to (the directory above its `bin/`), else the platform
  * default; `mavenRepos` is comma-separated (an env override is one string), tried in order for
  * `plugin add group:artifact:version`; `curl` downloads (`plugin add <url>`).
  */
final case class CliPluginsConfig(
    installDir: CliPluginsConfig.InstallDir,
    mavenRepos: CliPluginsConfig.MavenRepos,
    curl: CliPluginsConfig.Curl
) derives ConfigReader:
  def repos: List[CliPluginsConfig.MavenRepo] =
    mavenRepos.value.split(",").toList.map(_.trim).filter(_.nonEmpty).map(CliPluginsConfig.MavenRepo(_))

object CliPluginsConfig:
  type MavenRepo = MavenRepo.T
  object MavenRepo extends Newtype[String, Pure]

  type InstallDir = InstallDir.T
  object InstallDir extends Newtype[String, Pure]
  type MavenRepos = MavenRepos.T
  object MavenRepos extends Newtype[String, Pure]
  type Curl = Curl.T
  object Curl extends Newtype[String, Pure]
