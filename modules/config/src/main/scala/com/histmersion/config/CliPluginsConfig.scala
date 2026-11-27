package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion plugin …` (docs/research-plugins.md §7.2): the plugins of the Histmersion installed on THIS machine.
  * `installDir` empty = the install this CLI binary belongs to (the directory above its `bin/`), else the platform
  * default; `mavenRepos` is comma-separated (an env override is one string), tried in order for
  * `plugin add group:artifact:version`; `curl` downloads (`plugin add <url>`).
  */
@movesTo(FeaturePlace.Config)
final case class CliPluginsConfig(
    installDir: CliPluginsConfig.InstallDir,
    mavenRepos: CliPluginsConfig.MavenRepos,
    curl: CliPluginsConfig.Curl
) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
