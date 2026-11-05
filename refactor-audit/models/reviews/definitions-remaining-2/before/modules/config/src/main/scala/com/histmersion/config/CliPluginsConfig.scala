package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion plugin …` (docs/research-plugins.md §7.2): the plugins of the Histmersion installed on THIS machine.
  * `installDir` empty = the install this CLI binary belongs to (the directory above its `bin/`), else the platform
  * default; `mavenRepos` is comma-separated (an env override is one string), tried in order for
  * `plugin add group:artifact:version`; `curl` downloads (`plugin add <url>`).
  */
final case class CliPluginsConfig(installDir: String, mavenRepos: String, curl: String) derives ConfigReader:
  def repos: List[String] = mavenRepos.split(",").toList.map(_.trim).filter(_.nonEmpty)
